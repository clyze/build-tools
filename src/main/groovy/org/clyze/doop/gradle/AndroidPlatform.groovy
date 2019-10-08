package org.clyze.doop.gradle

import groovy.io.FileType
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classloader.ClasspathUtil

import org.apache.commons.io.FileUtils

import static org.clyze.doop.gradle.DoopPlugin.*
import org.clyze.utils.AARUtils
import org.clyze.utils.AndroidDepResolver
import static org.clyze.utils.JHelper.throwRuntimeException

class AndroidPlatform implements Platform {

    // The name of the Doop Gradle plugin task that will generate the
    // code input for Doop.
    static final String TASK_CODE_JAR = 'codeJar'
    // The name of the Android Gradle plugin task that will compile
    // and package the program.
    static final String TASK_ASSEMBLE = 'assemble'

    private AndroidDepResolver resolver
    // Flag used to prompt the user to run again the Doop plugin when
    // needed files have not been generated yet.
    private boolean runAgain = false
    // Flag: true = AAR project, false = APK project.
    private boolean isLibrary
    // The resolved dependencies, cached to be shared between methods.
    private Set<File> cachedDeps = new HashSet<>()
    // The JARs needed to call the scavenge phase. They are posted to
    // the server when in AAR mode, but not when in APK mode.
    private Set<String> scavengeDeps = new HashSet<>()
    private Set<String> tmpDirs

    public AndroidPlatform(boolean lib) {
        isLibrary = lib
        resolver = new AndroidDepResolver()
        resolver.setUseLatestVersion(true)
        resolver.setResolveLatestLast(true)
    }

    void copyCompilationSettings(Project project, Task task) {
        task.classpath = project.files()
    }

    // This must happen afteEvaluate()
    void copySourceSettings(Project project, Task task) {
        for (def set1 : project.android.sourceSets) {
            if (set1.name == "main") {
                def srcFiles = set1.java.sourceFiles
                // Check if no Java sources were found. This may be a
                // user error (not specifying a 'subprojectName' in
                // build.gradle but it can also naturally occur during
                // the configuration of the top-level project that is
                // just a container of sub-projects.
                if ((srcFiles.size() == 0) && (isDefinedSubProject(project))) {
                    throwRuntimeException("No Java source files found for subproject " + subprojectName)
                } else {
                    task.source = srcFiles
                }
            }
        }
        if (task.source == null) {
            throwRuntimeException("Could not find sourceSet")
        }
    }

    // Checks if the current project is a Gradle sub-project (with a
    // non-"." value for doop.subprojectName in its build.gradle).
    private static boolean isDefinedSubProject(Project project) {
        DoopExtension doop = project.extensions.doop
        return ((doop.subprojectName != null) &&
                (!getSubprojectName(doop).equals(".")))
    }

