package org.clyze.doop.gradle

import groovy.transform.TypeChecked
import org.apache.http.HttpEntity
import org.clyze.client.web.Helper
import org.clyze.client.web.PostState
import org.clyze.client.web.api.AttachmentHandler
import org.clyze.client.web.api.Remote
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

import static org.clyze.build.tools.Conventions.msg

@TypeChecked
class RepackageTask extends PostTask {

    @TaskAction
    void repackage() {
        Extension ext = Extension.of(project)
        File out = repackageCodeArchive(project, ext, ext.platform.getOutputCodeArchive(), "repackaged-apk", ".apk", null)
        println msg("Repackaged output: ${out.canonicalPath}")
    }

    static File repackageCodeArchive(Project project, Extension ext, String codeArchive,
                                     String repackBaseName, String repackExtension,
                                     String shrinkResources) {
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
        addBasicPostOptions(project, ps, shrinkResources)
        ps.addFileInput("INPUTS", codeArchive)
        ps.addFileInput("CLUE_FILE", ruleFile.canonicalPath)

        File out = File.createTempFile(repackBaseName, repackExtension)

        Remote api = Helper.connect(ext.host, ext.port, ext.username, ext.password)

        AttachmentHandler saveAttachment = new AttachmentHandler() {
            @Override
            String handleAttachment(HttpEntity entity) {
                out.withOutputStream { entity.writeTo(it) }
                return out.canonicalPath
            }
        }
        api.repackageBundleForCI(ext.username, ext.clueProject, ps, saveAttachment)
        return out
    }
}
