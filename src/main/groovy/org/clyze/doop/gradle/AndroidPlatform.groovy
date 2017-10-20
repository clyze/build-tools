package org.clyze.doop.gradle

import groovy.io.FileType
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classloader.ClasspathUtil

import static org.clyze.doop.gradle.DoopPlugin.*
import org.clyze.utils.AndroidDepResolver
import static org.clyze.utils.AndroidDepResolver.throwRuntimeException

class AndroidPlatform implements Platform {

    static final String TASK_CODE_JAR = 'codeJar'
    static final String TASK_ASSEMBLE = 'assemble'

    private AndroidDepResolver resolver
    private boolean runAgain
    private boolean isLibrary
    private Set<File> cachedDeps

    public AndroidPlatform(boolean lib) {
        cachedDeps = new HashSet<>()
        isLibrary = lib
        resolver = new AndroidDepResolver()
        resolver.setUseLatestVersion(true)
        resolver.setResolveLatestLast(true)
        runAgain = false
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
    void markMetadataToFix(Project project) {
        project.afterEvaluate {

            JavaCompile scavengeTask = project.tasks.findByName(TASK_SCAVENGE)
            copySourceSettings(project, scavengeTask)

            // Read properties from build.gradle.
            DoopExtension doop = project.extensions.doop
            if (!doop.definesAndroidProperties()) {
                println "No 'doop' section found in build.gradle, skipping configuration."
                return
            }

            def subprojectName = getSubprojectName(doop)
            def appBuildHome = "${project.rootDir}/${subprojectName}/build"

            String buildType = checkAndGetBuildType(doop)
            String flavor = doop.flavor
            String flavorDir = getFlavorDir(flavor, buildType)

            // Find locations of the Android SDK and the project build path.
            def androidSdkHome = resolver.findSDK(project.rootDir.canonicalPath)
            // Add to classpath: android.jar/layoutlib.jar (core OS
            // API) and the location of R*.class files.
            Set<String> scavengeJars = new HashSet<>()
            project.android.getBootClasspath().collect {
                scavengeJars << it.canonicalPath
            }
            scavengeJars << "${appBuildHome}/intermediates/classes/${flavorDir}"

            Set<String> deps = new HashSet<>()
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

                    String name = dep.name
                    String version = dep.version
                    Set<String> depsJ = resolver.resolveDependency(appBuildHome, group, name, version)
                    if (depsJ != null) {
                        deps.addAll(depsJ)
                    }
                }
            }
            Set<String> deferredDeps = resolver.getLatestDelayedArtifacts()
            scavengeJars.addAll(deferredDeps)
            scavengeJars.addAll(deps)
            scavengeJars.addAll(doop.getExtraInputFiles(project.rootDir))
            // Check if all parts of the new classpath exist.
            scavengeJars.each {
                if (!(new File(it)).exists())
                    println("AndroidPlatform warning: classpath entry to add does not exist: " + it)
            }
            scavengeTask.options.compilerArgs << "-cp"
            scavengeTask.options.compilerArgs << scavengeJars.join(File.pathSeparator)
            // println(scavengeTask.options.compilerArgs)

            cachedDeps.addAll(deps.collect { new File(it) })

            // Update location of class files for JAR task.
            Jar jarTask = project.tasks.findByName(TASK_CODE_JAR)
            jarTask.from("${appBuildHome}/intermediates/classes/${flavorDir}")

            def genSourceDirs = findGeneratedSourceDirs(appBuildHome, flavorDir)
            Jar sourcesJarTask = project.tasks.findByName(TASK_SOURCES_JAR)
            gatherSourcesAfterEvaluate(project, sourcesJarTask, flavorDir)
            genSourceDirs.each { dir -> sourcesJarTask.from dir}
            scavengeTask.source(genSourceDirs)

            // Create dependency on source JAR task in order to create
            // the R.java files. This cannot happen at an earlier
            // stage because 'assemble' creates a circular dependency
            // and thus we use 'assemble{Debug,Release}' (or
            // equivalent flavor task, given via the 'flavor'
            // parameter).
            createSourcesJarDep(project, sourcesJarTask, flavor, buildType)
        }
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
        String manifest = "${appPath}/build/intermediates/manifests/full/${flavorDir}/AndroidManifest.xml"
        if ((new File(manifest)).exists()) {
            println "Using manifest for sources JAR: ${manifest}"
            sourcesJarTask.from manifest
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

    List<String> inputFiles(Project project) {
        DoopExtension doop = project.extensions.doop
        String mode = checkAndGetBuildType(doop)
        println "Finding input files for mode = ${mode}, isLibrary = ${isLibrary}"
        def packageTask = null
        String flavorPart = doop.flavor ? doop.flavor.capitalize() : ""
        if (isLibrary) {
            if (mode.equals('debug')) {
                packageTask = "bundle${flavorPart}Debug"
            } else if (mode.equals('release')) {
                packageTask = "bundle${flavorPart}Release"
            }
        } else {
            if (mode.equals('debug')) {
                packageTask = "package${flavorPart}Debug"
            } else if (mode.equals('release')) {
                packageTask = "package${flavorPart}Release"
            }
        }
        println "Using outputs from task ${packageTask}"
        def ars = project.tasks.findByName(packageTask).outputs.files
                                 .findAll { extension(it.name) == 'apk' ||
                                            extension(it.name) == 'aar' }
                                 .collect { it.canonicalPath }
        List<String> extraInputFiles = doop.getExtraInputFiles(project.rootDir)
        return ars.toList() + getDependencies() + extraInputFiles
    }

    private Set<String> getDependencies() {
        return cachedDeps.collect { File f ->
            if (!f.exists()) {
                throwRuntimeException("Dependency ${f} does not exist!")
            }
            f.canonicalPath
        }
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

    public static String getSubprojectName(DoopExtension doop) {
	if (doop.subprojectName == null)
	    throwRuntimeException("Please set doop.subprojectName to the name of the app subproject (e.g. 'Application').")
	else
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
}
