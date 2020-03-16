package org.clyze.build.tools.gradle

import groovy.transform.TypeChecked
import org.apache.http.HttpEntity
import org.apache.http.client.ClientProtocolException
import org.apache.http.conn.HttpHostConnectException
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
        if (out)
            println msg("Repackaged output: ${out.canonicalPath}")
        else
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
            Remote api = Helper.connect(ext.host, ext.port, ext.username, ext.password)

            AttachmentHandler saveAttachment = new AttachmentHandler() {
                @Override
                String handleAttachment(HttpEntity entity) {
                    out.withOutputStream { entity.writeTo(it) }
                    return out.canonicalPath
                }
            }
            api.repackageBundleForCI(ext.username, ext.project, ps, saveAttachment)
            return out
        } catch (HttpHostConnectException ex) {
            project.logger.error msg( "ERROR: could not post bundle, is the server running?")
        } catch (ClientProtocolException ex) {
            project.logger.error msg("ERROR: ${ex.message}")
        }
        return null
    }
}