    // Reads properties from local.properties and build.gradle and
    // fills in infromation needed when on Android. The code below is
    // scheduled to run after all tasks have been configured
    // ("afterEvaluate") and does the following:
    //
    // 1. It augments the classpath in order to find the Android API
    // and other needed code. To find what is needed, it scans
    // metadata such as the compile dependencies (e.g. to find uses of
    // the support libraries).
    //
    // 2. It sets the location of the class files needed by the code
    // JAR task.
    //
    // 3. It sets the location of the auto-generated Java sources in
    // the sources JAR task.
    //
    // 4. It creates dynamic dependencies of Doop tasks.
    //
    // 5. It copies the source file location from the configured
    // Android sourceSet.
    //
    // 6. If the metadata processor is integrated as an annotation
    // processor in an existing task, configure it.
    void markMetadataToFix(Project project) {
        project.afterEvaluate {

            // Read properties from build.gradle.
            DoopExtension doop = project.extensions.doop
            if (!doop.definesAndroidProperties()) {
                println "Bad 'doop' section found in build.gradle, skipping configuration."
                return
            }

            def tasks = project.gradle.startParameter.taskNames
            // Skip configuration if no Doop tasks will run (this can only be
            // determined when no integration with existing tasks happens).
            if (explicitScavengeTask() && !tasks.any {
                    it.endsWith(TASK_CODE_JAR) ||
                    it.endsWith(TASK_SCAVENGE) ||
                    it.endsWith(TASK_JCPLUGIN_ZIP) ||
                    it.endsWith(TASK_ANALYZE) ||
                    it.endsWith(TASK_SOURCES_JAR)
                }) {
                println "No ${DOOP_GROUP} task invoked, skipping configuration."
                return
            }

            def subprojectName = getSubprojectName(doop)
            def appBuildHome = "${project.rootDir}/${subprojectName}/build"

            String buildType = checkAndGetBuildType(doop)
            String flavor = doop.flavor
            String flavorDir = getFlavorDir(flavor, buildType)

            // Resolve dependencies if using an explicit scavenge task.
            if (explicitScavengeTask()) {
                Set<String> deps = resolveDeps(project, appBuildHome)
                JavaCompile scavengeTask = project.tasks.findByName(TASK_SCAVENGE)
                copySourceSettings(project, scavengeTask)

                // The scavenge classpath prefix contains Android core
                // libraries and the location of R*.class files.  Its
                // contents are not to be uploaded, so it is kept separate
                // from the rest of the classpath.
                Set<String> scavengeJarsPre = new HashSet<>()
                project.android.getBootClasspath().collect {
                    scavengeJarsPre << it.canonicalPath
                }
                scavengeJarsPre << "${appBuildHome}/intermediates/classes/${flavorDir}"
                // Calculate the scavenge classpath.
                calcScavengeDeps(project, deps)

                // Construct scavenge classpath, checking if all parts exist.
                tmpDirs = new HashSet<>()
                Set<String> cp = new HashSet<>()
                cp.addAll(scavengeJarsPre)
                cp.addAll(AARUtils.toJars(scavengeDeps as List, true, tmpDirs))
                cp.each {
                    if (!(new File(it)).exists())
                        println("AndroidPlatform warning: classpath entry to add does not exist: " + it)
                }
                scavengeTask.options.compilerArgs << "-cp"
                scavengeTask.options.compilerArgs << cp.join(File.pathSeparator)
                cachedDeps.addAll(deps.collect { new File(it) })
            }

            // Update location of class files for JAR task.
            Jar jarTask = project.tasks.findByName(TASK_CODE_JAR)
            jarTask.from("${appBuildHome}/intermediates/classes/${flavorDir}")

            def genSourceDirs = findGeneratedSourceDirs(appBuildHome, flavorDir)
            Jar sourcesJarTask = project.tasks.findByName(TASK_SOURCES_JAR)
            gatherSourcesAfterEvaluate(project, sourcesJarTask, flavorDir)
            genSourceDirs.each { dir -> sourcesJarTask.from dir}

            if (explicitScavengeTask()) {
                scavengeTask.source(genSourceDirs)
            }

            // Create dependency on source JAR task in order to create
            // the R.java files. This cannot happen at an earlier
            // stage because 'assemble' creates a circular dependency
            // and thus we use 'assemble{Debug,Release}' (or
            // equivalent flavor task, given via the 'flavor'
            // parameter).
            createSourcesJarDep(project, sourcesJarTask, flavor, buildType)

            if (!explicitScavengeTask()) {
                configureCompileHook(project)
            }
        }
    }

    // Resolves the dependencies of the project.
    private Set<String> resolveDeps(Project project, String appBuildHome) {
        Set<String> deps = new HashSet<>()

        // Find the location of the Android SDK.
        resolver.findSDK(project.rootDir.canonicalPath)
        // Don't resolve dependencies the user overrides.
        resolver.ignoredArtifacts.addAll(project.extensions.doop.replacedByExtraInputs ?: [])

        project.configurations.each { conf ->
            // println "Configuration: ${conf.name}"
            conf.allDependencies.each { dep ->
                String group = dep.group
                if (group == null) {
                    return
                } else if (group.equals(project.group.toString())) {
                    // We do not resolve dependencies whose group is
                    // that of the current build. This means that
                    // other subprojects in the same tree must be
                    // separately built and their code provided using
                    // 'extraInputs' in build.gradle's 'doop' section.
                    println "Ignoring own dependency ${group}:${dep.name}"
                    return
                }

                Set<String> depsJ = resolver.resolveDependency(appBuildHome, group, dep.name, dep.version)
                if (depsJ != null) {
                    deps.addAll(depsJ)
                }
            }
        }
        return deps
    }

    // Calculates the scavenge dependencies of the project.
    private void calcScavengeDeps(Project project, Set<String> deps) {
        Set<String> deferredDeps = resolver.getLatestDelayedArtifacts()
        List<String> extraInputs = project.extensions.doop.getExtraInputFiles(project.rootDir)
        scavengeDeps.addAll(deferredDeps)
        scavengeDeps.addAll(deps)
        scavengeDeps.addAll(extraInputs)
    }

    private static void createSourcesJarDep(Project project, Jar sourcesJarTask,
                                            String flavor, String buildType) {
        DoopExtension doop = project.extensions.doop
        String assembleTaskDep
        String flavorPart = flavor == null ? "" : flavor.capitalize()
        switch (buildType) {
            case 'debug':
                assembleTaskDep = "assemble${flavorPart}Debug"
                break
            case 'release':
                assembleTaskDep = "assemble${flavorPart}Release"
                break
        }
        println "Using task '${assembleTaskDep}' to generate the sources JAR."
        sourcesJarTask.dependsOn project.tasks.findByName(assembleTaskDep)
    }

