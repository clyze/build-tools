package org.clyze.doop.gradle

import groovy.transform.TypeChecked
import org.clyze.build.tools.Conventions
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile

import static org.clyze.build.tools.Conventions.msg

/**
 * The Gradle plugin that integrates builds with the server.
 */
@TypeChecked
class RepackagePlugin implements Plugin<Project> {
    static final String TASK_SCAVENGE       = 'scavenge'
    static final String TASK_JCPLUGIN_ZIP   = 'jcpluginZip'
    static final String TASK_SOURCES_JAR    = 'sourcesJar'
    static final String TASK_POST_BUNDLE    = 'postBundle'
    static final String TASK_REPLAY_POST    = 'replay'
    // The task that gathers all optimization directive configurations.
    static final String TASK_CONFIGURATIONS = 'configurations'
    static final String TASK_REPACKAGE      = 'repackage'

    private Platform platform

    @Override
    void apply(Project project) {

        // Require Java 1.8 or higher.
        if (!JavaVersion.current().isJava8Compatible()) {
            throw new RuntimeException(msg("The plugin requires Java 1.8 or higher"))
        }

        //verify that the appropriate plugins have been applied
        if (project.plugins.hasPlugin('java')) {
            println msg("Project platform: Java")
            platform = new JavaPlatform(project)
        } else if (project.plugins.hasPlugin('android') || project.plugins.hasPlugin('com.android.application') || project.plugins.hasPlugin('com.android.library')) {
            println msg("Project platform: Android")
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
        project.logger.debug msg("Configuring code archive task")
        platform.configureCodeJarTask()
        if (platform.explicitScavengeTask()) {
            project.logger.debug msg("Configuring scavenge task")
            configureScavengeTask(project)
        }
        project.logger.debug msg("Configuring jcplugin task")
        configureJCPluginZipTask(project)
        project.logger.debug msg("Configuring sources task")
        configureSourceJarTask(project)
        project.logger.debug msg("Configuring bundling task")
        configurePostBundleTask(project)
        project.logger.debug msg("Configuring replay task")
        configureReplayPostTask(project)
        project.logger.debug msg("Configuring configuration-gathering task")
        platform.configureConfigurationsTask()
        project.logger.debug msg("Performing late configuration")
        platform.markMetadataToFix()
        project.logger.debug msg("Configuring repackaging task")
        configureRepackageTask(project)
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
        JavaCompile task = project.tasks.create(TASK_SCAVENGE, JavaCompile)
        task.description = 'Scavenges the source files of the project for analysis'
        task.group = Conventions.TOOL_NAME

        // Copy the project's Java compilation settings.
        platform.copyCompilationSettings(task)

        // Our custom settings.
        String processorPath = platform.getClasspath()
        println msg("Using processor path: ${processorPath}")

        File dest = Extension.of(project).scavengeOutputDir
        addPluginCommandArgs(task, dest)
        task.destinationDir = new File(dest as File, "classes")
        task.options.annotationProcessorPath = project.files(processorPath)
        // The compiler may fail when dependencies are missing, try to continue.
        task.options.failOnError = false

        platform.createScavengeDependency(task)
    }

    static void addPluginCommandArgs(JavaCompile task, File dest) {
        File jsonOutput = new File(dest as File, "json")
        task.options.compilerArgs += ['-Xplugin:TypeInfoPlugin ' + jsonOutput]
    }

    private void configureJCPluginZipTask(Project project) {
        Zip task = project.tasks.create(TASK_JCPLUGIN_ZIP, Zip)
        task.description = 'Zips the output files of the metadata processor'
        task.group = Conventions.TOOL_NAME

        // If a separate metadata generation task exists, depend on it;
        // otherwise depend on build task (which integrates metadata generation).
        if (platform.explicitScavengeTask()) {
	        task.dependsOn project.tasks.findByName(TASK_SCAVENGE)
        } else {
	        task.dependsOn project.tasks.findByName(platform.jarTaskName())
        }

        task.archiveFileName.set(Conventions.METADATA_FILE)
        File scavengeDir = Extension.of(project).scavengeOutputDir
        if (!scavengeDir.exists()) {
            scavengeDir.mkdirs()
        }
        task.destinationDirectory.set(scavengeDir)
        File jsonOutput = new File(scavengeDir, "json")
        task.from jsonOutput
    }

    private synchronized void configureSourceJarTask(Project project) {
        def existing = project.tasks.findByName(TASK_SOURCES_JAR)
        Jar task
        if (existing == null) {
            task = project.tasks.create(TASK_SOURCES_JAR, Jar)
        } else if (existing instanceof Jar) {
            // Heuristic to handle repeated configuration by Gradle.
            println msg("Reusing existing task ${TASK_SOURCES_JAR}")
            task = existing as Jar
        } else {
            throw new RuntimeException(msg("Non-JAR task ${TASK_SOURCES_JAR} exists (of group ${existing.group}), cannot configure ${Conventions.TOOL_NAME} plugin."))
        }

        String prefix = project.name ? "${project.name}-": ""
        String sourcesName = prefix + Conventions.SOURCES_FILE
        project.logger.info msg("Sources archive: ${sourcesName}")
        task.archiveFileName.set(sourcesName)

        task.destinationDirectory.set(Extension.of(project).scavengeOutputDir)
        task.description = 'Generates the sources JAR'
        task.group = Conventions.TOOL_NAME
        task.archiveClassifier.set('sources')

        platform.gatherSources(task)
    }

    private static void configurePostBundleTask(Project project) {
        PostBundleTask task = project.tasks.create(TASK_POST_BUNDLE, PostBundleTask)
        task.description = 'Posts the current project as a bundle'
        task.group = Conventions.TOOL_NAME
    }

    private static void configureReplayPostTask(Project project) {
        ReplayPostTask task = project.tasks.create(TASK_REPLAY_POST, ReplayPostTask)
        task.description = 'Post analysis data generated by a previous run'
        task.group = Conventions.TOOL_NAME
    }

    private static void configureRepackageTask(Project project) {
        RepackageTask repackage = project.tasks.create(TASK_REPACKAGE, RepackageTask)
        repackage.description = 'Repackage the build output using a given set of rules'
        repackage.group = Conventions.TOOL_NAME
    }
}
