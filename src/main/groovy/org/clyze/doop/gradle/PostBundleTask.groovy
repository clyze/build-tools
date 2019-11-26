package org.clyze.doop.gradle

import groovy.io.FileType
import java.nio.file.Files
import org.clyze.client.web.Helper
import org.clyze.client.web.PostState
import org.clyze.client.web.http.DefaultHttpClientLifeCycle
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class PostBundleTask extends PostTask {

    @TaskAction
    void postBundle() {

        DoopExtension doop = DoopExtension.of(project)
        Platform p = doop.platform
        if (p.mustRunAgain()) {
            project.logger.error "ERROR: this looks like a first-time build, please run the '${DoopPlugin.TASK_POST_BUNDLE}' task again."
            return
        }
        
        // Package all information needed to post the bundle and the analysis.
        PostState bundlePostState = newBundlePostState(project)

        if (doop.cachePost) {
            File tmpDir = Files.createTempDirectory("").toFile()
            bundlePostState.saveTo(tmpDir)            
            println "Saved post state in ${tmpDir}"
        }

        if (!doop.dry) {
            Helper.doPost(doop.host, doop.port, doop.username, doop.password,
                          doop.clueProject, doop.profile, bundlePostState)
        }        

        p.cleanUp()
    }

    //A PostState for preserving all the information required to replay a bundle post
    private final PostState newBundlePostState(Project project) {

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
        DoopExtension doop = DoopExtension.of(project)
        Platform p = doop.platform
        PostState ps = new PostState(id:"bundle")
        addBasicPostOptions(project, ps)

        // The aplication regex.
        addStringInputFromDoopExtensionOption(ps, doop, "APP_REGEX", "app_regex")

        // The heap snapshots are optional.
        doop.hprofs?.collect { 
            ps.addFileInput("HEAPDLS", it)
        }

        doop.scavengeOutputDir.eachFile(FileType.FILES) { File f ->
            String n = f.name
            if (n != DoopPlugin.SOURCES_FILE && (n.endsWith('.apk') || n.endsWith('.jar') || n.endsWith('.aar'))) {
                addFileInput(project, ps, 'INPUTS', n)
                project.logger.info "Added local cached input: ${n}"
            }
        }

        // Filter out empty inputs.
        p.inputFiles.findAll(Helper.checkFileEmpty).each {
            ps.addFileInput("INPUTS", it)
            project.logger.info "Added input: ${it}"
        }

        // Filter out empty libraries.
        p.libraryFiles.findAll(Helper.checkFileEmpty).each {
            ps.addFileInput("LIBRARIES", it)
            project.logger.info "Added library: ${it}"
        }

        // The main class of the program. Usually empty on Android code.
        addStringInputFromDoopExtensionOption(ps, doop, "MAIN_CLASS", "main_class")

        // The platform to use when analyzing the code.
        ps.addStringInput("PLATFORM", doop.platform instanceof AndroidPlatform ? "android_25_fulljars" : "java_8")        

        // Upload sources (user can override with alternative sources archive).
        String altSourcesJar = doop.useSourcesJar
        if (altSourcesJar) {
            File sources = new File(altSourcesJar)
            if (!sources.exists()) {
                project.logger.warn "WARNING: explicit sources JAR ${altSourcesJar} does not exist, no sources will be uploaded."
            } else {
                ps.addFileInput("SOURCES_JAR", sources.canonicalPath)
            }
        } else {
            addFileInput(project, ps, 'SOURCES_JAR', DoopPlugin.SOURCES_FILE)
        }

        //tamiflex
        addFileInputFromDoopExtensionOption(ps, doop, "TAMIFLEX", "tamiflex")

        return ps
    }

    private static void addStringInputFromDoopExtensionOption(PostState ps, DoopExtension doop, String inputId, String optionId) {
        if (doop.options.containsKey(optionId)) {
            ps.addStringInput(inputId, doop.options[(optionId)] as String)
        }
    }

    private static void addFileInputFromDoopExtensionOption(PostState ps, DoopExtension doop, String inputId, String optionId) {
        if (doop.options.containsKey(optionId)) {
            ps.addFileInput(inputId, doop.options[(optionId)] as String)
        }
    }

}
