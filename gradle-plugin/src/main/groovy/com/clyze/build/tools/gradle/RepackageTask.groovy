package com.clyze.build.tools.gradle

import groovy.transform.CompileStatic
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import com.clyze.build.tools.Archiver
import org.gradle.api.tasks.TaskAction
import org.zeroturnaround.zip.ZipUtil

import static com.clyze.build.tools.Conventions.msg

/**
 * This task repackages a program without UI intervention.
 */
@CompileStatic
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
                            if (AUtils.isAppCodeArtifact(f.name)) {
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
            if (ext.repackageOutput) {
                File userOut = project.file(ext.repackageOutput)
                Files.move(out.toPath(), userOut.toPath(), StandardCopyOption.REPLACE_EXISTING)
                out = userOut
            }
            println msg("Repackaged output: ${out.canonicalPath}")
        } else
            println msg("Could not repackage application.")
    }
}
