package org.clyze.build.tools.gradle

import groovy.io.FileType
import groovy.transform.TypeChecked
import org.clyze.build.tools.Conventions

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.apache.commons.io.FileUtils
import org.clyze.build.tools.Archiver
import org.clyze.build.tools.JcPlugin
import org.clyze.utils.AARUtils
import org.clyze.utils.AndroidDepResolver
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

import static org.clyze.build.tools.Conventions.msg
import static org.clyze.utils.JHelper.throwRuntimeException

/**
 * This class controls how the plugin adapts to Android build projects.
 */
@TypeChecked
class AndroidPlatform extends Platform {

    /** The name of the Gradle plugin task that will generate the
     *  code input for the server. */
    static final String CODE_ARCHIVE = 'codeApk'
    /** The name prefix of the Android Gradle plugin task that will
      * compile and package the program. */
    static final String ASSEMBLE_PRE = 'assemble'

    /** Default subproject is the current directory. */
    final String DEFAULT_SUBPROJECT_NAME = "."
    /** Default build type. */
    final String DEFAULT_BUILD_TYPE = "release"

    private AndroidDepResolver resolver
    // Flag: true = AAR project, false = APK project.
    private boolean isLibrary
    // The resolved dependencies, cached to be shared between methods.
    private Set<File> cachedDeps = new HashSet<>()
    // The JARs needed to call the scavenge phase. They are posted to
    // the server when in AAR mode, but not when in APK mode.
    private Set<String> scavengeDeps = new HashSet<>()
    private Set<String> tmpDirs

    /**
     * Initializes an Android platform handler for a project.
     *
     * @param project    an Android app/library project (library support is experimental)
     */
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

    /**
     * Copies the source settings of the Android build process to a custom
     * build task. This must happen in the "afterEvaluate" stage.
     *
     * @param task   the compile task to accept source file configuration
     */
    private void copySourceSettings(JavaCompile task) {
        AndroidAPI.forEachSourceFile(
            project,
            { FileTree srcFiles ->
                // Check if no Java sources were found. This may be a
                // user error (not specifying a 'subprojectName' in
                // build.gradle but it can also naturally occur during
                // the configuration of the top-level project that is
                // just a container of sub-projects.
                if ((srcFiles.size() == 0) && (isDefinedSubProject())) {
                    throwRuntimeException(msg("No Java source files found for subproject " + repackageExt.subprojectName))
                } else {
                    task.source = srcFiles
                }
            })
        if (task.source == null) {
            throwRuntimeException(msg("Could not find sourceSet"))
        }
    }

    /**
     * Checks if the current project is a Gradle sub-project (with a
     * non-"." value for option 'subprojectName' in its build.gradle).
     */
    private boolean isDefinedSubProject() {
        return ((repackageExt.subprojectName != null) &&
                getSubprojectName() != ".")
    }