    static String baseName(File file) {
        return file.name.replaceFirst(~/\.[^\.]+$/, '')
    }

    static String extension(String name) {
        return name.substring(name.lastIndexOf('.') + 1, name.size())
    }

    // Add auto-generated Java files (examples are the app's R.java,
    // other R.java files, and classes in android.support packages).
    private List findGeneratedSourceDirs(String appBuildHome, String flavorDir) {
        def genSourceDirs = []
        def generatedSources = "${appBuildHome}/generated/source"
        File genDir = new File(generatedSources)
        if (!genDir.exists()) {
            println "Generated sources dir does not exist: ${generatedSources}"
            runAgain = true
            return []
        }
        genDir.eachFile (FileType.DIRECTORIES) { dir ->
            dir.eachFile (FileType.DIRECTORIES) { bPath ->
                if (bPath.canonicalPath.endsWith(flavorDir)) {
                    // Add subdirectories containing .java files.
                    println "Adding sources in ${bPath}"
                    def containsJava = false
                    bPath.eachFileRecurse (FileType.FILES) { f ->
                        def fName = f.name
                        if ((!containsJava) && extension(fName) == "java")
                            containsJava = true
                    }
                    if (containsJava) {
                        println "Found generated Java sources in ${bPath}"
                        genSourceDirs << bPath.getAbsolutePath()
                    }
                }
            }
        }
        return genSourceDirs
    }

    void createScavengeDependency(Project project, JavaCompile scavengeTask) {
        scavengeTask.dependsOn project.tasks.findByName(TASK_ASSEMBLE)
    }

    void gatherSources(Project project, Jar sourcesJarTask) {}

    void gatherSourcesAfterEvaluate(Project project, Jar sourcesJarTask, String flavorDir) {
        String subprojectName = getSubprojectName(project.extensions.doop)
        String appPath = "${project.rootDir}/${subprojectName}"

        // Check if the Maven convention is followed for the sources.
        String srcMaven = "src/main/java"
        String srcSimple = "src/"
        if ((new File("${appPath}/${srcMaven}")).exists()) {
            println "Using Maven-style source directories: ${srcMaven}"
            sourcesJarTask.from srcMaven
        } else if ((new File("${appPath}/${srcSimple}")).exists()) {
            println "Using sources: ${srcSimple}"
            sourcesJarTask.from srcSimple
        } else if (isDefinedSubProject(project)) {
            throwRuntimeException("Could not find source directory")
        }
        String srcTestMaven = "src/test/java"
        if ((new File("${appPath}/${srcTestMaven}")).exists()) {
            println "Using Maven-style test directories: ${srcTestMaven}"
            sourcesJarTask.from srcTestMaven
        }
        String srcAndroidTestMaven = "src/androidTest/java"
        if ((new File("${appPath}/${srcAndroidTestMaven}")).exists()) {
            println "Using Maven-style Android test directories: ${srcAndroidTestMaven}"
            sourcesJarTask.from srcAndroidTestMaven
        }

        String mDir = "${appPath}/build/intermediates/manifests"
        if (isLibrary) {
            addManifest("${mDir}/aapt/${flavorDir}/AndroidManifest.xml", sourcesJarTask)
        } else {
            addManifest("${mDir}/full/${flavorDir}/AndroidManifest.xml", sourcesJarTask)
        }
    }

    private static void addManifest(String manifest, Jar sourcesJarTask) {
        if ((new File(manifest)).exists()) {
            println "Using manifest for sources JAR: ${manifest}"
            sourcesJarTask.from manifest
        } else {
            println "Error: manifest not found: ${manifest}. This could be due to a missing product flavor (set doop.flavor=\"flavor\" in build.gradle)."
        }
    }

    // Analogous to configureSourceJarTask(), needed for Android,
    // where no JAR task exists in the Android gradle plugin. The task
    // is not fully created here; its inputs are set "afterEvaluate"
    // (see method markMetadataToFix() above).
    void configureCodeJarTask(Project project) {
        Jar codeJarTask = project.tasks.create(TASK_CODE_JAR, Jar)
        codeJarTask.description = 'Generates the code jar'
        codeJarTask.group = DOOP_GROUP
        codeJarTask.dependsOn project.getTasks().findByName(TASK_ASSEMBLE)
    }

    String jarTaskName() { return TASK_CODE_JAR }

