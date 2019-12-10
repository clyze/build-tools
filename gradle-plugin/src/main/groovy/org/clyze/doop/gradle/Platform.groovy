package org.clyze.doop.gradle

import groovy.transform.CompileStatic
import org.clyze.build.tools.Conventions
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

import static org.clyze.build.tools.Conventions.msg

/**
 * The Java platform used: plain Java or Android. Each platform is
 * handled by a different gradle plugin ('java' vs. 'android'), so
 * different tasks exist and the Doop plugin must examine different
 * metadata on each platform.
 */

@CompileStatic
abstract class Platform {

    private static final String DEFAULT_RULES = 'optimize.clue'
    protected static final String MISSING_PROPERTIES = msg("WARNING: Cannot configure ${Conventions.TOOL_NAME} plugin with defaults.")

    protected Project project
    private Extension repackageExt = null

    Platform(Project project) {
        this.project = project
    }

    protected Extension getRepackageExt() {
        if (!repackageExt) {
            repackageExt = Extension.of(project)
        }
        return repackageExt
    }

    /**
     * Check if the build.gradle section defines the needed properties.
     *
     * @return true if build.gradle defines the section
     */
    boolean definesRequiredProperties() {
        Extension ext = getRepackageExt()
        // We don't check for 'options', as that is never empty (but
        // initialized to defaults).
        def err = { project.logger.error msg("ERROR: missing property: '${it}'") }
        if (ext.host == null) {
            project.logger.warn msg("WARNING: missing property 'host', assuming host=${Conventions.DEFAULT_HOST}")
            ext.host = Conventions.DEFAULT_HOST
        }
        if (ext.port == 0) {
            String port = GradleProps.get(project, 'clue_port')
            if (port) {
                ext.port = port as Integer
                project.logger.warn msg("WARNING: missing property 'port', using Gradle property 'clue_port'= ${ext.port}")
            } else {
                err 'port'
                return false
            }
        }
        if (ext.username == null) {
            ext.username = Conventions.DEFAULT_USERNAME
        }
        if (ext.password == null) {
            ext.password = Conventions.DEFAULT_PASSWORD
        }
        if (ext.clueProject == null) {
            project.logger.warn msg("WARNING: missing property 'clueProject', assuming host=${Conventions.DEFAULT_PROJECT}")
            ext.clueProject = Conventions.DEFAULT_PROJECT
        }
        if (ext.profile == null) {
            project.logger.debug msg("Missing property 'profile', assuming profile=${Conventions.DEFAULT_PROFILE}")
            ext.profile = Conventions.DEFAULT_PROFILE
        }
        if (ext.ruleFile == null) {
            project.logger.debug msg("Missing property 'ruleFile', assuming ruleFile=${DEFAULT_RULES}")
            ext.ruleFile = DEFAULT_RULES
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
    abstract boolean isCodeArtifact(String filename)

    /**
     * Helper test method to control the creation of the "scavenge" task.
     *
     * @return true if the metadata processor runs in a separate Gradle task,
     *         false if the processor is integrated in an existing task.
     */
    abstract boolean explicitScavengeTask()

}
