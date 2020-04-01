package org.clyze.build.tools.gradle

import groovy.io.FileType
import groovy.transform.TypeChecked
import java.nio.file.Files
import org.clyze.build.tools.Conventions
import org.clyze.build.tools.Poster
import org.clyze.client.web.Helper
import org.clyze.client.web.PostState
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

import static org.clyze.build.tools.Conventions.msg

/**
 * The task that posts a bundle to the server.
 */
@TypeChecked
class PostBundleTask extends PostTask {

    /**
     * The main task action.
     */
    @TaskAction
    void postBundle() {
        postBundlePostState(newBundlePostState())
    }

    /**
     * Generates a PostState representation of the current bundle (e.g., for
     * preserving all the information required to replay a bundle post).
     * @return the current bundle as a PostState object
     */
    private final PostState newBundlePostState() {

        /*         
        These are the options for bundles: 
        --app_regex <arg>        
        --heapdls <files>
        --inputs <files>
        --jcplugin_metadata <file>
        --libraries <files>
        --main_class <arg>
        --platform <arg>
        --project_name <arg>
        --sources_jar <file>
        --tamiflex <file>
        */
        Extension ext = Extension.of(project)
        Platform p = ext.platform
        PostState ps = new PostState(id:Conventions.BUNDLE_ID, profile:ext.profile)
        addBasicPostOptions(ext, ps, null)

        boolean submitInputs = false
        ext.getBundleDir(project).eachFile(FileType.FILES) { File f ->
            String n = f.name
            if (p.isCodeArtifact(n) && !n.endsWith(Conventions.SOURCES_FILE)) {
                addFileInput(project, ps, 'INPUTS', n)
                submitInputs = true
            }
        }

        // Filter out empty inputs.
        p.inputFiles.findAll(Helper.checkFileEmpty).each {
            ps.addFileInput("INPUTS", it)
            project.logger.info msg("Added input: ${it}")
            submitInputs = true
        }

        if (!submitInputs) {
            project.logger.error msg("ERROR: No code inputs submitted, aborting task '${PTask.POST_BUNDLE.name}'.")
            return null
        }

        // Filter out empty libraries.
        p.libraryFiles.findAll(Helper.checkFileEmpty).each {
            ps.addFileInput("LIBRARIES", it)
            project.logger.info msg("Added library: ${it}")
        }

        // The platform to use when analyzing the code.
        ps.addStringInput("PLATFORM", ext.platform instanceof AndroidPlatform ? Conventions.getR8AndroidPlatform("25") : "java_8")

        project.logger.info msg("PostState object: ${ps.toJSON()}")

        return ps
    }
}
