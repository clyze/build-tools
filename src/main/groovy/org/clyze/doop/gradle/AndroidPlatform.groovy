package org.clyze.doop.gradle

import groovy.io.FileType
import groovy.transform.TypeChecked
import org.apache.commons.io.FileUtils
import org.clyze.utils.AARUtils
import org.clyze.utils.AndroidDepResolver
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

import static org.clyze.utils.JHelper.throwRuntimeException

@TypeChecked
class AndroidPlatform extends Platform {

    // The name of the Doop Gradle plugin task that will generate the
    // code input for Doop.
    static final String TASK_CODE_JAR = 'codeJar'
    // The name prefix of the Android Gradle plugin task that will
    // compile and package the program.
    static final String TASK_ASSEMBLE_PRE = 'assemble'

    // Configuration defaults.
    final String DEFAULT_SUBPROJECT_NAME = "."
    final String DEFAULT_BUILD_TYPE = "debug"

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

    AndroidPlatform(Project project) {
        super(project)
        isLibrary = project.plugins.hasPlugin('com.android.library')
        resolver = new AndroidDepResolver()
        resolver.setUseLatestVersion(true)
        resolver.setResolveLatestLast(true)
    }

    @Override
    void copyCompilationSettings(JavaCompile task) {
        task.classpath = project.files()
    }

    // This must happen in the "afterEvaluate" stage.
    void copySourceSettings(JavaCompile task) {
        AndroidAPI.forEachSourceFile(
            project,
            { FileTree srcFiles ->
                // Check if no Java sources were found. This may be a
                // user error (not specifying a 'subprojectName' in
                // build.gradle but it can also naturally occur during
                // the configuration of the top-level project that is
                // just a container of sub-projects.
                if ((srcFiles.size() == 0) && (isDefinedSubProject())) {
                    throwRuntimeException("No Java source files found for subproject " + doop.subprojectName)
                } else {
                    task.source = srcFiles
                }
            })
        if (task.source == null) {
            throwRuntimeException("Could not find sourceSet")
        }
    }

