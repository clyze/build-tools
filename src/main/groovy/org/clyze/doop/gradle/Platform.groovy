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

    private static final String DEFAULT_HOST    = 'localhost'
    private static final String DEFAULT_PROFILE = 'apiTargetAndroid'
    private static final String DEFAULT_PROJECT = 'scrap'
    private static final String DEFAULT_RULES   = 'optimize.clue'
    protected static final String MISSING_PROPERTIES = "WARNING: Bad 'doop' section found in build.gradle, skipping configuration."

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
            doop.username = 'user'
        }
        if (doop.password == null) {
            doop.password = 'user123'
        }
        if (doop.clueProject == null) {
            project.logger.warn "WARNING: missing property 'clueProject', assuming host=${DEFAULT_PROJECT}"
            doop.clueProject = DEFAULT_PROJECT
        }
        if (doop.profile == null) {
            project.logger.debug "Missing property 'profile', assuming profile=${DEFAULT_PROFILE}"
            doop.profile = DEFAULT_PROFILE
        }
        if (doop.ruleFile == null) {
            project.logger.debug "Missing property 'ruleFile', assuming ruleFile=${DEFAULT_RULES}"
            doop.ruleFile = DEFAULT_RULES
        }
        return true
    }

    abstract void copyCompilationSettings(JavaCompile task)
    abstract void markMetadataToFix()
    abstract void createScavengeDependency(JavaCompile scavengeTask)
    abstract void gatherSources(Jar sourcesJarTask)
    abstract void configureCodeJarTask()
    abstract String jarTaskName()
    abstract List<String> getInputFiles()
    abstract List<String> getLibraryFiles()
    abstract String getClasspath()
    abstract String getProjectName()
    abstract boolean mustRunAgain()
    abstract void cleanUp()
    abstract void configureConfigurationsTask()
    abstract String getOutputCodeArchive()
    // True if the metadata processor runs in a separate Gradle task,
    // false if the processor is integrated in an existing task.
    abstract boolean explicitScavengeTask()
}
