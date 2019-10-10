package org.clyze.doop.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.bundling.Jar
import groovy.transform.CompileStatic

/**
 * The Java platform used: plain Java or Android. Each platform is
 * handled by a different gradle plugin ('java' vs. 'android'), so
 * different tasks exist and the Doop plugin must examine different
 * metadata on each platform.
 */

@CompileStatic
abstract class Platform {

    protected Project project
    protected DoopExtension doopExt = null

    Platform(Project project) {
        this.project = project
    }

    protected DoopExtension getDoop() {
        if (!doopExt) {
            doopExt = DoopExtension.of(project)
        }
        return doopExt
    }

    abstract void copyCompilationSettings(JavaCompile task)
    abstract void markMetadataToFix()
    abstract void createScavengeDependency(JavaCompile scavengeTask)
    abstract void gatherSources(Jar sourcesJarTask)
    abstract void configureCodeJarTask()
    abstract String jarTaskName()
    abstract List<String> inputFiles()
    abstract List<String> libraryFiles()
    abstract String getClasspath()
    abstract String getProjectName()
    abstract boolean mustRunAgain()
    abstract void cleanUp()
    // True if the metadata processor runs in a separate Gradle task,
    // false if the processor is integrated in an existing task.
    abstract boolean explicitScavengeTask()
}