    /**
     * Reads properties from local.properties and build.gradle and
     * fills in infromation needed when on Android. The code below is
     * scheduled to run after all tasks have been configured
     * ("afterEvaluate") and does the following:
     *
     * <ol type="1">
     * <li>It augments the classpath in order to find the Android API
     * and other needed code. To find what is needed, it scans
     * metadata such as the compile dependencies (e.g. to find uses of
     * the support libraries).</li>
     *
     * <li>It finishes the configuration of the code archive task.</li>
     *
     * <li>It sets the location of the auto-generated Java sources in
     * the sources JAR task.</li>
     *
     * <li>It creates dynamic dependencies of plugin tasks.</li>
     *
     * <li>It copies the source file location from the configured
     * Android sourceSet.</li>
     *
     * <li>If the metadata processor is integrated as an annotation
     * processor in an existing task, configure it.</li>
     *
     * <li>Read all configuration files from transform tasks.</li>
     *
     * <li>Configure the test-repackaged-code task.</li>
     *
     * <li>Configure source tasks (if enabled).</li>
     *
     * </ol>
     */
    @Override
    void markMetadataToFix() {
        project.afterEvaluate {
            def tasks = project.gradle.startParameter.taskNames
            // Skip configuration if no plugin tasks will run.
            if (!tasks.any {
                    it.endsWith(CODE_ARCHIVE) ||
                    it.endsWith(Tasks.CONFIGURATIONS) ||
                    it.endsWith(Tasks.SCAVENGE) ||
                    it.endsWith(Tasks.JCPLUGIN_ZIP) ||
                    it.endsWith(Tasks.POST_BUNDLE) ||
                    it.endsWith(Tasks.SOURCES_JAR) ||
                    it.endsWith(Tasks.CREATE_BUNDLE)
                }) {
                project.logger.info msg("No ${Conventions.TOOL_NAME} task invoked, skipping configuration.")
                return
            }

            // Read properties from build.gradle.
            if (!definesRequiredProperties()) {
                project.logger.warn MISSING_PROPERTIES
                return
            }

            configureCodeTaskAfterEvaluate()

            if (repackageExt.sources) {
                configureSourceTasks()

                Jar sourcesTask = project.tasks.findByName(Tasks.SOURCES_JAR) as Jar
                gatherSourcesAfterEvaluate(sourcesTask)

                // If not using an explicit metadata scavenge task, hook into the
                // compiler instead. If this is a run that throws away code (because
                // no archive task is called), skip this integration.
                def taskArch = tasks.find { it.endsWith(CODE_ARCHIVE) || it.endsWith(Tasks.CREATE_BUNDLE) }
                if (!explicitScavengeTask() && taskArch)
                    configureCompileHook()
            }


            Task confTask = project.tasks.findByName(Tasks.CONFIGURATIONS) as Task
            confTask.dependsOn getAssembleTaskName()
            activateSpecialConfiguration()

            // If "create bundle" and "post bundle" are called
            // together, make the second depend on the first, so they
            // are executed in the correct order.
            if (tasks.find { it.endsWith(Tasks.CREATE_BUNDLE) } &&
                tasks.find { it.endsWith(Tasks.POST_BUNDLE) }) {
                project.tasks.findByName(Tasks.POST_BUNDLE)
                    .dependsOn(project.tasks.findByName(Tasks.CREATE_BUNDLE))
            }

            configureTestRepackaging()
        }
    }

    private void activateSpecialConfiguration() {
        Conventions.SpecialConfiguration sc = Conventions.getSpecialConfiguration(true, repackageExt.printConfig)
        if (!sc)
            project.logger.warn(Conventions.COULD_NOT_DISABLE_RULES + ' No disabling configuration.')
        else
            injectConfiguration(sc.file, Conventions.COULD_NOT_DISABLE_RULES)
        if (repackageExt.printConfig) {
            repackageExt.configurationFiles = [ sc.outputConfigurationPath ] as List<String>
        }
    }

    /**
     * Configure the tasks that invoke tests to check if repackaging is correct.
     */
    private void configureTestRepackaging() {
        // Insert test-code repackager between the javac
        // invocation and the test runner.
        Task testRepackageTask = project.tasks.findByName(Tasks.REPACKAGE_TEST) as Task
        String utciTaskName = getUnitTestCompileInnerTask()
        Task utciTask = project.tasks.findByName(utciTaskName)
        String utcTaskName = getUnitTestCompileTask()
        Task utcTask = project.tasks.findByName(utcTaskName) as Task
        if (utciTask && utcTask) {
            testRepackageTask.dependsOn getUnitTestCompileInnerTask()
            utcTask.dependsOn testRepackageTask
        } else if (!utciTask) {
            project.logger.warn msg("WARNING: no '${utciTaskName}' task, skipping configuration of task '${Tasks.REPACKAGE_TEST}'.")
        } else if (!utcTask) {
            project.logger.warn msg("WARNING: no '${utcTaskName}' task, skipping configuration of task '${Tasks.REPACKAGE_TEST}'.")
        }
    }

