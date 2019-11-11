package org.clyze.doop.gradle

import org.apache.http.HttpEntity
import org.clyze.client.web.Helper
import org.clyze.client.web.PostState
import org.clyze.client.web.api.AttachmentHandler
import org.clyze.client.web.api.Remote
import org.gradle.api.tasks.TaskAction

class RepackageTask extends PostTask {

    @TaskAction
    void repackage() {
        DoopExtension doop = DoopExtension.of(project)
        if (doop.ruleFile == null) {
            project.logger.error "ERROR: no 'ruleFile' set in build.gradle, cannot repackage."
            return
        }

        File ruleFile = new File(doop.ruleFile)
        if (!ruleFile.exists()) {
            project.logger.error "ERROR: rule file does not exist: ${doop.ruleFile}"
            return
        }

        PostState ps = new PostState()
        ps.addFileInput("INPUTS", doop.platform.getOutputCodeArchive())
        ps.addFileInput("CLUE_FILE", ruleFile.canonicalPath)
        addSourcesAndMetadata(project, ps)

        File out = File.createTempFile("repackaged-apk", ".apk")

        Remote api = Helper.connect(doop.host, doop.port, doop.username, doop.password)

        AttachmentHandler saveAttachment = new AttachmentHandler() {
            @Override
            String handleAttachment(HttpEntity entity) {
                out.withOutputStream { entity.writeTo(it) }
                return out.canonicalPath
            }
        }
        api.repackageBundleForCI(doop.username, doop.clueProject, ps, saveAttachment)

        println "Repackaged output: ${out.canonicalPath}"
    }
}
