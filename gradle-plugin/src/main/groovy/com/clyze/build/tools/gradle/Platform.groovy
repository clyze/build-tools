package com.clyze.build.tools.gradle

import com.clyze.client.Printer
import groovy.transform.CompileStatic
import com.clyze.build.tools.Archiver
import com.clyze.build.tools.Conventions
import com.clyze.build.tools.JcPlugin
import com.clyze.build.tools.Settings
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile

import static com.clyze.build.tools.Conventions.msg
import static RepackagePlugin.dependOn

/**
 * The Java platform used: plain Java or Android. Each platform is
 * handled by a different Gradle plugin (plain Java vs. Android), so
 * different tasks exist and the Gradle plugin must examine different
 * metadata on each platform.
 */

@CompileStatic
abstract class Platform {

    /** The name of the default rules file (in our format). */
    private static final String DEFAULT_RULES = 'clyze.json'
    /**
     * Message to display when configuration block is missing
     * and initialization cannot happen.
     */
    protected static final String MISSING_PROPERTIES = msg("WARNING: Cannot configure ${Conventions.TOOL_NAME} plugin with defaults, check '${Extension.SECTION_NAME}' configuration block in build.gradle.")
    /** The current project. */
    Project project
    /** Field to hold the configuration data structure. */
    private Extension repackageExt = null
    /** The special configuration used to turn off optimization passes. */
    protected Conventions.SpecialConfiguration sc = null

    /**
     * Default constructor.
     *
     * @param project the project being built
     */
    Platform(Project project) {
        this.project = project
    }

    /**
     * Reads the plugin configuration data structure ('extension').
     *
     * @return the plugin configuration object
     */
    protected synchronized Extension getRepackageExt() {
        if (!repackageExt) {
            repackageExt = Extension.of(project)
        }
        if (!repackageExt)
            project.logger.warn msg("WARNING: could not find extension")
        return repackageExt
    }

    /**
     * Check if the build.gradle section defines the needed properties.
     *
     * @return true if build.gradle defines the section
     */
    boolean definesRequiredProperties() {
        Extension ext = getRepackageExt()
        // We don't check for 'options', as that is never empty (but
        // initialized to defaults).
        def err = { project.logger.error msg("ERROR: missing property: '${it}'") }
        if (ext.host == null) {
            project.logger.debug msg("WARNING: missing property 'host', assuming host=${Conventions.DEFAULT_HOST}")
            ext.host = Conventions.DEFAULT_HOST
        }
        if (ext.port == 0) {
            String port = Settings.getDefaultPort()
            if (port) {
                ext.port = port as Integer
                project.logger.info msg("Using configured port ${ext.port}")
            } else {
                port = GradleProps.get(project, 'clue_port')
                if (port) {
                    ext.port = port as Integer
                    project.logger.debug msg("WARNING: missing property 'port', using Gradle property 'clue_port'= ${ext.port}")
                } else {
                    err 'port'
                    return false
                }
            }
        }
        if (ext.username == null) {
            ext.username = Conventions.DEFAULT_USERNAME
        }
        if (ext.stacks == null) {
            ext.stacks = [getDefaultStack()] as List<String>
            project.logger.debug msg("Missing property 'stacks', assuming stacks=${ext.stacks}")
        }
        if (ext.ruleFile == null) {
            project.logger.debug msg("Missing property 'ruleFile', assuming ruleFile=${DEFAULT_RULES}")
            ext.ruleFile = DEFAULT_RULES
        }
        return true
    }

    /**
     * Returns the interesting visible (sub-)projects.
     *
     * @return a set of projects
     */
    static Set<Project> getInterestingProjects(Project project) {
        Set<Project> projects = project.subprojects
        projects.add(project)
        return projects
    }

    /**
     * Configuration of the sources/metadata tasks. Separated here
     * so that it can be invoked as a last step.
     */
    void configureSourceTasks() {
        Extension ext = getRepackageExt()
        if (ext.sources) {
            if (ext.useJavacPlugin) {
                project.logger.debug msg("Configuring compile hook")
                configureCompileHook()
            }
            project.logger.debug msg("Configuring sources task")
            configureSourceJarTask()
            project.logger.debug msg("Configuring metadata task")
            configureMetadataTask()
            project.logger.debug msg("Configuring bundling task (step 2)")
            configureCreateSnapshotTask_step2()
        } else
            project.logger.info msg("Note: sources will not be processed.")
    }

