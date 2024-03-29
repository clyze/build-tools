package com.clyze.build.tools.gradle

import com.clyze.build.tools.Archiver
import groovy.io.FileType
import groovy.transform.CompileStatic
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.function.Function
import org.apache.commons.io.FileUtils
import com.clyze.build.tools.Conventions
import org.clyze.utils.ContainerUtils
import org.clyze.utils.AndroidDepResolver
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

import static com.clyze.build.tools.Conventions.msg
import static org.clyze.utils.JHelper.throwRuntimeException

/**
 * This class controls how the plugin adapts to Android Gradle projects.
 */
@CompileStatic
class AndroidPlatform extends Platform {

    /** The name prefix of the Android Gradle plugin task that will
      * compile and package the program (APK format). */
    static final String ASSEMBLE_PRE = 'assemble'
    /** The name prefix of the Android Gradle plugin task that will
     * compile and package the program (AAB format). */
    static final String BUNDLE_PRE = 'bundle'

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
            if (!tasks.any { String s -> PTask.values().any { s.endsWith(it.name) } }) {
                project.logger.info msg("No ${Conventions.TOOL_NAME} task invoked, skipping configuration.")
                return
            }

            // Read properties from build.gradle.
            if (!definesRequiredProperties()) {
                project.logger.warn MISSING_PROPERTIES
                return
            }

            configureCodeTaskAfterEvaluate()

            if (repackageExt.sources)
                configureSourceTasksAfterEvaluate()

            Task confTask = project.tasks.findByName(PTask.CONFIGURATIONS.name) as Task
            confTask.dependsOn getBuildTaskName()
            activateSpecialConfiguration()

