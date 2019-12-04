package org.clyze.doop.gradle

import groovy.io.FileType
import java.nio.file.Files
import org.clyze.client.web.Helper
import org.clyze.client.web.PostState
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class PostBundleTask extends PostTask {

    @TaskAction
    void postBundle() {

        Extension ext = Extension.of(project)
        Platform p = ext.platform
        if (p.mustRunAgain()) {
            project.logger.error msg("ERROR: this looks like a first-time build, please run the '${RepackagePlugin.TASK_POST_BUNDLE}' task again.")
            return
        }
        
        // Package all information needed to post the bundle and the analysis.
        PostState bundlePostState = newBundlePostState(project)

        if (bundlePostState) {
            if (ext.cachePost) {
                File tmpDir = Files.createTempDirectory("").toFile()
                bundlePostState.saveTo(tmpDir)
                println msg("Saved post state in ${tmpDir}")
            }

            if (!ext.dry) {
                Helper.doPost(ext.host, ext.port, ext.username, ext.password,
                              ext.clueProject, ext.profile, bundlePostState)
            }
        }

        p.cleanUp()
    }

    //A PostState for preserving all the information required to replay a bundle post
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
        PostState ps = new PostState(id:"bundle")
        addBasicPostOptions(project, ps)

        // The aplication regex.
        addStringInputFromExtensionOption(ps, ext, "APP_REGEX", "app_regex")

        // The heap snapshots are optional.
        ext.hprofs?.collect {
            ps.addFileInput("HEAPDLS", it)
        }

        boolean submitInputs = false
        ext.scavengeOutputDir.eachFile(FileType.FILES) { File f ->
            String n = f.name
            if (p.isCodeArtifact(n) && !n.endsWith(RepackagePlugin.SOURCES_FILE)) {
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
            project.logger.error msg("ERROR: No code inputs submitted, aborting task '${RepackagePlugin.TASK_POST_BUNDLE}'.")
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
        ps.addStringInput("PLATFORM", ext.platform instanceof AndroidPlatform ? "android_25_fulljars" : "java_8")

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
            ext.scavengeOutputDir.eachFile(FileType.FILES) { File f ->
                String n = f.name
                if (n.endsWith(RepackagePlugin.SOURCES_FILE)) {
                    addFileInput(project, ps, 'SOURCES_JAR', n)
                }
            }
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
