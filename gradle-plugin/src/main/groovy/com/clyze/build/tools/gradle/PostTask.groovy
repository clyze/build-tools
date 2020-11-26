package com.clyze.build.tools.gradle

import groovy.io.FileType
import groovy.transform.CompileStatic
import org.apache.http.HttpEntity
import org.apache.http.client.ClientProtocolException
import org.apache.http.conn.HttpHostConnectException
import com.clyze.build.tools.Conventions
import com.clyze.build.tools.Poster
import com.clyze.client.Message
import com.clyze.client.web.PostOptions
import com.clyze.client.web.PostState
import com.clyze.client.web.api.AttachmentHandler
import org.gradle.api.DefaultTask
import org.gradle.api.Project

import static com.clyze.build.tools.Conventions.msg

/**
 * This class represents tasks that post data to the server.
 */
@CompileStatic
abstract class PostTask extends DefaultTask {

    /**
     * Helper method to add a file input from the local "build" directory.
     *
     * @param project the current project
     * @param ps      the PostState to update
     * @param tag     the "tag" to use for the added item
     * @param fName   the file name
     */
    protected static void addFileInput(Project project, PostState ps, String tag, String fName) {
        Extension ext = Extension.of(project)
        try {
            File f = new File(ext.getBuildDir(project), fName)
            if (f.exists()) {
                ps.addFileInput(tag, f.canonicalPath)
                project.logger.info msg("Added local cached ${tag} item: ${f}")
            } else
                project.logger.warn msg("WARNING: could not find ${tag} item: ${f}")
        } catch (Throwable t) {
            project.logger.warn msg("WARNING: could not upload ${tag} item: ${fName} (reason: ${t.message})")
        }
    }

    protected static void addFileInputFromExtensionOption(PostState ps, Extension ext, String inputId, String optionId) {
        if (ext.options.containsKey(optionId)) {
            ps.addFileInput(inputId, ext.options[(optionId)] as String)
        }
    }

    protected static void addStringInputFromExtensionOption(PostState ps, Extension ext, String inputId, String optionId) {
        if (ext.options.containsKey(optionId)) {
            ps.addStringInput(inputId, ext.options[(optionId)] as String)
        }
    }

    /**
     * Adds the basic options that are common in all builds posted.
     *
     * @param ext              the plugin extension data structure
     * @param ps               the 'post state' object to fill in
     * @param shrinkResources  a flag to pass to the repackager to allow
     *                         shrinking of resources (if null, autodetect
     *                         from current project build file)
     */
    protected void addBasicPostOptions(Extension ext, PostState ps,
                                       String shrinkResources) {
        ps.addStringInput('PLUGIN_VERSION', RepackagePlugin.pluginVersion ?: '')
        ps.addStringInput('API_VERSION', Conventions.API_VERSION)

        if (ext.platform instanceof AndroidPlatform) {
            ps.addStringInput('ANDROID_COMPILE_SDK_VERSION', AndroidAPI.getCompileSdkVersion(project))
            String buildType = ext.buildType
            ps.addStringInput('BUILD_TYPE', buildType)
            if (shrinkResources == null) {
                shrinkResources = AndroidAPI.getShrinkResources(project, buildType)
            }
            ps.addStringInput('SHRINK_RESOURCES', shrinkResources)
        }

        addSourcesDataAndConfigurations(ext, ps)
        addDeepOptions(ext, ps)
    }

    /**
     * Adds the source (sources, metadata) and configurations inputs.
     *
     * @param ext   the plugin extension data structure
     * @param ps    the build representation
     */
    protected void addSourcesDataAndConfigurations(Extension ext, PostState ps) {
        // Add the configurations archive.
        addFileInput(project, ps, 'PG_ZIP', Conventions.CONFIGURATIONS_FILE)

        if (ext.sources) {
            // Upload sources (user can override with alternative sources archive).
            String altSourcesJar = ext.useSourcesJar
            if (altSourcesJar) {
                File sources = new File(altSourcesJar)
                if (!sources.exists()) {
                    project.logger.warn msg("WARNING: explicit sources JAR ${altSourcesJar} does not exist, no sources will be uploaded.")
                } else {
                    ps.addFileInput("SOURCES_JAR", sources.canonicalPath)
                }
            } else {
                ext.getBuildDir(project).eachFile(FileType.FILES) { File f ->
                    String n = f.name
                    if (n.endsWith(Conventions.SOURCES_FILE)) {
                        addFileInput(project, ps, 'SOURCES_JAR', n)
                    }
                }
            }
            // Upload source metadata.
            addFileInput(project, ps, 'JCPLUGIN_METADATA', Conventions.METADATA_FILE)
        }
    }

