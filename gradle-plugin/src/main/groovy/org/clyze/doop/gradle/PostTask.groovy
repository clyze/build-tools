package org.clyze.doop.gradle

import groovy.transform.TypeChecked
import org.clyze.client.web.PostState
import org.gradle.api.DefaultTask
import org.gradle.api.Project

import static org.clyze.doop.gradle.RepackagePlugin.msg

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

    protected static void addBasicPostOptions(Project project, PostState ps) {
        addFileInput(project, ps, 'JCPLUGIN_METADATA', RepackagePlugin.METADATA_FILE)
        addFileInput(project, ps, 'PG_ZIP', RepackagePlugin.CONFIGURATIONS_FILE)

        Extension ext = Extension.of(project)
        if (ext.platform instanceof AndroidPlatform) {
            ps.addStringInput('ANDROID_COMPILE_SDK_VERSION', AndroidAPI.getCompileSdkVersion(project))
            String buildType = ext.buildType
            ps.addStringInput('BUILD_TYPE', buildType)
            ps.addStringInput('SHRINK_RESOURCES', AndroidAPI.getShrinkResources(project, buildType))
        }
    }
}
