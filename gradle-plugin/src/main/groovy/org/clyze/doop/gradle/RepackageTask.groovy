package org.clyze.doop.gradle

import org.apache.http.HttpEntity
import org.clyze.client.web.Helper
import org.clyze.client.web.PostState
import org.clyze.client.web.api.AttachmentHandler
import org.clyze.client.web.api.Remote
import org.gradle.api.tasks.TaskAction

import static org.clyze.build.tools.Conventions.msg

class RepackageTask extends PostTask {

    @TaskAction
    void repackage() {
        Extension ext = Extension.of(project)
        if (ext.ruleFile == null) {
            project.logger.error msg("ERROR: no 'ruleFile' set in build.gradle, cannot repackage.")
            return
        }

        File ruleFile = new File(ext.ruleFile)
        if (!ruleFile.exists()) {
            project.logger.error msg("ERROR: rule file does not exist: ${ext.ruleFile}")
            return
        }

        PostState ps = new PostState()
        addBasicPostOptions(project, ps)
        ps.addFileInput("INPUTS", ext.platform.getOutputCodeArchive())
        ps.addFileInput("CLUE_FILE", ruleFile.canonicalPath)

        File out = File.createTempFile("repackaged-apk", ".apk")

        Remote api = Helper.connect(ext.host, ext.port, ext.username, ext.password)

        AttachmentHandler saveAttachment = new AttachmentHandler() {
            @Override
            String handleAttachment(HttpEntity entity) {
                out.withOutputStream { entity.writeTo(it) }
                return out.canonicalPath
            }
        }
        api.repackageBundleForCI(ext.username, ext.clueProject, ps, saveAttachment)

        println msg("Repackaged output: ${out.canonicalPath}")
    }
}