    /**
     * Add options needed for deep analysis.
     *
     * @param ext   the plugin extension data structure
     * @param ps    the build representation
     */
    protected static void addDeepOptions(Extension ext, PostState ps) {
        // The heap snapshots are optional.
        ext.hprofs?.collect { ps.addFileInput("HEAPDLS", it) }
        // The main class of the program. Usually empty on Android code.
        addStringInputFromExtensionOption(ps, ext, "MAIN_CLASS", "main_class")
        // Tamiflex file.
        addFileInputFromExtensionOption(ps, ext, "TAMIFLEX", "tamiflex")
        // The aplication regex.
        addStringInputFromExtensionOption(ps, ext, "APP_REGEX", "app_regex")
    }

    /**
     * Constructs the "Poster" object that will handle interactions with the server.
     *
     * @param project     the current project
     * @param autoRepack  if the Poster should support automated repackaging
     * @return            the Poster object
     */
    protected static Poster getPoster(Project project, boolean autoRepack) {
        Extension ext = Extension.of(project)
        PostOptions opts = ext.createPostOptions(autoRepack)
        File cachePostDir = ext.cachePostDir ? new File(ext.cachePostDir): null
        // Handle relative paths (so that they don't go in random locations
        // (such as ~/.gradle/daemon).
        if (cachePostDir && cachePostDir.canonicalPath != ext.cachePostDir)
            cachePostDir = project.rootProject.file(cachePostDir) as File
        return new Poster(opts, cachePostDir, ext.getBuildDir(project))
    }

    /**
     * The actual method that posts a build and shows the generated messages.
     *
     * @param buildPostState   the PostState object representing the build
     */
    protected void postBuildPostState(PostState buildPostState) {
        Extension ext = Extension.of(project)
        if (buildPostState) {
            List<Message> messages = [] as List<Message>
            getPoster(project, false).post(buildPostState, messages, ext.debug)
            messages.each { Platform.showMessage(project, it) }
        } else
            project.logger.error msg("ERROR: could not post build.")
        ext.platform.cleanUp()
    }

    /**
     * Repackages a code archive (.jar, .apk, or .aab).
     *
     * @param ext              the plugin extension data structure
     * @param codeArchive      the path of the code archive to repackage
     * @param repackBaseName   the base name of the output file
     * @param repackExtension  the extension of the output file
     * @param shrinkResources  "true"/"false"/null, see "addBasicOptions()"
     */
    protected File repackageCodeArchive(Extension ext, String codeArchive,
                                        String repackBaseName, String repackExtension,
                                        String shrinkResources) {
        if (ext.ruleFile == null) {
            project.logger.error msg("ERROR: no 'ruleFile' set in build.gradle, cannot repackage.")
            return null
        }

        File ruleFile = new File(ext.ruleFile)
        if (!ruleFile.exists()) {
            project.logger.error msg("ERROR: rule file does not exist: ${ext.ruleFile}")
            return null
        }

        PostState ps = new PostState()
        addBasicPostOptions(ext, ps, shrinkResources)
        ps.addFileInput("INPUTS", codeArchive)
        ps.addFileInput("CLUE_FILE", ruleFile.canonicalPath)
        ps.addStringInput("PLATFORM", ext.platform.defaultAutomatedRepackagingProfile)

        File out = File.createTempFile(repackBaseName, repackExtension)

        try {
            Poster poster = getPoster(project, true)
            List<Message> messages = new LinkedList<>()
            if (!poster.isServerCapable(messages)) {
                messages.each { Platform.showMessage(project, it) }
                return
            }

            AttachmentHandler<String> saveAttachment = new AttachmentHandler() {
                @Override
                String handleAttachment(HttpEntity entity) {
                    out.withOutputStream { entity.writeTo(it) }
                    return out.canonicalPath
                }
            }
            poster.repackageBuildForCI(ps, saveAttachment)
            return out
        } catch (HttpHostConnectException ignored) {
            project.logger.error msg("ERROR: cannot repackage build, is the server running?")
        } catch (ClientProtocolException ex) {
            project.logger.error msg("ERROR: ${ex.message}")
        }
        return null
    }
}
