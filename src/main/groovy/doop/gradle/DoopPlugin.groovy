package doop.gradle

import doop.web.client.Helper
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

    @Override
    void apply(Project project) {

        //verify that the java plugin has been applied
        if (!project.plugins.hasPlugin('java')) {
            throw new RuntimeException('The java plugin should be applied before Doop')
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

        project.extensions.doop.analysis.jar = project.tasks.findByName('jar').outputs.files.files +
                                               project.configurations.runtime.files

        project.extensions.doop.analysis.options = Helper.createDefaultOptions()
    }

    private void configureScavengeTask(Project project) {
        JavaCompile task = project.tasks.create(TASK_SCAVENGE, JavaCompile)
        task.description = 'Scavenges the source files of the project for the Doop analysis'
        task.group = DOOP_GROUP

        //copy the project's Java compilation settings
        JavaCompile projectDefaultTask = project.tasks.findByName("compileJava")
        task.classpath = projectDefaultTask.classpath
        task.source = projectDefaultTask.source

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

        task.dependsOn project.tasks.findByName('classes')
        task.classifier = 'sources'
        task.from project.sourceSets.main.allSource
    }

    private void configureAnalyseTask(Project project) {
        AnalyseTask task = project.tasks.create(TASK_ANALYSE, AnalyseTask)
        task.description = 'Starts the Doop analysis of the project'
        task.group = DOOP_GROUP

        task.dependsOn project.tasks.findByName('jar'),
                       project.tasks.findByName(TASK_SOURCES_JAR),
                       project.tasks.findByName(TASK_JCPLUGIN_ZIP)
    }
}