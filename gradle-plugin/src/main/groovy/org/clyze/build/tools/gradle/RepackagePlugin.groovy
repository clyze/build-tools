package org.clyze.build.tools.gradle

import groovy.transform.TypeChecked
import org.clyze.build.tools.Conventions
import org.clyze.utils.VersionInfo
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

import static org.clyze.build.tools.Conventions.msg

/**
 * The entry point of the plugin.
 */
@TypeChecked
class RepackagePlugin implements Plugin<Project> {

    private Platform platform

    @Override
    void apply(Project project) {
        project.logger.info msg("Gradle plugin [${pluginVersion}]")

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
        configureDefaults(project)

        // Configure the tasks.
        project.logger.debug msg("Configuring code task")
        platform.configureCodeTask()
        if (platform.explicitScavengeTask()) {
            project.logger.debug msg("Configuring scavenge task")
            configureScavengeTask(project)
        }

        project.logger.debug msg("Configuring bundle posting task")
        configurePostBundleTask(project)
        project.logger.debug msg("Configuring replay task")
        configureReplayPostTask(project)
        project.logger.debug msg("Configuring configuration-gathering task")
        platform.configureConfigurationsTask()
        project.logger.debug msg("Performing generic late configuration")
        platform.markMetadataToFix()
        project.logger.debug msg("Configuring repackaging task")
        configureRepackageTask(project)
        project.logger.debug msg("Configuring repackage-test task")
        configureRepackageTestTask(project)
        project.logger.debug msg("Configuring bundling task (step 1)")
        configureCreateBundleTask_step1(project)
    }

    private void configureDefaults(Project project) {
        Extension ext = Extension.of(project)
        ext.orgName = project.group
        ext.projectName = platform.getProjectName()
        ext.projectVersion = project.version?.toString()
        ext.scavengeOutputDir = new File(project.rootDir, Conventions.CLUE_BUNDLE_DIR)
        ext.options = [ 'analysis': 'context-insensitive' ] as Map
    }

    private void configureScavengeTask(Project project) {
        JavaCompile task = project.tasks.create(PTask.SCAVENGE.name, JavaCompile)
        task.description = 'Scavenges the source files of the project for analysis'
        task.group = Conventions.TOOL_NAME

        // Copy the project's Java compilation settings.
        platform.copyCompilationSettings(task)

        // Our custom settings.
        String processorPath = platform.getClasspath()
        project.logger.info msg("Using processor path: ${processorPath}")

        File dest = Extension.of(project).getBundleDir(project)
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

    private static void configurePostBundleTask(Project project) {
        PostBundleTask task = project.tasks.create(PTask.POST_BUNDLE.name, PostBundleTask)
        task.description = 'Posts the current project as a bundle'
        task.group = Conventions.TOOL_NAME
    }

    private static void configureReplayPostTask(Project project) {
        ReplayPostTask task = project.tasks.create(PTask.REPLAY_POST.name, ReplayPostTask)
        task.description = 'Post analysis data generated by a previous run'
        task.group = Conventions.TOOL_NAME
    }

    private static void configureRepackageTask(Project project) {
        RepackageTask repackage = project.tasks.create(PTask.REPACKAGE.name, RepackageTask)
        repackage.description = 'Repackage the build output using a given set of rules'
        repackage.group = Conventions.TOOL_NAME
    }

    private static void configureRepackageTestTask(Project project) {
        TestRepackageTask repackage = project.tasks.create(PTask.REPACKAGE_TEST.name, TestRepackageTask)
        repackage.description = 'Repackage the build output using a given set of rules and test it'
        repackage.group = Conventions.TOOL_NAME
    }

    private void configureCreateBundleTask_step1(Project project) {
        CreateBundleTask task = project.tasks.create(PTask.CREATE_BUNDLE.name, CreateBundleTask)
        task.description = 'Creates a bundle from this project.'
        task.group = Conventions.TOOL_NAME
        dependOn(project, task, platform.codeTaskName(), 'core build task', true)
        dependOn(project, task, PTask.CONFIGURATIONS.name, 'configurations task', false)
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
        return VersionInfo.getVersionInfo(RepackagePlugin.class)
    }
}