    // Checks if the current project is a Gradle sub-project (with a
    // non-"." value for doop.subprojectName in its build.gradle).
    private boolean isDefinedSubProject() {
        return ((doop.subprojectName != null) &&
                getSubprojectName() != ".")
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
    //
    // 7. Read all configuration files from transform tasks.
    @Override
    void markMetadataToFix() {
        project.afterEvaluate {
            // Read properties from build.gradle.
            if (!definesRequiredProperties()) {
                project.logger.warn "WARNING: Bad 'doop' section found in build.gradle, skipping configuration."
                return
            }

            def tasks = project.gradle.startParameter.taskNames
            // Skip configuration if no Doop tasks will run (this can only be
            // determined when no integration with existing tasks happens).
            if (explicitScavengeTask() && !tasks.any {
                    it.endsWith(TASK_CODE_JAR) ||
                    it.endsWith(DoopPlugin.TASK_SCAVENGE) ||
                    it.endsWith(DoopPlugin.TASK_JCPLUGIN_ZIP) ||
                    it.endsWith(DoopPlugin.TASK_ANALYZE) ||
                    it.endsWith(DoopPlugin.TASK_SOURCES_JAR)
                }) {
                project.logger.warn "WARNING: No ${DoopPlugin.DOOP_GROUP} task invoked, skipping configuration."
                return
            }

            String flavorDir = getFlavorDir()
            String appBuildHome = getAppBuildDir()

            // Update location of class files for JAR task.
            Jar jarTask = project.tasks.findByName(TASK_CODE_JAR) as Jar
            jarTask.from("${appBuildHome}/intermediates/classes/${flavorDir}")
            // Create dependency (needs doop section so it must happen late).
            configureCodeJarTaskDep(jarTask)

            Jar sourcesJarTask = project.tasks.findByName(DoopPlugin.TASK_SOURCES_JAR) as Jar
            gatherSourcesAfterEvaluate(sourcesJarTask)

            // For AAR libraries, attempt to read autogenerated sources.
            if (isLibrary) {
                def genSourceDirs = findGeneratedSourceDirs(appBuildHome, flavorDir)
                genSourceDirs.each { dir -> sourcesJarTask.from dir}
                if (explicitScavengeTask()) {
                    JavaCompile scavengeTask = project.tasks.findByName(DoopPlugin.TASK_SCAVENGE) as JavaCompile
                    if (scavengeTask) {
                        scavengeTask.source(genSourceDirs)
                    } else {
                        project.logger.error "Error: scavenge task is missing."
                    }
                }
                // Create dependency on source JAR task in order to create
                // the R.java files. This cannot happen at an earlier
                // stage because 'assemble' creates a circular dependency
                // and thus we use 'assemble{Debug,Release}' (or
                // equivalent flavor task, given via the 'flavor'
                // parameter).
                createSourcesJarDep(sourcesJarTask)
            }

            if (!explicitScavengeTask()) {
                configureCompileHook()
            }

            readConfigurationFiles()
        }
    }

    // Resolves the dependencies of the project.
    private Set<String> resolveDeps(String appBuildHome) {
        Set<String> deps = new HashSet<>()

        // Find the location of the Android SDK.
        resolver.findSDK(project.rootDir.canonicalPath)
        // Don't resolve dependencies the user overrides.
        resolver.ignoredArtifacts.addAll(doop.replacedByExtraInputs ?: [])

        project.configurations.each { conf ->
            // println "Configuration: ${conf.name}"
            conf.allDependencies.each { dep ->
                String group = dep.group
                if (group == null) {
                    return
                } else if (group == project.group.toString()) {
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
    private void calcScavengeDeps(Set<String> deps) {
        Set<String> deferredDeps = resolver.getLatestDelayedArtifacts()
        List<String> extraInputs = doop.getExtraInputFiles(project.rootDir)
        scavengeDeps.addAll(deferredDeps)
        scavengeDeps.addAll(deps)
        scavengeDeps.addAll(extraInputs)
    }

    // Returns the build type.
    String getBuildType() {
        if (doop.buildType == null) {
            throw new RuntimeException("Please set doop.buildType to the type of the existing build ('debug' or 'release').")
        } else if ((doop.buildType != 'debug') && (doop.buildType != 'release')) {
            project.logger.info "Property doop.buildType should probably be 'debug' or 'release' (current value: ${doop.buildType})."
        }

        Set<String> bTypes = AndroidAPI.getBuildTypes(project)
        if (!bTypes.contains(doop.buildType)) {
            project.logger.warn "WARNING: Build type not found in project: ${doop.buildType} (values: ${bTypes})"
        }

        return doop.buildType
    }

    private String getAssembleTaskName() {
        String flavor = doop.flavor
        String flavorPart = flavor == null ? "" : flavor.capitalize()
        String buildType = getBuildType()
        if (!buildType) {
            throw new RuntimeException("Error: could not determine build type")
        }
        String taskName = TASK_ASSEMBLE_PRE + flavorPart + buildType.capitalize()
        if (buildType != 'debug' && buildType != 'release') {
            project.logger.info "Unknown build type ${buildType}, assuming \"assemble\" task: ${taskName}"
        }
        return taskName
    }

    private void createSourcesJarDep(Jar sourcesJarTask) {
        String assembleTaskDep = getAssembleTaskName()
        project.logger.info "Using task '${assembleTaskDep}' to generate the sources JAR."
        sourcesJarTask.dependsOn project.tasks.findByName(assembleTaskDep)
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
            project.logger.warn "WARNING: Generated sources dir does not exist: ${generatedSources}"
            runAgain = true
            return []
        }
        genDir.eachFile (FileType.DIRECTORIES) { dir ->
            dir.eachFile (FileType.DIRECTORIES) { bPath ->
                if (bPath.canonicalPath.endsWith(flavorDir)) {
                    // Add subdirectories containing .java files.
                    project.logger.info "Adding sources in ${bPath}"
                    def containsJava = false
                    bPath.eachFileRecurse (FileType.FILES) { f ->
                        def fName = f.name
                        if ((!containsJava) && extension(fName) == "java")
                            containsJava = true
                    }
                    if (containsJava) {
                        project.logger.info "Found generated Java sources in ${bPath}"
                        genSourceDirs << bPath.getAbsolutePath()
                    }
                }
            }
        }
        return genSourceDirs
    }

    private String getAppBuildDir() {
        String subprojectName = getSubprojectName()
        return "${project.rootDir}/${subprojectName}/build"
    }

    /**
     * This happens in the "afterEvaluate" stage, since the "doop" section
     * must have already been read (to determine build-type-specific tasks).
     */
    @Override
    void createScavengeDependency(JavaCompile scavengeTask) {
        project.afterEvaluate {
            copySourceSettings(scavengeTask)
            scavengeTask.dependsOn project.tasks.findByName(assembleTaskName)

            // The scavenge classpath prefix contains Android core
            // libraries and the location of R*.class files.  Its
            // contents are not to be uploaded, so it is kept separate
            // from the rest of the classpath.
            Set<String> scavengeJarsPre = new HashSet<>()
            AndroidAPI.forEachClasspathEntry(project, { String s -> scavengeJarsPre << s })

            String appBuildHome = getAppBuildDir()
            String flavorDir = getFlavorDir()
            scavengeJarsPre << ("${appBuildHome}/intermediates/classes/${flavorDir}" as String)

            // Resolve dependencies to calculate the scavenge classpath.
            Set<String> deps = resolveDeps(appBuildHome)
            calcScavengeDeps(deps)

            // Construct scavenge classpath, checking if all parts exist.
            tmpDirs = new HashSet<>()
            Set<String> cp = new HashSet<>()
            cp.addAll(scavengeJarsPre)
            cp.addAll(AARUtils.toJars(scavengeDeps as List, true, tmpDirs))
            cp.each {
                if (!(new File(it)).exists())
                    project.logger.warn "WARNING: classpath entry to add does not exist: ${it}"
            }
            scavengeTask.options.compilerArgs << "-cp"
            scavengeTask.options.compilerArgs << cp.join(File.pathSeparator)
            cachedDeps.addAll(deps.collect { new File(it) })
        }
    }

    @Override
    void gatherSources(Jar sourcesJarTask) {}

    /**
     * This should run in the "afterEvaluate" stage, to find the subproject
     * name from the parsed "doop" section.
     */
    void gatherSourcesAfterEvaluate(Jar sourcesJarTask) {
        String subprojectName = getSubprojectName()
        String appPath = "${project.rootDir}/${subprojectName}"

        // Check if the Maven convention is followed for the sources.
        String srcMaven = "src/main/java"
        String srcSimple = "src/"
        if ((new File("${appPath}/${srcMaven}")).exists()) {
            project.logger.info "Using Maven-style source directories: ${srcMaven}"
            sourcesJarTask.from srcMaven
        } else if ((new File("${appPath}/${srcSimple}")).exists()) {
            project.logger.info "Using sources: ${srcSimple}"
            sourcesJarTask.from srcSimple
        } else if (isDefinedSubProject()) {
            throwRuntimeException("Could not find source directory")
        }
        String srcTestMaven = "src/test/java"
        if ((new File("${appPath}/${srcTestMaven}")).exists()) {
            project.logger.info "Using Maven-style test directories: ${srcTestMaven}"
            sourcesJarTask.from srcTestMaven
        }
        String srcAndroidTestMaven = "src/androidTest/java"
        if ((new File("${appPath}/${srcAndroidTestMaven}")).exists()) {
            project.logger.info "Using Maven-style Android test directories: ${srcAndroidTestMaven}"
            sourcesJarTask.from srcAndroidTestMaven
        }
    }

    /**
     * Analogous to configureSourceJarTask(), needed for Android,
     * where no JAR task exists in the Android Gradle plugin. The task
     * is not fully created here; its inputs and dependencies are set
     * in the "afterEvaluate" stage (see method markMetadataToFix()).
     */
    @Override
    void configureCodeJarTask() {
        Jar codeJarTask = project.tasks.create(TASK_CODE_JAR, Jar)
        codeJarTask.description = 'Generates the code jar'
        codeJarTask.group = DoopPlugin.DOOP_GROUP
    }

    private void configureCodeJarTaskDep(Jar codeJarTask) {
        codeJarTask.dependsOn project.tasks.findByName(assembleTaskName)
    }

    // Read the configuration files of all appropriate transform tasks
    // set up by the Android Gradle plugin. This uses the internal API
    // of the Android Gradle plugin.
    void readConfigurationFiles() {
        Set<File> allPros = new HashSet<>()
        AndroidAPI.forEachTransform(
            project,
            { FileCollection pros -> allPros.addAll(pros) })

        project.logger.info "Found ${allPros.size()} configuration files:"
        if (!doop.configurationFiles) {
            doop.configurationFiles = new ArrayList<>()
        }
        allPros.each {
            project.logger.info "Using rules from configuration file: ${it.canonicalPath}"
            doop.configurationFiles.add(it.canonicalPath)
        }
    }

    @Override
    String jarTaskName() { return TASK_CODE_JAR }

    // Returns the task that will package the compiled code as an .apk or .aar.
    String getPackageTaskName() {
        String buildType = getBuildType()
        String flavorPart = doop.flavor ? doop.flavor.capitalize() : ""
        String prefix = isLibrary? "bundle" : "package"
        // String sub = getSubprojectName()
        return "${prefix}${flavorPart}${buildType.capitalize()}"
    }

    @Override
    List<String> inputFiles() {
        String packageTask = getPackageTaskName()
        project.logger.info "Using non-library outputs from task ${packageTask}"
        List<String> ars = AndroidAPI.getOutputs(project, packageTask)
        project.logger.info "Calculated non-library outputs: ${ars}"
        return ars
    }

    @Override
    List<String> libraryFiles() {
        // Only upload dependencies when in AAR mode.
        if (!isLibrary) {
            return null
        }

        String packageTask = getPackageTaskName()
        project.logger.info "Using library outputs from task ${packageTask}, isLibrary = ${isLibrary}"
        List<String> extraInputFiles = doop.getExtraInputFiles(project.rootDir)
        return getDependencies().asList() + extraInputFiles
    }

    private Set<String> getDependencies() {
        Set<String> ret = cachedDeps.collect { File f ->
            if (!f.exists()) {
                throwRuntimeException("Dependency ${f} does not exist!")
            }
            f.canonicalPath
        } as Set
        if (scavengeDeps != null) {
            // Skip any directories used in the scavenge class path.
            ret.addAll(scavengeDeps.findAll { (new File(it)).isFile() })
        }
        return ret
    }

    @Override
    String getClasspath() {
        // Unfortunately, ScriptHandler.CLASSPATH_CONFIGURATION is not
        // a real task in the Android Gradle plugin and we have to use
        // a lower-level way to read the classpath.
        def cLoader = project.buildscript.getClassLoader()
        def cpList
        if (cLoader instanceof URLClassLoader) {
            URLClassLoader cl = (URLClassLoader)cLoader
            cpList = cl.getURLs()
        } else {
            cpList = AndroidAPI.getAsURIs(cLoader)
        }

        if (cpList == null) {
            throwRuntimeException('AndroidPlatform: cannot get classpath for jcplugin, cLoader is ' + cLoader)
        }
        return cpList.collect().join(File.pathSeparator).replaceAll('file://', '')
    }

    private String getFlavorDir() {
        String buildType = getBuildType()
        String flavor = doop.flavor
        return flavor == null? "${buildType}" : "${flavor}/${buildType}"
    }

    private String getSubprojectName(boolean crash = true) {
        if (doop.subprojectName == null) {
            if (crash) {
                throwRuntimeException("Please set doop.subprojectName to the name of the app subproject (e.g. 'Application').")
            }
            return ""
        } else
            return doop.subprojectName
    }

    // Android projects may have project.name be the default name of
    // the 'app' directory, so we use the group name too.
    @Override
    String getProjectName() {
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

    @Override
    boolean mustRunAgain() {
        return runAgain
    }

    @Override
    void cleanUp() {
        tmpDirs?.each { FileUtils.deleteQuietly(new File(it)) }
    }

    @Override
    boolean explicitScavengeTask() {
        // Current behavior: integrate with existing compile task.
        return false
    }

    /**
     * Configures the metadata processor (when integrated with a build task).
     *
     * @parameter project   the current project
     */
    private void configureCompileHook() {
        Set<JavaCompile> tasks = project.tasks.findAll { it instanceof JavaCompile } as Set<JavaCompile>
        if (tasks.size() == 0) {
            project.logger.error "Could not integrate metadata processor, no compile tasks found."
            return
        }

        tasks.each { task ->
            project.logger.info "Plugging metadata processor into task ${task.name}"
            DoopPlugin.addPluginCommandArgs(task, doop.scavengeOutputDir)
        }
    }

    // Check 'doop' sections in Android Gradle scripts.
    @Override
    boolean definesRequiredProperties() {
        if (doop.subprojectName == null) {
            project.logger.warn "WARNING: missing property 'subprojectName', using top-level directory"
	        doop.subprojectName = DEFAULT_SUBPROJECT_NAME
	    }
        if (doop.buildType == null) {
            project.logger.warn "WARNING: missing property 'buildType', assuming buildType=${DEFAULT_BUILD_TYPE}"
            doop.buildType = DEFAULT_BUILD_TYPE
	    }
        if (doop.flavor == null) {
            Set<String> pFlavors = AndroidAPI.getFlavors(project)
            if (pFlavors.size() > 0) {
                project.logger.warn "WARNING: property 'flavor' not set but these flavors were found: ${pFlavors}"
            }
        }
	    return super.definesRequiredProperties()
    }
}
