package org.clyze.doop.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

class JavaPlatform implements Platform {

    void copyCompilationSettings(Project project, Task task) {
        JavaCompile projectDefaultTask = project.tasks.findByName("compileJava")
        task.classpath = projectDefaultTask.classpath
        task.source = projectDefaultTask.source
    }

    // No metadata is read.
    void markMetadataToFix(Project project, JavaCompile scavengeTask) {}

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
        return [jar] + project.configurations.runtime.files
    }

    List<File> getExtraInputs(Project project) {
        if (project.extensions.doop.extraInputs)
            throw new RuntimeException("Option 'extraInputs' is not implemented for plain Java yet.")
    }

    public Set<File> getDependencies() {
        println "Warning: dependency posting not yet implemented for plain Java."
        return new HashSet()
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
