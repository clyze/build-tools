package org.clyze.build.tools.gradle

import groovy.transform.TypeChecked
import org.clyze.build.tools.Conventions
import org.clyze.build.tools.Message
import org.clyze.build.tools.Poster
import org.clyze.client.web.PostState
import org.gradle.api.DefaultTask
import org.gradle.api.Project

import static org.clyze.build.tools.Conventions.msg

/**
 * This class represents tasks that post data to the server.
 */
@TypeChecked
abstract class PostTask extends DefaultTask {

    /**
     * Helper method to add a file input from the local "bundle" directory.
     *
     * @param project the current project
     * @param ps      the PostState to update
     * @param tag     the "tag" to use for the added item
     * @param fName   the file name
     */
    protected static void addFileInput(Project project, PostState ps, String tag, String fName) {
        Extension ext = Extension.of(project)
        try {
            File f = new File(ext.getBundleDir(project), fName)
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
     * Adds the basic options that are common in all bundles posted.
     *
     * @param project          the project containing the task
     * @param ps               the 'post state' object to fill in
     * @param shrinkResources  a flag to pass to the repackager to allow
     *                         shrinking of resources (if null, autodetect
     *                         from current project build file)
     */
    protected static void addBasicPostOptions(Project project, PostState ps,
                                              String shrinkResources) {
        ps.addStringInput('PLUGIN_VERSION', RepackagePlugin.pluginVersion ?: '')
        ps.addStringInput('API_VERSION', Conventions.API_VERSION)

        Extension ext = Extension.of(project)
        if (ext.platform instanceof AndroidPlatform) {
            ps.addStringInput('ANDROID_COMPILE_SDK_VERSION', AndroidAPI.getCompileSdkVersion(project))
            String buildType = ext.buildType
            ps.addStringInput('BUILD_TYPE', buildType)
            if (shrinkResources == null) {
                shrinkResources = AndroidAPI.getShrinkResources(project, buildType)
            }
            ps.addStringInput('SHRINK_RESOURCES', shrinkResources)
        }
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
        Poster.Options opts = new Poster.Options()
        opts.host = ext.host
        opts.port = ext.port
        opts.username = ext.username
        opts.password = ext.password
        opts.profile = ext.profile
        opts.project = ext.project
        opts.dry = ext.dry
        return new Poster(opts, ext.cachePost, ext.getBundleDir(project),
                          ext.androidProject, autoRepack)
    }

    /**
     * The actual method that posts a bundle and shows the generated messages.
     *
     * @param bundlePostState   the PostState object representing the bundle
     */
    protected void postBundlePostState(PostState bundlePostState) {
        Extension ext = Extension.of(project)
        if (bundlePostState) {
            List<Message> messages = new LinkedList<>()
            getPoster(project, false).post(bundlePostState, messages)
            messages.each { Platform.showMessage(project, it) }
        } else
            project.logger.error msg("ERROR: could not post bundle.")
        ext.platform.cleanUp()
    }

}
