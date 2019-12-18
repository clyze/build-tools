package org.clyze.doop.gradle

import groovy.transform.TypeChecked
import org.clyze.build.tools.Conventions
import org.clyze.client.web.PostState
import org.gradle.api.DefaultTask
import org.gradle.api.Project

import static org.clyze.build.tools.Conventions.msg

/**
 * This class represents tasks that post data to the server.
 */
@TypeChecked
abstract class PostTask extends DefaultTask {

    protected static void addFileInput(Project project, PostState ps, String tag, String fName) {
        Extension ext = Extension.of(project)
        try {
            File f = new File(ext.scavengeOutputDir, fName)
            if (f.exists()) {
                ps.addFileInput(tag, f.canonicalPath)
                project.logger.info msg("Added local cached ${tag} item: ${f}")
            } else {
                project.logger.warn msg("WARNING: could not find ${tag} item: ${f}")
            }
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
        addFileInput(project, ps, 'JCPLUGIN_METADATA', Conventions.METADATA_FILE)
        addFileInput(project, ps, 'PG_ZIP', Conventions.CONFIGURATIONS_FILE)

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
}
