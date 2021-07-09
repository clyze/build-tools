package com.clyze.build.tools.gradle

import groovy.transform.CompileStatic
import com.clyze.client.web.PostOptions
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

import static com.clyze.build.tools.Conventions.msg

/**
 * The data structure maintained by the plugin.
 */
@CompileStatic
class Extension {
    static final String SECTION_NAME = 'clyze'

    /** The server host name. */
    String host
    /** The server port. */
    int port
    /** The username to use for authentication. */
    String username
    /** The password to use for authentication. */
    String password
    /** The project name where the snapshot will be posted. */
    String projectName
    /**
     * If true, the javac plugin is used to process sources
     * and post source metadata. Disabled by default.
     */
    boolean useJavacPlugin = false
    File scavengeOutputDir
    /** The configuration files to post. */
    List<String> configurationFiles
    /** The HPROF files to post. */
    List<String> hprofs
    /** Extra inputs, as a list of paths relative to the project root
     *  directory. Can be used to add dependency JARs whose resolutions
     * has failed or extra code. */
    List<String> extraInputs
    Map<String, Object> options
    /** Directory to receive the serialized snapshot before posting to the server. */
    String cachePostDir = null
    String convertUTF8Dir
    /** Dry mode, for sample snapshot creation. */
    boolean dry = false
    /** If true, skip posting of configurations. */
    boolean ignoreConfigurations = false
    /** The project stacks to use (if creating a new project). */
    List<String> stacks
    /** The rule file to use for automated repackaging. */
    String ruleFile
    /** Subproject name (Android-only). */
    String subprojectName
    /** Flavor name (Android-only). */
    String flavor
    /** Build type (Android-only). */
    String buildType
    /** Output .apk filter substring (Android-only). */
    String apkFilter
    /** List of group-name pairs of artifacts that are resolved by
     *  extraInputs (and thus the resolver should ignore them). */
    List<List<String>> replacedByExtraInputs
    /** If true, the snapshot uses the generated .aab output, otherwise
     *  the snapshot uses the .apk output. */
    boolean aab = false
    /**
     * If true, the collected configurations are gathered via a
     * '-printconfiguration' directive.
     */
    boolean printConfig = false
    /** Flag to enable gathering sources and their metadata. */
    boolean sources = true
    /** Override sources archive with user-provided sources. */
    String useSourcesJar
    /** Show output when the javac plugin runs. */
    boolean jcPluginOutput = false
    /** Signing configuration for automated repackaging. */
    String signingConfig = null
    /** Filename of automated repackaging output. */
    String repackageOutput = null
    /** If true, print extra debug information. */
    boolean debug = false
    /** The path to the CodeQL database directory. */
    String codeqlDatabase = null

    Platform platform

    def options(Closure cl) {
        ConfigureUtil.configure(cl, options)
    }

    List<String> getExtraInputFiles(File rootDir) {
        if (extraInputs == null) {
            return []
        } else {
            return extraInputs.collect { String fName ->
                File f = new File("${rootDir.canonicalPath}/${fName}")
                if (!f.exists()) {
                    println msg("Extra input ${f.canonicalPath} does not exist.")
                } else {
                    println msg("Using extra input ${f.canonicalPath}")
                }
                f.canonicalPath
            }
        }
    }

    /**
     * Returns the extension defined in a project's build.gradle script.
     * If no extension block was defined, throws a runtime exception.
     *
     * @param project   the build project
     * @return          the extension object
     */
    static Extension of(Project project) {
        Object sec = project.extensions.getByName(SECTION_NAME)
        if (sec == null) {
            throw new RuntimeException(msg("Missing section \"${SECTION_NAME}\" in build.gradle."))
        }
        return (sec instanceof Extension) ? sec : null
    }

    /**
     * Returns the snapshot directory (creating it if it does not already
     * exist). Use this method before writing to this directory.
     *
     * @param project   the current project
     * @return          a File object representing the directory
     */
    static File getSnapshotDir(Project project) {
        // Create output directory.
        File scavengeDir = of(project).scavengeOutputDir
        if (!scavengeDir.exists()) {
            scavengeDir.mkdirs()
            project.logger.debug msg("Creating directory: ${scavengeDir}")
        }
        return scavengeDir
    }

    /**
     * Check if this is an Android project.
     *
     * @return true if this an Android project, false otherwise
     */
    boolean isAndroidProject() {
        return (platform instanceof AndroidPlatform)
    }

    /**
     * Creates a PostOptions object from the setup in this extension.
     *
     * @param autoRepack   true if automated repackaging is going to be used
     * @return             the options object for lower-level use
     */
    PostOptions createPostOptions(boolean autoRepack) {
        PostOptions opts = new PostOptions()
        opts.host = this.host
        opts.port = this.port
        opts.username = this.username
        opts.password = this.password
        opts.stacks = this.stacks
        opts.project = this.projectName
        opts.dry = this.dry
        opts.android = this.androidProject
        opts.autoRepackaging = autoRepack
        return opts
    }
}