    // Resolves the dependencies of the project.
    private Set<String> resolveDeps(String appBuildHome) {
        Set<String> deps = new HashSet<>()

        // Find the location of the Android SDK.
        resolver.findSDK(project.rootDir.canonicalPath)
        // Don't resolve dependencies the user overrides.
        resolver.ignoredArtifacts.addAll(repackageExt.replacedByExtraInputs ?: [])

        project.configurations.each { conf ->
            // println msg("Configuration: ${conf.name}")
            conf.allDependencies.each { dep ->
                String group = dep.group
                if (group == null) {
                    return
                } else if (group == project.group.toString()) {
                    // We do not resolve dependencies whose group is that of
                    // the current build. This means that other subprojects
                    // in the same tree must be separately built and their
                    // code provided using 'extraInputs' in build.gradle's
                    // plugin configuration section.
                    project.logger.warn msg("WARNING: ignoring own dependency ${group}:${dep.name}")
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
        List<String> extraInputs = repackageExt.getExtraInputFiles(project.rootDir)
        scavengeDeps.addAll(deferredDeps)
        scavengeDeps.addAll(deps)
        scavengeDeps.addAll(extraInputs)
    }

    /**
     * Returns the configured build type.
     *
     * @return a build type identifier
     */
    private String getBuildType() {
        if (repackageExt.buildType == null) {
            throwRuntimeException(msg("Please set option 'buildType' to the type of the existing build (default: '${DEFAULT_BUILD_TYPE}')."))
        } else if ((repackageExt.buildType != 'debug') && (repackageExt.buildType != 'release')) {
            project.logger.info msg("Property 'buildType' should probably be 'debug' or 'release' (current value: ${repackageExt.buildType}).")
        }

        Set<String> bTypes = AndroidAPI.getBuildTypes(project)
        if (!bTypes.contains(repackageExt.buildType)) {
            project.logger.warn msg("WARNING: Build type not found in project ${project.name}: ${repackageExt.buildType} (values: ${bTypes})")
        }

        return repackageExt.buildType
    }

    /**
     * Return the configured flavor and build type.
     *
     * @return a single string (for example, "prod" + "release" = "prodRelease")
     */
    String getFlavorAndBuildType() {
        String flavor = repackageExt.flavor
        String flavorPart = flavor == null ? "" : flavor
        String buildType = getBuildType()
        if (!buildType) {
            throwRuntimeException(msg("ERROR: could not determine build type"))
        }
        return flavorPart + buildType.capitalize()
    }

    private String getAssembleTaskName() {
        String taskName = ASSEMBLE_PRE + flavorAndBuildType.capitalize()
        if (buildType != 'debug' && buildType != 'release') {
            project.logger.info msg("Unknown build type ${buildType}, assuming \"assemble\" task: ${taskName}")
        }
        return taskName
    }

    private String getUnitTestCompileTask() {
        return "compile" + flavorAndBuildType.capitalize() + "UnitTestSources"
    }

    /**
     * This is the name of the inner task that compiles test sources via javac,
     * putting them in a directory with the same task name.
     *
     * @return the inner task name
     */
    String getUnitTestCompileInnerTask() {
        return "compile" + flavorAndBuildType.capitalize() + "JavaWithJavac"
    }

    private void createSourcesJarDep(Jar sourcesJarTask) {
        String assembleTaskDep = getAssembleTaskName()
        project.logger.info msg("Using task '${assembleTaskDep}' to generate the sources JAR.")
        sourcesJarTask.dependsOn project.tasks.findByName(assembleTaskDep)
    }

    // Add auto-generated Java files (examples are the app's R.java,
    // other R.java files, and classes in android.support packages).
    private List findGeneratedSourceDirs(String appBuildHome, String flavorDir) {
        def genSourceDirs = []
        def generatedSources = "${appBuildHome}/generated/source"
        File genDir = new File(generatedSources)
        if (!genDir.exists()) {
            project.logger.warn msg("WARNING: Generated sources dir does not exist: ${generatedSources}")
            return []
        }
        genDir.eachFile (FileType.DIRECTORIES) { dir ->
            dir.eachFile (FileType.DIRECTORIES) { bPath ->
                if (bPath.canonicalPath.endsWith(flavorDir)) {
                    // Add subdirectories containing .java files.
                    project.logger.info msg("Adding sources in ${bPath}")
                    def containsJava = false
                    bPath.eachFileRecurse (FileType.FILES) { f ->
                        def fName = f.name
                        if ((!containsJava) && fName.endsWith('.java'))
                            containsJava = true
                    }
                    if (containsJava) {
                        project.logger.info msg("Found generated Java sources in ${bPath}")
                        genSourceDirs << bPath.getAbsolutePath()
                    }
                }
            }
        }
        return genSourceDirs
    }

    /**
     * Returns the build directory of the current subproject.
     *
     * @return the build directory path
     */
    String getAppBuildDir() {
        String subprojectName = getSubprojectName()
        return "${project.rootDir}/${subprojectName}/build"
    }

    /**
     * This happens in the "afterEvaluate" stage, since the configuration section
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
                    project.logger.warn msg("WARNING: classpath entry to add does not exist: ${it}")
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
     * name from the parsed configuration section.
     *
     * @param sourcesJarTask   the name of the task gathering source (text) files
     */
    void gatherSourcesAfterEvaluate(Jar sourcesJarTask) {
        String subprojectName = getSubprojectName()
        String appPath = "${project.rootDir}/${subprojectName}"

        // Check if the Maven convention is followed for the sources.
        String srcMaven = "src/main/java"
        String srcSimple = "src/"
        if ((new File("${appPath}/${srcMaven}")).exists()) {
            project.logger.info msg("Using Maven-style source directories: ${srcMaven}")
            sourcesJarTask.from srcMaven
        } else if ((new File("${appPath}/${srcSimple}")).exists()) {
            project.logger.info msg("Using sources: ${srcSimple}")
            sourcesJarTask.from srcSimple
        } else if (isDefinedSubProject()) {
            throwRuntimeException(msg("Could not find source directory"))
        }
        String srcTestMaven = "src/test/java"
        if ((new File("${appPath}/${srcTestMaven}")).exists()) {
            project.logger.info msg("Using Maven-style test directories: ${srcTestMaven}")
            sourcesJarTask.from srcTestMaven
        }
        String srcAndroidTestMaven = "src/androidTest/java"
        if ((new File("${appPath}/${srcAndroidTestMaven}")).exists()) {
            project.logger.info msg("Using Maven-style Android test directories: ${srcAndroidTestMaven}")
            sourcesJarTask.from srcAndroidTestMaven
        }

        // For AAR libraries, attempt to read autogenerated sources.
        if (isLibrary) {
            def genSourceDirs = findGeneratedSourceDirs(getAppBuildDir(), getFlavorDir())
            genSourceDirs.each { dir -> sourcesJarTask.from dir}
            if (explicitScavengeTask()) {
                JavaCompile scavengeTask = project.tasks.findByName(Tasks.SCAVENGE) as JavaCompile
                if (scavengeTask)
                    scavengeTask.source(genSourceDirs)
                else
                    project.logger.error msg("ERROR: scavenge task is missing.")
            }
            // Create dependency on source JAR task in order to create
            // the R.java files. This cannot happen at an earlier
            // stage because 'assemble' creates a circular dependency
            // and thus we use 'assemble{Debug,Release}' (or
            // equivalent flavor task, given via the 'flavor'
            // parameter).
            createSourcesJarDep(sourcesJarTask)
        }
    }

    /**
     * Analogous to configureSourceJarTask(), needed for Android,
     * where no JAR task exists in the Android Gradle plugin. The task
     * is not fully created here; its inputs and dependencies are set
     * in the "afterEvaluate" stage (see method markMetadataToFix()).
     */
    @Override
    void configureCodeTask() {
        Jar codeJarTask = project.tasks.create(CODE_ARCHIVE, Jar)
        codeJarTask.description = 'Generates the code archive'
        codeJarTask.group = Conventions.TOOL_NAME
    }

    /**
     * Late configuration for the code archive task.
     */
    private void configureCodeTaskAfterEvaluate() {
        // Update location of class files for JAR task.
        Jar codeTask = project.tasks.findByName(CODE_ARCHIVE) as Jar
        if (!codeTask) {
            project.logger.warn(msg("ERROR: code task ${CODE_ARCHIVE} does not exist!"))
            return
        }
        String classDir = "${appBuildDir}/intermediates/classes/${flavorDir}"
        if (!(new File(classDir)).exists()) {
            project.logger.warn msg("WARNING: class directory does not exist: ${classDir}, maybe a wrong 'flavor'/'buildType' setting? (Ignore this warning if this is a clean build.)")
        }

        codeTask.from(classDir)

        // Create dependency (needs configuration section so it must happen late).
        // Copy compiled outputs to local bundle cache.
        Task assembleTask = project.tasks.findByName(assembleTaskName)
        if (!assembleTask) {
            project.logger.warn(msg("ERROR: build task ${assembleTaskName} does not exist!"))
            return
        }
        codeTask.dependsOn assembleTask

        codeTask.doLast {
            String output = getOutputCodeArchive()
            if (output == null) {
                project.logger.warn msg("WARNING: could not determine code output of project ${project.name}.")
                return
            }
            File codeArchive = new File(output)
            File target = new File(repackageExt.getBundleDir(project), codeArchive.name)
            Files.copy(codeArchive.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Override
    String getOutputCodeArchive() {
        List<String> outputs = AndroidAPI.getOutputs(project, repackageExt.buildType, repackageExt.flavor)
        project.logger.info msg("Found code outputs: ${outputs}")
        if (outputs.size() == 1) {
            return outputs[0]
        } else if (outputs.size() == 0) {
            project.logger.warn msg("WARNING: no outputs for project in ${project.name}")
        } else if (repackageExt.apkFilter != null) {
            List<String> filteredOutputs = outputs.findAll { it.contains(repackageExt.apkFilter) }
            int sz = filteredOutputs.size()
            if (sz == 0) {
                project.logger.error msg("ERROR: filter '${repackageExt.apkFilter}' does not match any code output.")
            } else if (sz == 1) {
                return filteredOutputs[0]
            } else {
                project.logger.error msg("ERROR: filter '${repackageExt.apkFilter}' matches too many code outputs: ${filteredOutputs}")
            }
        } else {
            project.logger.error msg("ERROR: too many outputs (${outputs}), please set filter via option 'apkFilter'.")
        }
        return null
    }

    @Override
    void configureConfigurationsTask() {
        Task confTask = project.tasks.create(Tasks.CONFIGURATIONS, Task)
        confTask.description = 'Generates the configurations archive'
        confTask.group = Conventions.TOOL_NAME
        confTask.doFirst {
            if (repackageExt.ignoreConfigurations) {
                project.logger.warn msg("WARNING: ignoreConfigurations = true, configuration files will not be read.")
            } else {
                readConfigurationFiles()
            }
        }
    }

    /**
     * Disable rules by adding a "disabling configuration" with -dont* directives.
     */
    private void disableRules() {
    }

    /**
     * Injects a configuration file (containing extra rules or directives) to
     * the transform phase.
     *
     * @param conf           the configuration file
     * @param errorMessage   a message to show when a problem occurs (warning/error)
     */
    private void injectConfiguration(File conf, String errorMessage) {
        AndroidAPI.forEachTransform(
            project, flavorAndBuildType, { FileCollection pros ->
                try {
                    if (pros instanceof ConfigurableFileCollection)
                        ((ConfigurableFileCollection)pros).from(conf)
                    else
                        project.logger.warn(errorMessage + " Unhandled file collection type: ${pros.class}")

                } catch (Throwable t) {
                    t.printStackTrace()
                    project.logger.error errorMessage
                }
            }
        )
    }

    /**
     * Read the configuration files of all appropriate transform tasks
     * set up by the Android Gradle plugin. This uses the internal API
     * of the Android Gradle plugin.
     */
    private void readConfigurationFiles() {
        if (!repackageExt.configurationFiles) {
            // Preserve the ordering of the configurations, while avoiding duplicates.
            Set<File> allPros = new LinkedHashSet<>()
            // Gather test configurations so that they can be excluded.
            Set<String> testConfPaths = AndroidAPI.getTestConfigurations(project, buildType)
                .collect { it.canonicalPath } as Set<String>
            testConfPaths.each { project.logger.debug msg("Found test configuration: ${it}") }
            AndroidAPI.forEachTransform(
                project, flavorAndBuildType, { FileCollection pros -> pros.each { File pro ->
                    if (!testConfPaths.contains(pro.canonicalPath)) {
                        allPros.add(pro)
                    }
                }})

            project.logger.info msg("Found ${allPros.size()} configuration files:")
            repackageExt.configurationFiles = new ArrayList<String>()
            allPros.each {
                project.logger.info msg("Using rules from configuration file: ${it.canonicalPath}")
                repackageExt.configurationFiles.add(it.canonicalPath)
            }
            if (allPros.size() == 0) {
                project.logger.info msg("No project configuration files were found.")
                return
            }
        } else {
            project.logger.info msg("Using provided configuration files: ${repackageExt.configurationFiles}")
        }

        File confZip = getConfFile()
        List<String> warnings = [] as List<String>
        Archiver.zipConfigurations(repackageExt.configurationFiles.collect { new File(it) }, confZip, warnings)
        if (warnings.size() > 0)
            warnings.each { project.logger.warn msg(it) }
        project.logger.info msg("Configurations written to: ${confZip.canonicalPath}")
    }


    @Override
    String codeTaskName() { return CODE_ARCHIVE }

    /**
     * Returns the code files that will be given as "input" to the server.
     * These should be .apk or .aar files.
     *
     * @return a list of file paths
     */
    @Override
    List<String> getInputFiles() {
        String outputsTask = getAssembleTaskName()
        project.logger.info msg("Using non-library outputs from task ${outputsTask}")
        List<String> ars = AndroidAPI.getOutputs(project, project.tasks.findByName(outputsTask))
        project.logger.info msg("Calculated non-library outputs: ${ars}")
        return ars
    }

    /**
     * Returns the library files of the code. For .apk inputs, this
     * returns nothing, since everything is bundled in the .apk.
     * For .aar inputs, this returns a list of dependencies (libraries).
     *
     * @return a list of file paths
     */
    @Override
    List<String> getLibraryFiles() {
        // Only upload dependencies when in AAR mode.
        if (!isLibrary) {
            return null
        }

        project.logger.info msg("Detecting library outputs...")
        List<String> extraInputFiles = repackageExt.getExtraInputFiles(project.rootDir)
        return getDependencies().asList() + extraInputFiles
    }

    private Set<String> getDependencies() {
        Set<String> ret = cachedDeps.collect { File f ->
            if (!f.exists()) {
                throwRuntimeException(msg("Dependency ${f} does not exist!"))
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
        def cpList = (cLoader instanceof URLClassLoader) ?
            ((URLClassLoader)cLoader).getURLs() : AndroidAPI.getAsURIs(cLoader)

        if (cpList == null) {
            throwRuntimeException(msg('AndroidPlatform: cannot get classpath for jcplugin, cLoader is ' + cLoader))
        }
        return cpList.collect().join(File.pathSeparator).replaceAll('file://', '')
    }

    private String getFlavorDir() {
        String buildType = getBuildType()
        String flavor = repackageExt.flavor
        return flavor == null? "${buildType}" : "${flavor}/${buildType}"
    }

    private String getSubprojectName(boolean crash = true) {
        if (repackageExt.subprojectName == null) {
            if (crash) {
                throwRuntimeException(msg("Please set subprojectName to the name of the app subproject (e.g. 'Application')."))
            }
            return ""
        } else
            return repackageExt.subprojectName
    }

    /**
     * Read the project name. Android projects may have
     * project.name be the default name of the 'app'
     * directory, so we use the group name too.
     *
     * @return a group-qualifed project name
     */
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
     */
    private void configureCompileHook() {

        String javacPluginArtifact

        try {
            javacPluginArtifact = JcPlugin.jcPluginArtifact
        } catch (Exception ex) {
            ex.printStackTrace()
        }

        if (javacPluginArtifact) {
            List<String> jcplugin = JcPlugin.getJcPluginClasspath()
            int sz = jcplugin.size()
            if (sz == 0) {
                project.logger.warn msg('WARNING: could not find metadata processor, sources will not be processed.')
                return
            } else {
                String jcpluginProc = jcplugin.get(0)
                project.dependencies.add('annotationProcessor', project.files(jcpluginProc))
                project.logger.info msg("Metadata processor added: ${jcpluginProc}")
                if (sz > 1) {
                    project.logger.warn msg('WARNING: too many metadata processors found: ' + jcplugin)
                }
            }
        } else {
            project.logger.warn msg('WARNING: Could not integrate metadata processor, sources will not be processed.')
            return
        }

        Set<JavaCompile> tasks = project.tasks.findAll { it instanceof JavaCompile } as Set<JavaCompile>
        if (tasks.size() == 0) {
            project.logger.error msg("Could not integrate metadata processor, no compile tasks found.")
            return
        }

        tasks.each { task ->
            project.logger.info msg("Plugging metadata processor into task ${task.name}")
            RepackagePlugin.addPluginCommandArgs(task, repackageExt.getBundleDir(project), repackageExt.jcPluginOutput)
        }
    }

    /**
     * Check configuration sections in Android Gradle scripts.
     *
     * @return true if a configuration block is defined, false otherwise
     */
    @Override
    boolean definesRequiredProperties() {
        if (repackageExt.subprojectName == null) {
            String rootPath = project.rootDir.canonicalPath
            String projPath = project.projectDir
            if (projPath.startsWith(rootPath) && projPath.size() > rootPath.size()) {
                String suffix = Archiver.stripRootPrefix(projPath.substring(rootPath.size()))
                repackageExt.subprojectName = suffix
                project.logger.warn msg("WARNING: missing property 'subprojectName', using: ${suffix}")
            } else {
                project.logger.warn msg("WARNING: missing property 'subprojectName', using top-level directory")
	            repackageExt.subprojectName = DEFAULT_SUBPROJECT_NAME
            }
	    }
        if (repackageExt.buildType == null) {
            project.logger.warn msg("WARNING: missing property 'buildType', assuming buildType=${DEFAULT_BUILD_TYPE}")
            repackageExt.buildType = DEFAULT_BUILD_TYPE
	    }
        if (repackageExt.flavor == null) {
            Set<String> pFlavors = AndroidAPI.getFlavors(project)
            if (pFlavors.size() > 0) {
                project.logger.warn msg("WARNING: property 'flavor' not set but these flavors were found: ${pFlavors}")
            }
        }
	    return super.definesRequiredProperties()
    }

    @Override
    boolean isCodeArtifact(String filename) {
        String n = filename.toLowerCase()
        if (n.endsWith('.apk')) {
            return true
        } else if (n.endsWith('.aar')) {
            // Ignore AAR artifacts, so that they are not posted when
            // the user invokes the "post bundle" task globally.
            project.logger.warn msg("WARNING: AAR artifact is currently ignored as a standalone artifact to post: ${filename}")
        }
        return false
    }
}
