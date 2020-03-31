package org.clyze.build.tools.gradle

import groovy.transform.TypeChecked
import org.clyze.build.tools.Message
import org.clyze.build.tools.Poster
import org.clyze.client.web.Helper
import org.clyze.client.web.PostState
import org.clyze.client.web.api.Remote
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

import static org.clyze.build.tools.Conventions.msg

/**
 * This task repackages a program without UI intervention.
 */
@TypeChecked
class RepackageTask extends PostTask {

    @TaskAction
    void repackage() {
        Extension ext = Extension.of(project)
        File out = repackageCodeArchive(ext, ext.platform.getOutputCodeArchive(), "repackaged-apk", ".apk", null)
        if (out) {
            println msg("Repackaged output: ${out.canonicalPath}")
            if (ext.signingConfig) {
                project.logger.info msg("Signing using configuration '${ext.signingConfig}'")
                try {
                    if (ext.platform instanceof AndroidPlatform)
                        AndroidAPI.signWithConfig(project, ext.signingConfig, out)
                    else
                        project.logger.warn msg("WARNING: signing not yet supported for JAR inputs.")
                } catch (Throwable t) {
                    project.logger.error msg("Signing failed: ${t.message}")
                    t.printStackTrace()
                }
            }
        } else
            println msg("Could not repackage application.")
    }
}