    private synchronized void configureSourceJarTask() {
        def existing = project.tasks.findByName(PTask.SOURCES_JAR.name)
        Jar task
        if (existing == null) {
            task = project.tasks.create(PTask.SOURCES_JAR.name, Jar)
        } else if (existing instanceof Jar) {
            String GRADLE_SOURCES_JAR_TASK = 'sourcesJar'
            List<String> taskNames = project.gradle.startParameter.taskNames
            // Corner case: if the user tries to run the original Gradle task together with
            // the Clyze tasks (which modify that task), show a warning.
            if (taskNames.contains(GRADLE_SOURCES_JAR_TASK) && GRADLE_SOURCES_JAR_TASK == PTask.SOURCES_JAR.name && PTask.nonSourceTaskMatches(taskNames)) {
                project.logger.warn msg("WARNING: running Clyze Gradle tasks together with task '${GRADLE_SOURCES_JAR_TASK}' is not supported.")
                return
            } else {
                // Heuristic to handle repeated configuration by Gradle.
                project.logger.info msg("Reusing existing task ${PTask.SOURCES_JAR.name}")
                task = existing as Jar
            }
        } else {
            project.logger.warn msg("WARNING: Non-JAR task ${PTask.SOURCES_JAR.name} exists, cannot configure ${Conventions.TOOL_NAME} plugin.")
            return
        }

        String prefix = project.name ? "${project.name}-": ""
        String sourcesName = prefix + Conventions.SOURCES_FILE
        project.logger.info msg("Sources archive: ${sourcesName}")
        task.archiveFileName.set(sourcesName)

        task.destinationDirectory.set(repackageExt.getSnapshotDir(project))
        task.description = 'Generates the sources JAR'
        task.group = Conventions.TOOL_NAME
        task.archiveClassifier.set('sources')

        gatherSources(task)
    }

    /**
     * Configures the metadata scavenging task.
     */
    private void configureMetadataTask() {
        Zip task = project.tasks.create(PTask.JCPLUGIN_ZIP.name, Zip)
        task.description = 'Zips the output files of the metadata processor'
        task.group = Conventions.TOOL_NAME

        // If a separate metadata generation task exists, depend on it;
        // otherwise depend on build task (which integrates metadata generation).
        if (explicitScavengeTask()) {
	        task.dependsOn project.tasks.findByName(PTask.SCAVENGE.name)
        } else {
	        task.dependsOn project.tasks.findByName(codeTaskName())
        }

        task.archiveFileName.set(Conventions.METADATA_FILE)
        File scavengeDir = repackageExt.getSnapshotDir(project)
        task.destinationDirectory.set(scavengeDir)
        File jsonOutput = new File(scavengeDir, "json")
        task.from jsonOutput
    }

    private void configureCreateSnapshotTask_step2() {
        Task createSnapshotTask = project.tasks.findByName(PTask.CREATE_SNAPSHOT.name)
        dependOn(project, createSnapshotTask, PTask.JCPLUGIN_ZIP.name, 'metadata task', false)
        dependOn(project, project.tasks.findByName(PTask.JCPLUGIN_ZIP.name), codeTaskName(), 'core build task (metadata dependency)', false)
        dependOn(project, createSnapshotTask, PTask.SOURCES_JAR.name, 'sources task', false)
    }

    static class LogPrinter implements Printer {
        Project project

        LogPrinter(Project project) {
            this.project = project
        }

        @Override
        void error(String message) {
            project.logger.error msg(message)
        }

        @Override
        void warn(String message) {
            project.logger.warn msg(message)
        }

        @Override
        void debug(String message) {
            project.logger.debug msg(message)
        }

        @Override
        void info(String message) {
            project.logger.info msg(message)
        }

        @Override
        void always(String message) {
            System.out.println(msg(message))
            System.out.flush()
        }
    }

    Printer getPrinter() {
        return new LogPrinter(project)
    }

