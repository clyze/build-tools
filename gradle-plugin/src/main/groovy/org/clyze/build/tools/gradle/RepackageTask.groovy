package org.clyze.build.tools.gradle

import groovy.transform.TypeChecked
import java.nio.file.Files
import org.clyze.client.Message
import org.clyze.build.tools.Archiver
import org.clyze.build.tools.Poster
import org.clyze.client.web.Helper
import org.clyze.client.web.PostState
import org.clyze.client.web.api.Remote
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.zeroturnaround.zip.ZipUtil

import static org.clyze.build.tools.Conventions.msg

/**
 * This task repackages a program without UI intervention.
 */
@TypeChecked
class RepackageTask extends PostTask {

    @TaskAction
    void repackage() {
        Extension ext = Extension.of(project)
        File out = repackageCodeArchive(ext, ext.platform.getOutputCodeArchive(), "repackaged", ".zip", null)
        if (out) {
            if (ext.signingConfig) {
                project.logger.info msg("Signing using configuration '${ext.signingConfig}'")
                try {
                    if (ext.platform instanceof AndroidPlatform) {
                        File tmpDir = Files.createTempDirectory("repackaged-signing").toFile()
                        ZipUtil.unpack(out, tmpDir)
                        for (File f : tmpDir.listFiles()) {
                            if (f.name.endsWith('.apk')) {
                                project.logger.info msg("Signing: ${f.name}")
                                AndroidAPI.signWithConfig(project, ext.signingConfig, f)
                            }
                        }
                        Archiver.zipTree(tmpDir, out)
                    }
                    else
                        project.logger.warn msg("WARNING: signing not yet supported for JAR inputs.")
                } catch (Throwable t) {
                    project.logger.error msg("Signing failed: ${t.message}")
                    t.printStackTrace()
                }
            }
            println msg("Repackaged output: ${out.canonicalPath}")
        } else
            println msg("Could not repackage application.")
    }
}
