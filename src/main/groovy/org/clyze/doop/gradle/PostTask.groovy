package org.clyze.doop.gradle

import groovy.transform.TypeChecked
import org.clyze.client.web.PostState
import org.gradle.api.DefaultTask
import org.gradle.api.Project

/**
 * This class represents tasks that post data to the server.
 */
@TypeChecked
abstract class PostTask extends DefaultTask {

    protected void addFileInput(Project project, PostState ps, String tag, String fName) {
        DoopExtension doop = DoopExtension.of(project)
        try {
            File f = new File(doop.scavengeOutputDir, fName)
            if (f.exists()) {
                ps.addFileInput(tag, f.canonicalPath)
                project.logger.info "Added local cached ${tag} item: ${f}"
            } else {
                project.logger.warn "WARNING: could not find ${tag} item: ${f}"
            }
        } catch (Throwable t) {
            project.logger.warn "WARNING: could not upload ${tag} item: ${fName}"
        }
    }

    protected void addSourcesAndMetadata(Project project, PostState ps) {
        addFileInput(project, ps, 'JCPLUGIN_METADATA', DoopPlugin.METADATA_FILE)
        addFileInput(project, ps, 'PG_ZIP', DoopPlugin.CONFIGURATIONS_FILE)
    }

    protected void addCompileSdkVersion(Project project, PostState ps) {
        if (DoopExtension.of(project).platform instanceof AndroidPlatform) {
            ps.addStringInput('ANDROID_COMPILE_SDK_VERSION', AndroidAPI.getCompileSdkVersion(project))
        }
    }
}
