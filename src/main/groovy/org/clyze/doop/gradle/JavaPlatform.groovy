package org.clyze.doop.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.clyze.client.SourceProcessor

class JavaPlatform implements Platform {

    void copyCompilationSettings(Project project, Task task) {
        JavaCompile projectDefaultTask = project.tasks.findByName("compileJava")
        task.classpath = projectDefaultTask.classpath
        task.source = projectDefaultTask.source
    }

    /** Things to do last are:
     *
     * - Optional UTF-8 conversion ('convertUTF8Dir' Doop parameter).
     *
     * - Feed extra inputs to the scavenge task ('extraInputs' Doop
     *   parameter).
     */
    void markMetadataToFix(Project project, JavaCompile scavengeTask) {
        project.afterEvaluate {
            DoopExtension doop = project.extensions.doop

            String convPath = doop.convertUTF8Dir
            if (convPath != null) {
                println "Converting to UTF-8 in ${convPath}..."
                SourceProcessor sp = new SourceProcessor()
                sp.process(new File(convPath), true)
            }

            List<File> extras = doop.getExtraInputFiles(project.rootDir)
            if (extras != null && extras.size() > 0) {
                String extraCp = extras.collect { it.getAbsolutePath() }.join(File.pathSeparator)
                scavengeTask.options.compilerArgs << "-cp"
                scavengeTask.options.compilerArgs << extraCp
            }
        }
    }

    void createScavengeDependency(Project project, JavaCompile scavengeTask) {}

    void createSourcesJarDependency(Project project, Jar sourcesJarTask) {
        sourcesJarTask.dependsOn project.tasks.findByName('classes')
    }

    void gatherSources(Project project, Jar sourcesJarTask) {
        sourcesJarTask.from project.sourceSets.main.allSource
    }

    // No code JAR task is created, the 'java' gradle plugin already
    // provides 'jar'.
    void configureCodeJarTask(Project project) {}

    String jarTaskName() { return 'jar'; }

    List inputFiles(Project project) {
        def jar = project.file(project.tasks.findByName(jarTaskName()).archivePath)
        List<File> extraInputFiles = project.extensions.doop.getExtraInputFiles(project.rootDir)
        return [jar] + project.configurations.runtime.files + extraInputFiles
    }

    String getClasspath(Project project) {
        def buildScriptConf = project.getBuildscript().configurations.getByName(ScriptHandler.CLASSPATH_CONFIGURATION)
        //TODO: Filter-out not required jars
        return buildScriptConf.collect().join(File.pathSeparator)
    }

    String getProjectName(Project project) {
	return project.name
    }

    public boolean mustRunAgain() {
        return false
    }
}
