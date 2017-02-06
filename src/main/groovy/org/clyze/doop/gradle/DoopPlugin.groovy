package org.clyze.doop.gradle

import org.clyze.doop.web.client.Helper
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile

/**
 * The doop gradle plugin.
 */
class DoopPlugin implements Plugin<Project> {

    static final String DOOP_GROUP = "Doop"
    static final String TASK_SCAVENGE = 'scavenge'
    static final String TASK_JCPLUGIN_ZIP = 'jcpluginZip'
    static final String TASK_SOURCES_JAR = 'sourcesJar'
    static final String TASK_ANALYSE = 'analyse'

    public enum GradlePlugin {
      Java,
      Android
    }

    public static GradlePlugin plugin
    
    @Override
    void apply(Project project) {

        //verify that the appropriate plugins have been applied
        if (project.plugins.hasPlugin('java')) {
          plugin = GradlePlugin.Java
        }
        else if (project.plugins.hasPlugin('android') || project.plugins.hasPlugin('com.android.application')) {
          plugin = GradlePlugin.Android
        }
        else {
          throw new RuntimeException('One of the java/android/com.android.application plugins should be applied before Doop')
        }

        //require java 1.8 or higher
        if (!JavaVersion.current().isJava8Compatible()) {
            throw new RuntimeException("The Doop plugin requires Java 1.8 or higher")
        }

        //create the doop extension
        project.extensions.create('doop', DoopExtension)

        //set the default values
        configureDefaults(project)

        //configure the tasks
        configureScavengeTask(project)
        configureJCPluginZipTask(project)
        configureSourceJarTask(project)
        configureAnalyseTask(project)

        //update the project's artifacts
        project.artifacts {
            archives project.tasks.findByName('sourcesJar')
        }
    }

    private void configureDefaults(Project project) {
        project.extensions.doop.projectName = project.name
        project.extensions.doop.projectVersion = project.version?.toString()
        project.extensions.doop.scavengeOutputDir = project.file("build/scavenge")
        project.extensions.doop.analysis.options = Helper.createDefaultOptions()
    }

    private void configureScavengeTask(Project project) {
        JavaCompile task = project.tasks.create(TASK_SCAVENGE, JavaCompile)
        task.description = 'Scavenges the source files of the project for the Doop analysis'
        task.group = DOOP_GROUP

        //copy the project's Java compilation settings
        switch (plugin) {
        case GradlePlugin.Java:
          JavaCompile projectDefaultTask = project.tasks.findByName("compileJava")
          task.classpath = projectDefaultTask.classpath
          task.source = projectDefaultTask.source
          break
        case GradlePlugin.Android:
          def tName = "assemble"
          for (def set1 : project.android.sourceSets) {
            if (set1.name == "main") {
              task.source = set1.java.sourceFiles
            }
          }
          if (task.source == null) {
            throw new RuntimeException("Could not find sourceSet for task " + tName + ".")
          }
          task.classpath = project.files()
          break
        }

        //our custom settings
        File dest = project.extensions.doop.scavengeOutputDir
        def buildScriptConf = project.getBuildscript().configurations.getByName(ScriptHandler.CLASSPATH_CONFIGURATION)
        //TODO: Filter-out not required jars
        String processorPath = buildScriptConf.collect().join(File.pathSeparator)
        task.destinationDir = new File(dest as File, "classes")
        File jsonOutput = new File(dest as File, "json")
        task.options.compilerArgs = ['-processorpath', processorPath, '-Xplugin:TypeInfoPlugin ' + jsonOutput]

        task.doFirst {
            jsonOutput.mkdirs()
        }
    }

    private void configureJCPluginZipTask(Project project) {
        Zip task = project.tasks.create(TASK_JCPLUGIN_ZIP, Zip)
        task.description = 'Zips the output files of the scavenge task'
        task.group = DOOP_GROUP

        task.dependsOn project.tasks.findByName(TASK_SCAVENGE)

        task.archiveName = 'jcplugin.zip'
        task.destinationDir = project.extensions.doop.scavengeOutputDir
        File jsonOutput = new File(project.extensions.doop.scavengeOutputDir as File, "json")
        task.from jsonOutput
    }

    private void configureSourceJarTask(Project project) {
        Jar task = project.tasks.create(TASK_SOURCES_JAR, Jar)
        task.description = 'Generates the sources jar'
        task.group = DOOP_GROUP

        switch (plugin) {
        case GradlePlugin.Java:
          task.dependsOn project.tasks.findByName('classes')
          break
        case GradlePlugin.Android:
          // This creates a circular dependency.
          // task.dependsOn project.getTasks().findByPath('assemble')
          break
        }
        task.classifier = 'sources'

        switch (plugin) {
        case GradlePlugin.Java:
          task.from project.sourceSets.main.allSource
          break
        case GradlePlugin.Android:
          task.from "src/main/java"
          break
        }
    }

    private void configureAnalyseTask(Project project) {
        AnalyseTask task = project.tasks.create(TASK_ANALYSE, AnalyseTask)
        task.description = 'Starts the Doop analysis of the project'
        task.group = DOOP_GROUP

        switch (plugin) {
        case GradlePlugin.Java:
          task.dependsOn project.tasks.findByName('jar'),
                         project.tasks.findByName(TASK_SOURCES_JAR),
                         project.tasks.findByName(TASK_JCPLUGIN_ZIP)
          break
        case GradlePlugin.Android:
          task.dependsOn project.getTasks().findByPath('assemble'),
                         project.getTasks().findByPath(TASK_SOURCES_JAR),
                         project.getTasks().findByPath(TASK_JCPLUGIN_ZIP)
          break
        }
    }
}
