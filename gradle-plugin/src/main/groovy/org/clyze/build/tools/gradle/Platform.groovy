package org.clyze.build.tools.gradle

import groovy.transform.CompileStatic
import org.clyze.build.tools.Conventions
import org.clyze.build.tools.Settings
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

    /** The name of the default rules file (in our format). */
    private static final String DEFAULT_RULES = 'optimize.clue'
    /**
     * Message to display when configuration block is missing
     * and initialization cannot happen.
     */
    protected static final String MISSING_PROPERTIES = msg("WARNING: Cannot configure ${Conventions.TOOL_NAME} plugin with defaults.")
    /** The current project. */
    protected Project project
    /** Field to hold the configuration data structure. */
    private Extension repackageExt = null

    /**
     * Default constructor.
     *
     * @param project the project being built
     */
    Platform(Project project) {
        this.project = project
    }

    /**
     * Reads the plugin configuration data structure ('extension').
     *
     * @return the plugin configuration object
     */
    protected synchronized Extension getRepackageExt() {
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
            String port = Settings.getDefaultPort()
            if (port) {
                ext.port = port as Integer
                project.logger.warn msg("Using configured port ${ext.port}")
            } else {
                port = GradleProps.get(project, 'clue_port')
                if (port) {
                    ext.port = port as Integer
                    project.logger.warn msg("WARNING: missing property 'port', using Gradle property 'clue_port'= ${ext.port}")
                } else {
                    err 'port'
                    return false
                }
            }
        }
        if (ext.username == null) {
            ext.username = Conventions.DEFAULT_USERNAME
        }
        if (ext.password == null) {
            ext.password = Conventions.DEFAULT_PASSWORD
        }
        if (ext.project == null) {
            project.logger.warn msg("WARNING: missing property 'project', assuming host=${Conventions.DEFAULT_PROJECT}")
            ext.project = Conventions.DEFAULT_PROJECT
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

    /**
     * Takes the compilation settings from an already configured
     * build task.
     *
     * @param task the build task to read
     */
    abstract void copyCompilationSettings(JavaCompile task)
    /**
     * Registers logic that should run after basic initialization. Used
     * for example on Android builds, where basic build tasks creation
     * is delayed and thus our plugin cannot discover these tasks early.
     */
    abstract void markMetadataToFix()
    /** Creates a dependency for the "scavenge" task. */
    abstract void createScavengeDependency(JavaCompile scavengeTask)
    /**
     * Gathers the sources from an already configured task.
     *
     * @param task the task to read
     */
    abstract void gatherSources(Jar sourcesJarTask)
    /** Configures the task that will build the code to post to the server. */
    abstract void configureCodeTask()
    /** Returns the name of the task that constructs the code output. */
    abstract String codeTaskName()
    /**
     * Returns the code files that will be given as "input" to the server.
     * @return a list of file paths
     */
    abstract List<String> getInputFiles()
    /**
     * Returns the code files that will be given as "libraries" to the server.
     * @return a list of file paths
     */
    abstract List<String> getLibraryFiles()
    /** Return a build classpath. */
    abstract String getClasspath()
    /** Return the project name. */
    abstract String getProjectName()
    /** If true, the Gradle plugin must run again. */
    abstract boolean mustRunAgain()
    /** Clean up resources on plugin exit. */
    abstract void cleanUp()
    /**
     * Configure the task that gathers configuration files (containing
     * keep rules and directives).
     */
    abstract void configureConfigurationsTask()
    /** Return the output code archive (JAR, APK, AAR). */
    abstract String getOutputCodeArchive()
    /** Checks if a filename is a code artifact. */
    abstract boolean isCodeArtifact(String filename)

    /**
     * Helper test method to control the creation of the "scavenge" task.
     *
     * @return true if the metadata processor runs in a separate Gradle task,
     *         false if the processor is integrated in an existing task.
     */
    abstract boolean explicitScavengeTask()

}
