package org.clyze.doop.gradle

import org.clyze.client.web.Helper
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile

/**
 * The doop gradle plugin.
 */
class DoopPlugin implements Plugin<Project> {

    static final String DOOP_GROUP        = "Doop"
    static final String TASK_SCAVENGE     = 'scavenge'
    static final String TASK_JCPLUGIN_ZIP = 'jcpluginZip'
    static final String TASK_SOURCES_JAR  = 'sourcesJar'
    static final String TASK_ANALYZE      = 'analyze'
    static final String TASK_REPLAY_POST  = 'replay'

    @Override
    void apply(Project project) {

        //require java 1.8 or higher
        if (!JavaVersion.current().isJava8Compatible()) {
            throw new RuntimeException("The Doop plugin requires Java 1.8 or higher")
        }

        Platform platform0
        //verify that the appropriate plugins have been applied
        if (project.plugins.hasPlugin('java')) {
            println "Project platform: Java"
            platform0 = new JavaPlatform()
        } else if (project.plugins.hasPlugin('android') || project.plugins.hasPlugin('com.android.application') || project.plugins.hasPlugin('com.android.library')) {
            println "Project platform: Android"
            platform0 = new AndroidPlatform(project.plugins.hasPlugin('com.android.library'))
        } else {
            throw new RuntimeException('One of these plugins should be applied before Doop: java, android, com.android.application, com.android.library')
        }

        //create the doop extension
        project.extensions.create('doop', DoopExtension)
        project.extensions.doop.platform = platform0

        //set the default values
        configureDefaults(project)

        //configure the tasks
        configureScavengeTask(project)
        configureJCPluginZipTask(project)
        configureSourceJarTask(project)
        platform(project).configureCodeJarTask(project)
        configureAnalyzeTask(project)
        configureReplayPostTask(project)

        //update the project's artifacts
        project.artifacts {
            archives project.tasks.findByName(TASK_SOURCES_JAR)
        }
    }

    Platform platform(Project project) {
        return project.extensions.doop.platform
    }

    private void configureDefaults(Project project) {
        DoopExtension doop = project.extensions.doop
        doop.orgName = project.group
        doop.projectName = platform(project).getProjectName(project)
        doop.projectVersion = project.version?.toString()
        doop.scavengeOutputDir = project.file("build/scavenge")        
        doop.options = ['analysis':'context-insensitive']        
    }

    private void configureScavengeTask(Project project) {
        JavaCompile task = project.tasks.create(TASK_SCAVENGE, JavaCompile)
        task.description = 'Scavenges the source files of the project for the Doop analysis'
        task.group = DOOP_GROUP

        // Copy the project's Java compilation settings.
        platform(project).copyCompilationSettings(project, task)

        // Our custom settings.
        File dest = project.extensions.doop.scavengeOutputDir
        String processorPath = platform(project).getClasspath(project)
        task.destinationDir = new File(dest as File, "classes")
        File jsonOutput = new File(dest as File, "json")
        task.options.compilerArgs = ['-processorpath', processorPath, '-Xplugin:TypeInfoPlugin ' + jsonOutput]
        platform(project).createScavengeDependency(project, task)
        platform(project).markMetadataToFix(project)

        task.doFirst {
            jsonOutput.mkdirs()
        }
    }

    private void configureJCPluginZipTask(Project project) {
        Zip task = project.tasks.create(TASK_JCPLUGIN_ZIP, Zip)
        task.description = 'Zips the output files of the scavenge task'
        task.group = DOOP_GROUP

        task.dependsOn project.tasks.findByName(TASK_SCAVENGE)

        task.archiveName = 'metadata.zip'
        task.destinationDir = project.extensions.doop.scavengeOutputDir
        File jsonOutput = new File(project.extensions.doop.scavengeOutputDir as File, "json")
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
            task = existing
        } else {
            throw new RuntimeException("Non-JAR task ${TASK_SOURCES_JAR} exists (of group ${existing.group}), cannot configure Doop.")
        }
        task.description = 'Generates the sources jar'
        task.group = DOOP_GROUP
        task.classifier = 'sources'

        platform(project).gatherSources(project, task)
    }

    private void configureAnalyzeTask(Project project) {
        AnalyzeTask task = project.tasks.create(TASK_ANALYZE, AnalyzeTask)
        task.description = 'Starts the Doop analysis of the project'
        task.group = DOOP_GROUP

        task.dependsOn project.getTasks().findByName(platform(project).jarTaskName()),
                       project.getTasks().findByName(TASK_SOURCES_JAR),
                       project.getTasks().findByName(TASK_JCPLUGIN_ZIP)
    }

    private void configureReplayPostTask(Project project) {
        ReplayPostTask task = project.tasks.create(TASK_REPLAY_POST, ReplayPostTask)
        task.description = 'Post analysis data generated by a previous run'
        task.group = DOOP_GROUP
    }
}
