package com.clyze.build.tools.gradle

import com.clyze.build.tools.Conventions
import groovy.transform.CompileStatic
import org.clyze.utils.JHelper
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile

import static com.clyze.build.tools.Conventions.msg

/**
 * The entry point of the plugin.
 */
@CompileStatic
class RepackagePlugin implements Plugin<Project> {

    private Platform platform
    private Project project

    @Override
    void apply(Project project) {
        project.logger.info msg("Gradle plugin [${pluginVersion}]")

        this.project = project

        // Require Java 1.8 or higher.
        if (!JavaVersion.current().isJava8Compatible()) {
            throw new RuntimeException(msg("The plugin requires Java 1.8 or higher"))
        }

        //verify that the appropriate plugins have been applied
        if (project.plugins.hasPlugin('java')) {
            project.logger.info msg("Project platform: Java")
            platform = new JavaPlatform(project)
        } else if (project.plugins.hasPlugin('android') || project.plugins.hasPlugin('com.android.application') || project.plugins.hasPlugin('com.android.library')) {
            project.logger.info msg("Project platform: Android")
            platform = new AndroidPlatform(project)
        } else {
            throw new RuntimeException(msg("One of these plugins should be applied before the ${Conventions.TOOL_NAME} plugin: java, android, com.android.application, com.android.library"))
        }

        // Create the plugin extension that will hold all configuration.
        project.extensions.create(Extension.SECTION_NAME, Extension)
        Extension.of(project).platform = platform

        // Set the default values.
        configureDefaults()

        // Configure the tasks.
        project.logger.debug msg("Configuring code task")
        platform.configureCodeTask()
        if (platform.explicitScavengeTask()) {
            project.logger.debug msg("Configuring scavenge task")
            configureScavengeTask()
        }

        project.logger.debug msg("Configuring posting task")
        configurePostSnapshotTask()
        project.logger.debug msg("Configuring replay task")
        configureReplayPostTask()
        project.logger.debug msg("Configuring configuration-gathering task")
        platform.configureConfigurationsTask()
        project.logger.debug msg("Performing generic late configuration")
        platform.markMetadataToFix()
        project.logger.debug msg("Configuring repackaging task")
        configureRepackageTask(platform)
        project.logger.debug msg("Configuring repackage-test task")
        configureRepackageTestTask()
        project.logger.debug msg("Configuring bundling task (step 1)")
        configureCreateSnapshotTask_step1()

        // If some tasks are invoked together, configure which runs first.
        taskPrecedes(project, PTask.CREATE_SNAPSHOT, PTask.POST_SNAPSHOT)
        taskPrecedes(project, PTask.CREATE_SNAPSHOT, PTask.REPACKAGE)
    }

    /**
     * Helper method: if tasks a and b are invoked, then b should depend on a.
     *
     * @param project   the current project
     * @param a         the first task to be executed
     * @param b         the second task to be executed
     */
    protected static void taskPrecedes(Project project, PTask a, PTask b) {
        def tasks = project.gradle.startParameter.taskNames
        if (tasks.find { it.endsWith(a.name) } && tasks.find { it.endsWith(b.name) }) {
            project.tasks.findByName(b.name)
                .dependsOn(project.tasks.findByName(a.name))
        }
    }

    private void configureDefaults() {
        Extension ext = Extension.of(project)
        ext.projectName = platform.getProjectName()
        ext.scavengeOutputDir = new File(project.rootDir, Conventions.CLYZE_SNAPSHOT_DIR)
        ext.options = [ 'analysis': 'context-insensitive' ] as Map
    }

    private void configureScavengeTask() {
        JavaCompile task = project.tasks.create(PTask.SCAVENGE.name, JavaCompile)
        task.description = 'Scavenges the source files of the project for analysis'
        task.group = Conventions.TOOL_NAME

        // Copy the project's Java compilation settings.
        platform.copyCompilationSettings(task)

        // Our custom settings.
        String processorPath = platform.getClasspath()
        project.logger.info msg("Using processor path: ${processorPath}")

        File dest = Extension.of(project).getSnapshotDir(project)
        addPluginCommandArgs(task, dest, true)
        task.destinationDir = new File(dest as File, "classes")
        task.options.annotationProcessorPath = project.files(processorPath)
        // The compiler may fail when dependencies are missing, try to continue.
        task.options.failOnError = false

        platform.createScavengeDependency(task)
    }

    /**
     * Enable the javac metadata plugin.
     *
     * @param task    the compilation task to receive the plugin
     * @param dest    the directory that will receive the metadata
     * @param output  if true, the plugin will show output
     */
    static void addPluginCommandArgs(JavaCompile task, File dest, boolean output) {
        File jsonOutput = new File(dest as File, "json")
        task.options.compilerArgs += ['-Xplugin:TypeInfoPlugin ' + jsonOutput + " " + output]
    }

    private void configurePostSnapshotTask() {
        PostSnapshotTask task = project.tasks.create(PTask.POST_SNAPSHOT.name, PostSnapshotTask)
        task.description = 'Posts the current project as a snapshot'
        task.group = Conventions.TOOL_NAME
    }

    private void configureReplayPostTask() {
        ReplayPostTask task = project.tasks.create(PTask.REPLAY_POST.name, ReplayPostTask)
        task.description = 'Post analysis data generated by a previous run'
        task.group = Conventions.TOOL_NAME
    }

    private void configureRepackageTask(Platform platform) {
        RepackageTask repackage = project.tasks.create(PTask.REPACKAGE.name, RepackageTask)
        repackage.description = 'Repackage the Gradle build output using a given set of rules'
        repackage.group = Conventions.TOOL_NAME
    }

    private void dependOnCodeAndConfigurations(Platform platform, Task task) {
        dependOn(project, task, platform.codeTaskName(), 'core build task', true)
        dependOn(project, task, PTask.CONFIGURATIONS.name, 'configurations task', false)
    }

    private void configureRepackageTestTask() {
        TestRepackageTask repackage = project.tasks.create(PTask.REPACKAGE_TEST.name, TestRepackageTask)
        repackage.description = 'Repackage the Gradle build output using a given set of rules and test it'
        repackage.group = Conventions.TOOL_NAME
    }

    private void configureCreateSnapshotTask_step1() {
        CreateSnapshotTask task = project.tasks.create(PTask.CREATE_SNAPSHOT.name, CreateSnapshotTask)
        task.group = Conventions.TOOL_NAME
        dependOnCodeAndConfigurations(platform, task)
    }

    /**
     * Helper method to declare dependencies between tasks.
     *
     * @param project   the current project
     * @param t         the task that will depend on some other
     * @param tag       the name of the target task on which t depends
     * @param desc      a text description of the target task
     * @param fail      if true, desc is an error message, if false, a warning
     */
    static void dependOn(Project project, Task t, String tag,
                         String desc, boolean fail) {
        Task task0 = project.tasks.findByName(tag)
        if (task0)
	        t.dependsOn task0
        else if (fail)
            project.logger.error msg("ERROR: could not integrate with ${desc}.")
        else
            project.logger.warn msg("WARNING: could not integrate with ${desc}.")
    }

    /**
     * Returns the plugin version.
     *
     * @return a version identifier
     */
    static String getPluginVersion() {
        return JHelper.getVersionInfo(RepackagePlugin.class)
    }
}
