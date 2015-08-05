package doop.gradle

import doop.web.client.Helper
import doop.web.client.RestClient
import org.apache.commons.cli.Option
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

class DoopPlugin implements Plugin<Project> {
    static final String DOOP_GROUP = "Doop"

    @Override
    void apply(Project project) {

        //verify that the java plugin has been applied
        if (!project.plugins.hasPlugin('java')) {
            throw new RuntimeException('The java plugin should be applied before Doop')
        }

        project.extensions.create('doop', DoopExtension)

        configureCompileTask(project)
        configureSourceJarTask(project)
        configureAnalyseTask(project)

        //update the project's artifacts
        project.artifacts {
            archives project.tasks.findByName('sourcesJar')
        }

        configureDefaults(project)
    }

    private void configureDefaults(Project project) {
        project.extensions.doop.analysis.id = project.name
        project.extensions.doop.analysis.jar = project.tasks.findByName('jar').outputs.files.files +
                                               project.configurations.runtime.files

        project.extensions.doop.analysis.options = Helper.createDefaultOptions()
    }

    private void configureCompileTask(Project project) {
        JavaCompile task = project.tasks.create('compileJavaForDoop', JavaCompile)
        task.description = 'Compiles the project for Doop'
        task.group = DOOP_GROUP
    }

    private void configureSourceJarTask(Project project) {
        Jar task = project.tasks.create('sourcesJar', Jar)
        task.dependsOn project.tasks.findByName('classes')
        task.classifier = 'sources'
        task.from project.sourceSets.main.allSource
        task.description = 'Generates the sources jar'
        task.group = DOOP_GROUP
    }

    private void configureAnalyseTask(Project project) {
        DoopAnalyseTask task = project.tasks.create('analyse', DoopAnalyseTask)
        task.dependsOn project.tasks.findByName('jar')
        task.description = 'Starts the Doop analysis of the project'
        task.group = DOOP_GROUP
    }
}