    /**
     * Configures the metadata processor (when integrated with a build task).
     */
    protected void configureCompileHook() {
        // If not using an explicit metadata scavenge task, hook into the
        // compiler instead. If this is a run that throws away code (because
        // no archive task is called), skip this configuration.
        def taskArch = project.gradle.startParameter.taskNames.find { it.endsWith(codeTaskName()) || it.endsWith(PTask.CREATE_SNAPSHOT.name) }
        if (explicitScavengeTask() || !taskArch)
            return

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
                project.logger.debug msg("Metadata processor added: ${jcpluginProc}")
                if (sz > 1) {
                    project.logger.warn msg('WARNING: too many metadata processors found: ' + jcplugin)
                }
            }
        } else {
            project.logger.warn msg('WARNING: Could not integrate metadata processor, sources will not be processed.')
            return
        }

        Set<JavaCompile> compileTasks = project.tasks.findAll { it instanceof JavaCompile } as Set<JavaCompile>
            if (compileTasks.size() == 0) {
            project.logger.error msg("Could not integrate metadata processor, no compile tasks found.")
            return
        }

        compileTasks.each { task ->
            project.logger.info msg("Plugging metadata processor into task ${task.name}")
            RepackagePlugin.addPluginCommandArgs(task, repackageExt.getSnapshotDir(project), repackageExt.jcPluginOutput)
        }
    }

    /**
     * Configure the task that gathers configuration files (containing
     * keep rules and directives).
     */
    void configureConfigurationsTask() {
        Task confTask = project.tasks.create(PTask.CONFIGURATIONS.name, Task)
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
    protected void activateSpecialConfiguration() {
        sc = Conventions.getSpecialConfiguration(repackageExt.getSnapshotDir(project), true, true)
        if (!sc)
            project.logger.warn(Conventions.COULD_NOT_DISABLE_RULES + ' No disabling configuration.')
        else
            injectConfiguration(sc.file, Conventions.COULD_NOT_DISABLE_RULES)
        if (repackageExt.printConfig)
            repackageExt.configurationFiles = [ sc.outputRulesPath ] as List<String>
    }

    /**
     * Zips the configurations found in the plugin extension, to generate
     * the configurations archive of the snapshot.
     */
    protected void zipConfigurations() {
        Extension ext = getRepackageExt()
        File confZip = new File(ext.getSnapshotDir(project), Conventions.CONFIGURATIONS_FILE)
        List<File> files = ext.configurationFiles?.collect { project.file(it) } ?: []
        Archiver.zipConfigurations(files, confZip, printer, project.rootDir.canonicalPath, sc?.file?.canonicalPath, sc?.outputRulesPath)
        project.logger.info msg("Configurations written to: ${confZip.canonicalPath}")
    }

    /**
     * Takes the compilation settings from an already configured
     * build task.
     *
     * @param task the build task to read
     */
    abstract void copyCompilationSettings(JavaCompile task)

    /**
     * Registers logic that should run after basic initialization. Used
     * for example on Android builds, where basic Gradle build tasks creation
     * is delayed and thus our plugin cannot discover these tasks early.
     * All such logic should be gathered here instead of separate
     * project.afterEvaluate() blocks, to avoid order-of-execution bugs.
     */
    abstract void markMetadataToFix()

    /** Creates a dependency for the "scavenge" task. */
    abstract void createScavengeDependency(JavaCompile scavengeTask)

    /**
     * Gathers the sources from an already configured task.
     *
     * @param task the task to read
     */
    abstract void gatherSources(Jar sourcesJarTask)

    /** Configures the task that will build the code to post to the server. */
    abstract void configureCodeTask()

    /** Returns the name of the task that constructs the code output. */
    abstract String codeTaskName()

    /**
     * Returns the code files that will be given as "input" to the server.
     * @return a list of file paths
     */
    abstract List<String> getInputFiles()

    /**
     * Returns the code files that will be given as "libraries" to the server.
     * @return a set of file paths
     */
    abstract Set<String> getLibraryFiles()

    /** Return a build classpath. */
    abstract String getClasspath()

    /** Return the project name. */
    abstract String getProjectName()

    /** Clean up resources on plugin exit. */
    abstract void cleanUp()

    /** Return the output code archive (JAR, APK, AAR). */
    abstract String getOutputCodeArchive()

    /** Checks if a filename is a code artifact. */
    abstract boolean isCodeArtifact(String filename)

    /**
     * Helper test method to control the creation of the "scavenge" task.
     *
     * @return true if the metadata processor runs in a separate Gradle task,
     *         false if the processor is integrated in an existing task.
     */
    abstract boolean explicitScavengeTask()

    /** Read configurations from current project. */
    abstract protected void readConfigurationFiles()

    /**
     * Injects a configuration file (containing extra rules or directives) to
     * the rule files currently used in the project.
     */
    abstract protected void injectConfiguration(File conf, String errorMessage);

    /**
     * Returns the default stack used for posting snapshots.
     *
     * @return the stack name
     */
    abstract protected String getDefaultStack();

    /**
     * Returns the default stack used for repackaging snapshots from Gradle.
     *
     * @return the stack name
     */
    abstract protected String getDefaultAutomatedRepackagingProfile();
}
