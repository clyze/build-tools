package org.clyze.build.tools.gradle

import groovy.io.FileType
import groovy.transform.TypeChecked
import java.nio.file.Files
import org.clyze.build.tools.Conventions
import org.clyze.build.tools.Message
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

        Extension ext = Extension.of(project)
        
        // Package all information needed to post the bundle and the analysis.
        PostState bundlePostState = newBundlePostState(project)

        if (bundlePostState) {
            List<Message> messages = new LinkedList<>()
            getPoster(project, false).post(bundlePostState, messages)
            messages.each { Platform.showMessage(project, it) }
        } else
            project.logger.error msg("ERROR: could not package bundle.")

        ext.platform.cleanUp()
    }

    // A PostState for preserving all the information required to replay a bundle post
    private static final PostState newBundlePostState(Project project) {

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
        PostState ps = new PostState(id:Conventions.BUNDLE_ID)
        addBasicPostOptions(project, ps, null)

        // The aplication regex.
        addStringInputFromExtensionOption(ps, ext, "APP_REGEX", "app_regex")

        // The heap snapshots are optional.
        ext.hprofs?.collect {
            ps.addFileInput("HEAPDLS", it)
        }

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

        // The main class of the program. Usually empty on Android code.
        addStringInputFromExtensionOption(ps, ext, "MAIN_CLASS", "main_class")

        // The platform to use when analyzing the code.
        ps.addStringInput("PLATFORM", ext.platform instanceof AndroidPlatform ? Conventions.getR8AndroidPlatform("25") : "java_8")

        // Add the configurations archive.
        addFileInput(project, ps, 'PG_ZIP', Conventions.CONFIGURATIONS_FILE)

        if (ext.sources) {
            // Upload sources (user can override with alternative sources archive).
            String altSourcesJar = ext.useSourcesJar
            if (altSourcesJar) {
                File sources = new File(altSourcesJar)
                if (!sources.exists()) {
                    project.logger.warn msg("WARNING: explicit sources JAR ${altSourcesJar} does not exist, no sources will be uploaded.")
                } else {
                    ps.addFileInput("SOURCES_JAR", sources.canonicalPath)
                }
            } else {
                ext.getBundleDir(project).eachFile(FileType.FILES) { File f ->
                    String n = f.name
                    if (n.endsWith(Conventions.SOURCES_FILE)) {
                        addFileInput(project, ps, 'SOURCES_JAR', n)
                    }
                }
            }
            // Upload source metadata.
            addFileInput(project, ps, 'JCPLUGIN_METADATA', Conventions.METADATA_FILE)
        }

        //tamiflex
        addFileInputFromExtensionOption(ps, ext, "TAMIFLEX", "tamiflex")

        project.logger.info msg("PostState object: ${ps.toJSON()}")

        return ps
    }

    private static void addStringInputFromExtensionOption(PostState ps, Extension ext, String inputId, String optionId) {
        if (ext.options.containsKey(optionId)) {
            ps.addStringInput(inputId, ext.options[(optionId)] as String)
        }
    }

    private static void addFileInputFromExtensionOption(PostState ps, Extension ext, String inputId, String optionId) {
        if (ext.options.containsKey(optionId)) {
            ps.addFileInput(inputId, ext.options[(optionId)] as String)
        }
    }

}
