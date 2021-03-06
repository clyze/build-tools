package com.clyze.build.tools.gradle

import com.clyze.build.tools.Archiver
import com.clyze.client.Printer
import groovy.io.FileType
import groovy.transform.CompileStatic
import org.apache.http.HttpEntity
import org.apache.http.client.ClientProtocolException
import org.apache.http.conn.HttpHostConnectException
import com.clyze.build.tools.Conventions
import com.clyze.build.tools.Poster
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
     * Helper method to add a file input from the local "snapshot" directory.
     *
     * @param project the current project
     * @param ps      the PostState to update
     * @param tag     the "tag" to use for the added item
     * @param fName   the file name
     */
    protected static void addFileInput(Project project, PostState ps, String tag, String fName) {
        Extension ext = Extension.of(project)
        try {
            File f = new File(ext.getSnapshotDir(project), fName)
            if (f.exists()) {
                ps.addFileInput(tag, f.canonicalPath)
                project.logger.info msg("Added local cached ${tag} item: ${f}")
            } else
                project.logger.warn msg("WARNING: could not find ${tag} item: ${f}")
        } catch (Throwable t) {
            project.logger.warn msg("WARNING: could not upload ${tag} item: ${fName} (reason: ${t.message})")
        }
    }

    /**
     * Adds the basic options that are common in all snapshots posted.
     *
     * @param ext              the plugin extension data structure
     * @param ps               the 'post state' object to fill in
     * @param shrinkResources  a flag to pass to the repackager to allow
     *                         shrinking of resources (if null, autodetect
     *                         from current project Gradle build file)
     */
    protected void addBasicPostOptions(Extension ext, PostState ps,
                                       String shrinkResources) {
        ps.addStringInput('PLUGIN_VERSION', RepackagePlugin.pluginVersion ?: '')
        ps.addStringInput('API_VERSION', Conventions.API_VERSION)
        ps.stacks = ext.stacks

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
     * @param ps    the snapshot representation
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
                ext.getSnapshotDir(project).eachFile(FileType.FILES) { File f ->
                    String n = f.name
                    if (n.endsWith(Conventions.SOURCES_FILE)) {
                        addFileInput(project, ps, 'SOURCES_JAR', n)
                    }
                }
            }
            // Upload source metadata.
            addFileInput(project, ps, 'JCPLUGIN_METADATA', Conventions.METADATA_FILE)
        }

        if (ext.codeqlDatabase) {
            File codeqlDB_dir = new File(ext.codeqlDatabase)
            if (codeqlDB_dir.exists()) {
                project.logger.info msg("Using CodeQL database in: ${ext.codeqlDatabase}")
                Archiver.zipTree(codeqlDB_dir, new File(ext.scavengeOutputDir, Conventions.CODEQL_DB_FILE))
                addFileInput(project, ps, 'CODEQL_DB', Conventions.CODEQL_DB_FILE)
            } else
                project.logger.error msg("ERROR: CodeQL database not found: ${ext.codeqlDatabase}")
        }
    }

    /**
     * Add options needed for deep analysis.
     *
     * @param ext   the plugin extension data structure
     * @param ps    the snapshot representation
     */
    protected static void addDeepOptions(Extension ext, PostState ps) {
        // The heap snapshots are optional.
        ext.hprofs?.collect { ps.addFileInput("HEAPDLS", it) }
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
        return new Poster(opts, cachePostDir, ext.getSnapshotDir(project))
    }

    /**
     * The actual method that posts a snapshot and shows the generated messages.
     *
     * @param snapshotPostState   the PostState object representing the build
     */
    protected void postSnapshotPostState(PostState snapshotPostState) {
        Extension ext = Extension.of(project)
        if (snapshotPostState) {
            getPoster(project, false).post(snapshotPostState, ext.platform.printer, ext.debug)
        } else
            project.logger.error msg("ERROR: could not post snapshot.")
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
        ps.addFileInput(Conventions.BINARY_INPUT_TAG, codeArchive)
        ps.addFileInput(Conventions.CLYZE_RULES_TAG, ruleFile.canonicalPath)
        ps.addStringInput(Conventions.JVM_PLATFORM, ext.platform.defaultAutomatedRepackagingProfile)

        File out = File.createTempFile(repackBaseName, repackExtension)

        try {
            Poster poster = getPoster(project, true)
            Printer printer = ext.platform.printer
            if (!poster.isServerCapable(printer)) {
                return null
            }

            AttachmentHandler<String> saveAttachment = new AttachmentHandler<String>() {
                @Override
                String handleAttachment(HttpEntity entity) {
                    out.withOutputStream { entity.writeTo(it) }
                    return out.canonicalPath
                }
            }
            poster.repackageSnapshotForCI(ps, saveAttachment, printer)
            return out
        } catch (HttpHostConnectException ignored) {
            project.logger.error msg("ERROR: cannot repackage build, is the server running?")
        } catch (ClientProtocolException ex) {
            project.logger.error msg("ERROR: ${ex.message}")
        }
        return null
    }
}
