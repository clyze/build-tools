package com.clyze.build.tools.gradle

import groovy.io.FileType
import groovy.transform.CompileStatic
import com.clyze.build.tools.Conventions
import com.clyze.client.web.Helper
import com.clyze.client.web.PostState
import org.apache.http.client.ClientProtocolException
import org.gradle.api.tasks.TaskAction

import static com.clyze.build.tools.Conventions.msg

/**
 * The task that posts a build to the server.
 */
@CompileStatic
class PostBuildTask extends PostTask {

    /**
     * The main task action.
     */
    @TaskAction
    void postBuild() {
        try {
            postBuildPostState(newBuildPostState())
        } catch (ClientProtocolException ex) {
            project.logger.error msg("ERROR: " + ex.message)
        }
    }

    /**
     * Generates a PostState representation of the current build (e.g., for
     * preserving all the information required to replay a build post).
     * @return the current build as a PostState object
     */
    private final PostState newBuildPostState() {

        /*         
        These are the options for builds:
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
        PostState ps = new PostState(id:Conventions.BUILD_ID, stacks:ext.stacks)
        addBasicPostOptions(ext, ps, null)

        boolean submitInputs = false
        ext.getBuildDir(project).eachFile(FileType.FILES) { File f ->
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
            project.logger.error msg("ERROR: No code inputs submitted, aborting task '${PTask.POST_BUILD.name}'.")
            return null
        }

        // Filter out empty libraries.
        def projectLibs = p.libraryFiles
        if (projectLibs) {
            projectLibs.findAll(Helper.checkFileEmpty).each {
                ps.addFileInput("LIBRARIES", it)
                project.logger.info msg("Added library: ${it}")
            }
        }

        // The platform to use when analyzing the code.
        ps.addStringInput("PLATFORM", ext.platform instanceof AndroidPlatform ? Conventions.getR8AndroidPlatform("25") : "java_8")

        project.logger.info msg("PostState object: ${ps.toJSON()}")

        return ps
    }
}