    // Returns the task that will package the compiled code as an .apk or .aar.
    String findPackageTask(Project project) {
        DoopExtension doop = project.extensions.doop
        String buildType = checkAndGetBuildType(doop)
        String flavorPart = doop.flavor ? doop.flavor.capitalize() : ""
        String prefix = isLibrary? "bundle" : "package"
        // String sub = getSubprojectName(project.extensions.doop)
        return "${prefix}${flavorPart}${buildType.capitalize()}"
    }

    List<String> inputFiles(Project project) {
        String packageTask = findPackageTask(project)
        println "Using non-library outputs from task ${packageTask}"
        def ars = project.tasks.findByName(packageTask).outputs.files
                               .findAll { extension(it.name) == 'apk' ||
                                          extension(it.name) == 'aar' }
                               .collect { it.canonicalPath }
                               .toList()
        println "Calculated non-library outputs: ${ars}"
        return ars
    }

    List<String> libraryFiles(Project project) {
        // Only upload dependencies when in AAR mode.
        if (!isLibrary) {
            return null
        }

        String packageTask = findPackageTask(project.extensions.doop)
        println "Using library outputs from task ${packageTask}, isLibrary = ${isLibrary}"
        List<String> extraInputFiles = doop.getExtraInputFiles(project.rootDir)
        return getDependencies().asList() + extraInputFiles
    }

    private Set<String> getDependencies() {
        Set<String> ret = cachedDeps.collect { File f ->
            if (!f.exists()) {
                throwRuntimeException("Dependency ${f} does not exist!")
            }
            f.canonicalPath
        }
        if (scavengeDeps != null) {
            // Skip any directories used in the scavenge class path.
            ret.addAll(scavengeDeps.findAll { (new File(it)).isFile() })
        }
        return ret
    }

    String getClasspath(Project project) {
        // Unfortunately, ScriptHandler.CLASSPATH_CONFIGURATION is not
        // a real task in the Android Gradle plugin and we have to use
        // a lower-level way to read the classpath.
        def cLoader = project.buildscript.getClassLoader()
        def cpList = null
        if (cLoader instanceof URLClassLoader) {
            URLClassLoader cl = (URLClassLoader)cLoader
            cpList = cl.getURLs()
        } else {
            ClassPath cp = ClasspathUtil.getClasspath(cLoader);
            cpList = cp.getAsURIs()
        }

        if (cpList != null) {
            return cpList.collect().join(File.pathSeparator).replaceAll('file://', '')
        } else {
            throwRuntimeException('AndroidPlatform: cannot get classpath for jcplugin, cLoader is ' + cLoader)
        }
    }

    private static String checkAndGetBuildType(DoopExtension doop) {
        def buildType = doop.buildType
        if (buildType == null) {
            throwRuntimeException("Please set doop.buildType to the type of the existing build ('debug' or 'release').")
        } else if ((buildType != 'debug') && (buildType != 'release')) {
            throwRuntimeException("Property doop.buildType must be 'debug' or 'release'.")
        }
        return buildType
    }

    private static String getFlavorDir(String flavor, String buildType) {
        return flavor == null? "${buildType}" : "${flavor}/${buildType}"
    }

    public static String getSubprojectName(DoopExtension doop, boolean crash = true) {
	if (doop.subprojectName == null) {
	    if (crash) {
		throwRuntimeException("Please set doop.subprojectName to the name of the app subproject (e.g. 'Application').")
	    } else {
		return ""
	    }
	} else
	    return doop.subprojectName
    }

    // Android projects may have project.name be the default name of
    // the 'app' directory, so we use the group name too.
    String getProjectName(Project project) {
        String group = project.group
        String name = project.name
        boolean noGroup = (group == null) || (group.length() == 0)
        boolean noName = (name == null) || (name.length() == 0)
        if (noGroup) {
            return noName? "unnamed_Android_app" : name
        } else {
            return noName? group : "${group}_${name}"
        }
    }

    public boolean mustRunAgain() {
        return runAgain
    }

    public void cleanUp() {
        tmpDirs?.each { FileUtils.deleteQuietly(new File(it)) }
    }

    public boolean explicitScavengeTask() {
        // Current behavior: integrate with existing compile task.
        return false
    }

    /**
     * Configures the metadata processor (when integrated with a build task).
     *
     * @parameter project   the current project
     */
    private static void configureCompileHook(Project project) {
        String taskName = 'compileDebugJavaWithJavac'
        def task = project.tasks.findByName(taskName)
        if (!task) {
            println "Cannot integrate with build process, no task: ${taskName}"
            return
        }
        println "Integrating metadata processor with task '${taskName}': ${task}"
        DoopPlugin.addPluginCommandArgs(task, project.extensions.doop.scavengeOutputDir)
    }
}
