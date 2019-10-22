package org.clyze.doop.gradle

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

/**
 * The Java platform used: plain Java or Android. Each platform is
 * handled by a different gradle plugin ('java' vs. 'android'), so
 * different tasks exist and the Doop plugin must examine different
 * metadata on each platform.
 */

@CompileStatic
abstract class Platform {

    private static final String DEFAULT_HOST = "localhost"

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

    // Check if the build.gradle section defines the needed properties.
    boolean definesRequiredProperties() {
        DoopExtension doop = getDoop()
        // We don't check for 'options', as that is never empty (but
        // initialized to defaults).
        def err = { project.logger.error "ERROR: missing property: '${it}'" }
        if (doop.host == null) {
            project.logger.warn "WARNING: missing property 'host', assuming host=${DEFAULT_HOST}"
            doop.host = DEFAULT_HOST
        }
        if (doop.port == 0) {
            err 'port'
            return false
        }
        if (doop.username == null) {
            err 'username'
            return false
        }
        if (doop.password == null) {
            err 'password'
            return false
        }
        return true
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
