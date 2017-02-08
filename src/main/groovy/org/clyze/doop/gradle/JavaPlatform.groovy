package org.clyze.doop.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile

public class JavaPlatform implements Platform {

    public void copyCompilationSettings(Project project, Task task) {
        JavaCompile projectDefaultTask = project.tasks.findByName("compileJava")
        task.classpath = projectDefaultTask.classpath
        task.source = projectDefaultTask.source
    }

    // No metadata is read.
    public void markMetadataToFix(Project project, Task task) { }

    public void createScavengeDependency(Project project, Task task) { }
    public void createSourcesJarDependency(Project project, Task task) {
        task.dependsOn project.tasks.findByName('classes')
    }

    public void gatherSources(Project project, Task task) {
        task.from project.sourceSets.main.allSource
    }

    // No code JAR task is created, the 'java' gradle plugin already
    // provides 'jar'.
    public void configureCodeJarTask(Project project) { }

    public String jarTaskName() { return 'jar'; }

    public Set inputFiles(Project project, File jarArchive) {
        return ([jarArchive] + project.configurations.runtime.files) as Set;
    }
}
