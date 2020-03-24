package org.clyze.build.tools.gradle

import groovy.transform.TypeChecked
import org.apache.http.HttpEntity
import org.apache.http.client.ClientProtocolException
import org.apache.http.conn.HttpHostConnectException
import org.clyze.build.tools.Poster
import org.clyze.client.web.Helper
import org.clyze.client.web.PostState
import org.clyze.client.web.api.AttachmentHandler
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
        File out = repackageCodeArchive(project, ext, ext.platform.getOutputCodeArchive(), "repackaged-apk", ".apk", null)
        if (out) {
            println msg("Repackaged output: ${out.canonicalPath}")
            if (ext.signingConfig) {
                project.logger.info msg("Signing using configuration '${ext.signingConfig}'")
                try {
                    AndroidAPI.sign(project, ext.signingConfig, out)
                } catch (Throwable t) {
                    project.logger.error msg("Signing failed: ${t.message}")
                    t.printStackTrace()
                }
            }
        } else
            println msg("Could not repackage application.")
    }

    static File repackageCodeArchive(Project project, Extension ext, String codeArchive,
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
        addBasicPostOptions(project, ps, shrinkResources)
        ps.addFileInput("INPUTS", codeArchive)
        ps.addFileInput("CLUE_FILE", ruleFile.canonicalPath)

        File out = File.createTempFile(repackBaseName, repackExtension)

        try {
            Poster poster = getPoster(project)
            Map<String, Object> diag = poster.diagnose()
            if (ext.androidProject && !Poster.isAndroidSupported(diag)) {
                println msg("ERROR: Cannot repackage bundle: Android SDK setup missing.")
                return
            }

            Boolean supportsRepackaging = (Boolean)diag.get("AUTOMATED_REPACKAGING")
            if (!supportsRepackaging) {
                println msg("This version of the server does not support automated repackaging.")
                return
            }

            AttachmentHandler<String> saveAttachment = new AttachmentHandler() {
                @Override
                String handleAttachment(HttpEntity entity) {
                    out.withOutputStream { entity.writeTo(it) }
                    return out.canonicalPath
                }
            }
            poster.repackageBundleForCI(ps, saveAttachment)
            return out
        } catch (HttpHostConnectException ex) {
            project.logger.error msg( "ERROR: cannot repackage bundle, is the server running?")
        } catch (ClientProtocolException ex) {
            project.logger.error msg("ERROR: ${ex.message}")
        }
        return null
    }
}
