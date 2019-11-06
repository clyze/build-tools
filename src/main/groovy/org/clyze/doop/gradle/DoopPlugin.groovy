package org.clyze.doop.gradle

import groovy.transform.TypeChecked
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile

/**
 * The doop gradle plugin.
 */
@TypeChecked
class DoopPlugin implements Plugin<Project> {

    static final String LOCAL_BUNDLE_DIR    = '.clue-bundle'
    static final String METADATA_FILE       = 'metadata.zip'
    static final String CONFIGURATIONS_FILE = 'configurations.zip'
    static final String SOURCES_FILE        = 'sources.jar'

    static final String DOOP_GROUP          = 'Doop'
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

        //require java 1.8 or higher
        if (!JavaVersion.current().isJava8Compatible()) {
            throw new RuntimeException("The Doop plugin requires Java 1.8 or higher")
        }

        //verify that the appropriate plugins have been applied
        if (project.plugins.hasPlugin('java')) {
            println "Project platform: Java"
            platform = new JavaPlatform(project)
        } else if (project.plugins.hasPlugin('android') || project.plugins.hasPlugin('com.android.application') || project.plugins.hasPlugin('com.android.library')) {
            println "Project platform: Android"
            platform = new AndroidPlatform(project)
        } else {
            throw new RuntimeException('One of these plugins should be applied before Doop: java, android, com.android.application, com.android.library')
        }

        //create the doop extension
        project.extensions.create('doop', DoopExtension)
        DoopExtension.of(project).platform = platform

        //set the default values
        configureDefaults(project)

        //configure the tasks
        project.logger.debug "[DOOP] Configuring code archive task"
        platform.configureCodeJarTask()
        if (platform.explicitScavengeTask()) {
            project.logger.debug "[DOOP] Configuring scavenge task"
            configureScavengeTask(project)
        }
        project.logger.debug "[DOOP] Configuring jcplugin task"
        configureJCPluginZipTask(project)
        project.logger.debug "[DOOP] Configuring sources task"
        configureSourceJarTask(project)
        project.logger.debug "[DOOP] Configuring bundling task"
        configurePostBundleTask(project)
        project.logger.debug "[DOOP] Configuring replay task"
        configureReplayPostTask(project)
        project.logger.debug "[DOOP] Configuring configuration-gathering task"
        platform.configureConfigurationsTask()
        project.logger.debug "[DOOP] Performing late configuration"
        platform.markMetadataToFix()
        project.logger.debug "[DOOP] Configuring repackaging task"
        configureRepackageTask(project)
    }

    private void configureDefaults(Project project) {
        DoopExtension doop = DoopExtension.of(project)
        doop.orgName = project.group
        doop.projectName = platform.getProjectName()
        doop.projectVersion = project.version?.toString()
        doop.scavengeOutputDir = project.file(LOCAL_BUNDLE_DIR)
        doop.options = [ 'analysis': 'context-insensitive' ] as Map
    }

    private void configureScavengeTask(Project project) {
        JavaCompile task = project.tasks.create(TASK_SCAVENGE, JavaCompile)
        task.description = 'Scavenges the source files of the project for the Doop analysis'
        task.group = DOOP_GROUP

        // Copy the project's Java compilation settings.
        platform.copyCompilationSettings(task)

        // Our custom settings.
        String processorPath = platform.getClasspath()
        println "Using processor path: ${processorPath}"

        File dest = DoopExtension.of(project).scavengeOutputDir
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
        task.group = DOOP_GROUP

        // If a separate metadata generation task exists, depend on it;
        // otherwise depend on build task (which integrates metadata generation).
        if (platform.explicitScavengeTask()) {
	        task.dependsOn project.tasks.findByName(TASK_SCAVENGE)
        } else {
	        task.dependsOn project.tasks.findByName(platform.jarTaskName())
        }

        task.archiveFileName.set(METADATA_FILE)
        File scavengeDir = DoopExtension.of(project).scavengeOutputDir
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
            println "Reusing existing task ${TASK_SOURCES_JAR}"
            task = existing as Jar
        } else {
            throw new RuntimeException("Non-JAR task ${TASK_SOURCES_JAR} exists (of group ${existing.group}), cannot configure Doop.")
        }
        task.archiveFileName.set(SOURCES_FILE)
        task.destinationDirectory.set(project.file(LOCAL_BUNDLE_DIR))
        task.description = 'Generates the sources JAR'
        task.group = DOOP_GROUP
        task.archiveClassifier.set('sources')

        platform.gatherSources(task)
    }

    private void configurePostBundleTask(Project project) {
        PostBundleTask task = project.tasks.create(TASK_POST_BUNDLE, PostBundleTask)
        task.description = 'Posts the current project as a bundle'
        task.group = DOOP_GROUP
    }

    private static void configureReplayPostTask(Project project) {
        ReplayPostTask task = project.tasks.create(TASK_REPLAY_POST, ReplayPostTask)
        task.description = 'Post analysis data generated by a previous run'
        task.group = DOOP_GROUP
    }

    private void configureRepackageTask(Project project) {
        RepackageTask repackage = project.tasks.create(TASK_REPACKAGE, RepackageTask)
        repackage.description = 'Repackage the build output using a given set of rules'
        repackage.group = DOOP_GROUP
    }
}