            configureTestRepackaging()
        }
    }

    /**
     * Configure the tasks that invoke tests to check if repackaging is correct.
     */
    private void configureTestRepackaging() {
        // Insert test-code repackager between the javac
        // invocation and the test runner.
        Task testRepackageTask = project.tasks.findByName(PTask.REPACKAGE_TEST.name) as Task
        String utciTaskName = getUnitTestCompileInnerTask()
        Task utciTask = project.tasks.findByName(utciTaskName)
        String utcTaskName = getUnitTestCompileTask()
        Task utcTask = project.tasks.findByName(utcTaskName) as Task
        if (utciTask && utcTask) {
            testRepackageTask.dependsOn getUnitTestCompileInnerTask()
            utcTask.dependsOn testRepackageTask
        } else if (!utciTask) {
            project.logger.warn msg("WARNING: no '${utciTaskName}' task, skipping configuration of task '${PTask.REPACKAGE_TEST.name}'.")
        } else if (!utcTask) {
            project.logger.warn msg("WARNING: no '${utcTaskName}' task, skipping configuration of task '${PTask.REPACKAGE_TEST.name}'.")
        }
    }

    // Resolves the dependencies of the project.
    private Set<String> resolveDeps(String appBuildHome) {
        Set<String> deps = new HashSet<>()

        // Find the location of the Android SDK.
        resolver.findSDK(project.rootDir.canonicalPath)
        // Don't resolve dependencies the user overrides.
        if (repackageExt.replacedByExtraInputs)
            resolver.ignoredArtifacts.addAll(repackageExt.replacedByExtraInputs)

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
        String buildType = getBuildType()
        if (!buildType) {
            throwRuntimeException(msg("ERROR: could not determine build type"))
        }
        String flavor = repackageExt.flavor
        return flavor ? flavor + buildType.capitalize() : buildType
    }

    private String getBuildTaskName() {
        String prefix = repackageExt.aab ? BUNDLE_PRE : ASSEMBLE_PRE
        String taskName = prefix + flavorAndBuildType.capitalize()
        if (buildType != 'debug' && buildType != 'release') {
            project.logger.info msg("Unknown build type ${buildType}, assuming build task: ${taskName}")
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

    private void createSourcesJarDependency(Jar sourcesTask) {
        String codeTaskName = PTask.ANDROID_CODE_ARCHIVE.name
        Task codeTask = project.tasks.findByName(codeTaskName)
        if (!codeTask)
            project.logger.warn msg("ERROR: source-dependency task ${codeTaskName} does not exist!")
        else {
            project.logger.debug msg("Depending on task '${codeTaskName}' to generate the sources JAR.")
            sourcesTask.dependsOn codeTask
        }
    }

    // Add auto-generated Java files (examples are the app's R.java,
    // other R.java files, and classes in android.support packages).
    private List<String> findGeneratedSourceDirs() {
        List<String> genSourceDirs = []

        File gDir1 = new File("${appBuildDir}/generated/source")
        if (!gDir1.exists()) {
            project.logger.warn msg("WARNING: Generated sources dir does not exist: ${gDir1}")
        } else {
            // Add subdirectories containing .java files.
            String flavorDir = getFlavorDir()
            procGeneratedSourceDir(genSourceDirs, gDir1, 1,
                                   { it.canonicalPath.endsWith(flavorDir) ? it : null })
        }

        String variantName = getFlavorAndBuildType()
        def matchingSubDir =
            { String subDir -> { File dir ->
            if (dir.canonicalPath.endsWith(variantName)) {
                File out = new File(dir, subDir)
                if (out.exists())
                    return out
            }
            return null
        } as Function<File, File> }
        File gDir2 = new File("${appBuildDir}/generated/ap_generated_sources")
        if (gDir2.exists())
            procGeneratedSourceDir(genSourceDirs, gDir2, 0, matchingSubDir('out'))
        File gDir3 = new File("${appBuildDir}/generated/not_namespaced_r_class_sources")
        if (gDir3.exists())
            procGeneratedSourceDir(genSourceDirs, gDir3, 0, matchingSubDir('r'))

        return genSourceDirs
    }

    /**
     * Helper method to find generated sources.
     *
     * @param genSourceDirs  the list to receive any detected source directory
     * @param genDir         the directory to start searching
     * @param depth          how many levels to dive before applying the checking logic
     * @param dirCheck       the check logic: applied to a directory <f>, it should
     *                       return (sub)directory <g> containing sources (null for none)
     */
    private void procGeneratedSourceDir(List<String> genSourceDirs, File genDir,
                                        int depth, Function<File, File> dirCheck) {
        if (depth > 0) {
            genDir.eachFile (FileType.DIRECTORIES) { File dir ->
                procGeneratedSourceDir(genSourceDirs, dir, depth-1, dirCheck)
            }
        } else {
            genDir.eachFile (FileType.DIRECTORIES) { File dir ->
                File sourceDir = dirCheck.apply(dir)
                if (sourceDir) {
                    // Add subdirectories containing .java files.
                    boolean containsJava = false
                    sourceDir.eachFileRecurse (FileType.FILES) { f ->
                        if ((!containsJava) && f.name.endsWith('.java'))
                            containsJava = true
                    }
                    if (containsJava) {
                        project.logger.info msg("Found generated Java sources in ${sourceDir}")
                        genSourceDirs << sourceDir.absolutePath
                    }
                }
            }
        }
    }

    /**
     * Returns the Gradle build directory of the current subproject.
     *
     * @return the Gradle build directory path
     */
    String getAppBuildDir() {
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
            scavengeTask.dependsOn project.tasks.findByName(buildTaskName)

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
            cp.addAll(ContainerUtils.toJars(scavengeDeps as List, true, [], tmpDirs))
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
    void gatherSources(Jar sourcesTask) {}

    /**
     * Configure the task that gathers the project sources. This should run
     * after subproject Android tasks have already been configured.
     */
    void configureSourceTasksAfterEvaluate() {
        configureSourceTasks()

        // Wait for code build to finish, to gather autogenerated
        // sources.  This cannot happen at an earlier stage because
        // assemble/bundle tasks may create a circular dependency and
        // thus we use '{assemble,bundle}{Debug,Release}' (or equivalent
        // flavor task, given via the 'flavor' parameter).
        Jar sourcesTask = project.tasks.findByName(PTask.SOURCES_JAR.name) as Jar
        createSourcesJarDependency(sourcesTask)

        // Check if the Maven convention is followed for the sources.
        String appPath = "${project.rootDir}/${subprojectName}"
        String srcMaven = "src/main/java"
        String srcSimple = "src/"
        if ((new File("${appPath}/${srcMaven}")).exists()) {
            project.logger.info msg("Using Maven-style source directories: ${srcMaven}")
            sourcesTask.from srcMaven
        } else if ((new File("${appPath}/${srcSimple}")).exists()) {
            project.logger.info msg("Using sources: ${srcSimple}")
            sourcesTask.from srcSimple
        } else if (isDefinedSubProject()) {
            throwRuntimeException(msg("Could not find source directory"))
        }
        String srcTestMaven = "src/test/java"
        if ((new File("${appPath}/${srcTestMaven}")).exists()) {
            project.logger.info msg("Using Maven-style test directories: ${srcTestMaven}")
            sourcesTask.from srcTestMaven
        }
        String srcAndroidTestMaven = "src/androidTest/java"
        if ((new File("${appPath}/${srcAndroidTestMaven}")).exists()) {
            project.logger.info msg("Using Maven-style Android test directories: ${srcAndroidTestMaven}")
            sourcesTask.from srcAndroidTestMaven
        }

        // Attempt to read autogenerated sources.
        def genSourceDirs = findGeneratedSourceDirs()
        genSourceDirs.each { dir -> sourcesTask.from dir}
        if (explicitScavengeTask()) {
            JavaCompile scavengeTask = project.tasks.findByName(PTask.SCAVENGE.name) as JavaCompile
            if (scavengeTask)
                scavengeTask.source(genSourceDirs)
            else
                project.logger.error msg("ERROR: scavenge task is missing.")
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
        Jar codeJarTask = project.tasks.create(PTask.ANDROID_CODE_ARCHIVE.name, Jar)
        codeJarTask.description = 'Generates the code archive'
        codeJarTask.group = Conventions.TOOL_NAME
    }

    /**
     * Late configuration for the code archive task.
     */
    private void configureCodeTaskAfterEvaluate() {
        // Update location of class files for JAR task.
        Jar codeTask = project.tasks.findByName(PTask.ANDROID_CODE_ARCHIVE.name) as Jar
        if (!codeTask) {
            project.logger.warn(msg("ERROR: code task ${PTask.ANDROID_CODE_ARCHIVE.name} does not exist!"))
            return
        }
        String classDir1 = "${appBuildDir}/intermediates/classes/${flavorDir}"
        if ((new File(classDir1)).exists())
            codeTask.from(classDir1)
        else {
            String classDir2 = "${appBuildDir}/intermediates/javac/${flavorAndBuildType}/classes/"
            if ((new File(classDir2)).exists())
                codeTask.from(classDir2)
            else
                project.logger.debug msg("WARNING: intermediate class directory does not exist, locations checked: ${classDir1}, ${classDir2}. Maybe a wrong 'flavor'/'buildType' setting? (Ignore this warning if this is a clean build.)")
        }

        // Create dependency (needs configuration section so it must happen late).
        // Copy compiled outputs to local build cache.
        Task buildTask = project.tasks.findByName(buildTaskName)
        if (!buildTask) {
            project.logger.warn(msg("ERROR: build task ${buildTaskName} does not exist!"))
            return
        }
        codeTask.dependsOn buildTask

        codeTask.doLast {
            String output = getOutputCodeArchive()
            if (output == null) {
                project.logger.warn msg("WARNING: could not determine code output of project ${project.name}.")
                return
            }
            File codeArchive = new File(output)
            File target = new File(repackageExt.getSnapshotDir(project), codeArchive.name)
            Files.copy(codeArchive.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private void throwNoAabFound(File aabDir) {
        String aabError = "ERROR: no .aab files found in ${aabDir.canonicalPath}"
        project.logger.error msg(aabError)
        throw new RuntimeException(aabError)
    }

    @Override
    String getOutputCodeArchive() {
        List<String> outputs
        if (repackageExt.aab) {
            // Use first .aab from "build/outputs/bundle/<VARIANT>" as output code archive.
            File aabDir = Paths.get(appBuildDir, 'outputs', 'bundle', getFlavorAndBuildType()).toFile()
            File[] aabDirFiles = aabDir.listFiles()
            if (!aabDirFiles || aabDirFiles.length == 0)
                throwNoAabFound(aabDir)
            int aabIdx = aabDirFiles.findIndexOf {it.name.toLowerCase().endsWith('.aab') }
            if (aabIdx == -1)
                throwNoAabFound(aabDir)
            else
                outputs = [aabDirFiles[aabIdx].canonicalPath]
        } else
            outputs = AndroidAPI.getOutputs(project, repackageExt.buildType, repackageExt.flavor)
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

    /**
     * Injects a configuration file (containing extra rules or directives) to
     * the transform phase.
     *
     * @param conf           the configuration file
     * @param errorMessage   a message to show when a problem occurs (warning/error)
     */
    @Override
    protected void injectConfiguration(File conf, String errorMessage) {
        AndroidAPI.forEachRepackageTransform(
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
    @Override
    protected void readConfigurationFiles() {
        if (!repackageExt.configurationFiles) {
            // Preserve the ordering of the configurations, while avoiding duplicates.
            Set<File> allPros = new LinkedHashSet<>()
            // Gather test configurations so that they can be excluded.
            Set<String> testConfPaths = AndroidAPI.getTestConfigurations(project, buildType)
                .collect { it.canonicalPath } as Set<String>
            testConfPaths.each { project.logger.debug msg("Found test configuration: ${it}") }
            AndroidAPI.forEachRepackageTransform(
                project, flavorAndBuildType, { FileCollection pros ->
                    if (!pros)
                        return
                    project.logger.debug msg("pros=${pros}, size=${pros.files.size()}")
                    pros.each { File pro ->
                        project.logger.debug msg("Processing rules file: ${pro}")
                        if (!testConfPaths.contains(pro.canonicalPath)) {
                            allPros.add(pro)
                        }
                    }
                })

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

        zipConfigurations()
    }

    @Override
    String codeTaskName() { return PTask.ANDROID_CODE_ARCHIVE.name }

    /**
     * Returns the code files that will be given as "input" to the server.
     * These should be .apk, .aar, or .aab files.
     *
     * @return a list of file paths
     */
    @Override
    List<String> getInputFiles() {
        String outputsTask = getBuildTaskName()
        project.logger.info msg("Using non-library outputs from task ${outputsTask}")
        List<String> ars = AndroidAPI.getOutputs(project, project.tasks.findByName(outputsTask))
        project.logger.info msg("Calculated non-library outputs: ${ars}")
        throwIfInputHasExtension(ars, repackageExt.aab ? '.apk' : '.aab')
        return ars
    }

    private void throwIfInputHasExtension(Collection<String> ars, String ext) {
        String inputWithExt = ars.find { it.endsWith(ext) }
        if (inputWithExt != null)
            throwRuntimeException(msg("Plugin option aab=${repackageExt.aab} but ${ext} input found: ${inputWithExt}"))
    }

    /**
     * Returns the library files of the code. For app inputs, this
     * returns nothing, since everything is included in the app archive.
     * For library (.aar) inputs, this returns a list of dependencies
     * (libraries).
     *
     * @return a list of file paths
     */
    @Override
    Set<String> getLibraryFiles() {
        // Only upload dependencies when in AAR mode.
        if (!isLibrary) {
            return null
        }

        project.logger.info msg("Detecting library outputs...")
        Set<String> extraInputFiles = repackageExt.getExtraInputFiles(project.rootDir) as Set<String>
        return getDependencies() + extraInputFiles
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
            ((URLClassLoader)cLoader).getURLs() : AUtils.getAsURIs(cLoader)

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

    private String getSubprojectName() {
        final boolean crash = true
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
                project.logger.debug msg("WARNING: missing property 'subprojectName', using: ${suffix}")
            } else {
                project.logger.debug msg("WARNING: missing property 'subprojectName', using top-level directory")
	            repackageExt.subprojectName = DEFAULT_SUBPROJECT_NAME
            }
	    }
        if (repackageExt.buildType == null) {
            project.logger.debug msg("WARNING: missing property 'buildType', assuming buildType=${DEFAULT_BUILD_TYPE}")
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
        if (AUtils.isAppCodeArtifact(n)) {
            return true
        } else if (n.endsWith('.aar')) {
            // Ignore AAR artifacts, so that they are not posted when
            // the user invokes the "post snapshot" task globally.
            project.logger.warn msg("WARNING: AAR artifact is currently ignored as a standalone artifact to post: ${filename}")
        }
        return false
    }

    @Override
    protected String getDefaultStack() {
        return Conventions.ANDROID_STACK
    }

    @Override
    protected String getDefaultAutomatedRepackagingProfile() {
        return Conventions.DEFAULT_ANDROID_CLYZE_PROFILE
    }
}
