package org.clyze.doop.gradle

import java.nio.file.Files
import org.clyze.client.web.Helper
import org.clyze.client.web.PostState
import org.clyze.client.web.http.DefaultHttpClientLifeCycle
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class AnalyzeTask extends DefaultTask {

    @TaskAction
    void analyze() {

        DoopExtension doop = DoopExtension.of(project)
        Platform p = doop.platform
        if (p.mustRunAgain()) {
            println "ERROR: this looks like a first-time build, please run the 'analyze' task again."
            return
        }
        
        // Package all information needed to post the bundle and the analysis.
        PostState bundlePostState   = newBundlePostState(project)
        PostState analysisPostState = newAnalysisPostState(project)

        if (doop.cachePost) {
            File tmpDir = Files.createTempDirectory("").toFile()

            bundlePostState.saveTo(tmpDir)            
            if (analysisPostState.inputs) {
                analysisPostState.saveTo(tmpDir)                
            }
            println "Saved post state in $tmpDir"
        }

        if (!doop.dry) {            
            doPost(doop, bundlePostState, analysisPostState)
        }        

        p.cleanUp()
    }

    static void doPost(DoopExtension doop, PostState bundlePostState, PostState analysisPostState) {
        Helper.doPost(doop.host, doop.port, doop.username, doop.password, doop.clueProject, bundlePostState, analysisPostState)
    }

    //A PostState for preserving all the information required to replay a bundle post
    private static final PostState newBundlePostState(Project project) {

        /*         
        These are the options for bundles: 
        --app_regex <arg>        
        --dacapo                     
        --dacapo_bach            
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

        //app_regex
        addStringInputFromDoopExtensionOption(ps, doop, "APP_REGEX", "app_regex")        
        //dacapo
        addStringInputFromDoopExtensionOption(ps, doop, "DACAPO", "dacapo")
        //dacapo_bach
        addStringInputFromDoopExtensionOption(ps, doop, "DACAPO_BACH", "dacapo_bach")

        // The heapdls are optional.
        doop.hprofs?.collect { 
            ps.addFileInput("HEAPDLS", it)
        }

        // Filter out empty inputs.
        p.inputFiles.findAll(Helper.checkFileEmpty).each {
            ps.addFileInput("INPUTS", it)
        }

        // We expect jcplugin_metadata to always exist.
        File jcPluginMetadata = project.tasks.findByName(DoopPlugin.TASK_JCPLUGIN_ZIP).outputs.files.files[0]        
        ps.addFileInput("JCPLUGIN_METADATA", jcPluginMetadata.canonicalPath)

        // Filter out empty libraries.
        p.libraryFiles.findAll(Helper.checkFileEmpty).each {
            ps.addFileInput("LIBRARIES", it)
        }

        //main_class
        addStringInputFromDoopExtensionOption(ps, doop, "MAIN_CLASS", "main_class")

        //platform
        ps.addStringInput("PLATFORM", doop.platform instanceof AndroidPlatform ? "android_25_fulljars" : "java_8")        

        // We expect sources_jar to always exist.
        File sources
        String sourcesJar = doop.useSourcesJar
        if (sourcesJar) {
            sources = new File(sourcesJar)
            if (!sources.exists()) {
                println "WARNING: explicit sources JAR ${sourcesJar} does not exist, no sources will be uploaded."
                sources = null
            }
        } else {
            sources = project.tasks.findByName(DoopPlugin.TASK_SOURCES_JAR).outputs.files.files[0]
        }
        if (sources) {
            ps.addFileInput("SOURCES_JAR", sources.canonicalPath)
        }        

        //tamiflex
        addFileInputFromDoopExtensionOption(ps, doop, "TAMIFLEX", "tamiflex")

        return ps
    }

    //A PostState for preserving all the information required to replay an analysis post
    private static final PostState newAnalysisPostState(Project project) {

        DoopExtension doop = DoopExtension.of(project)
        PostState ps = new PostState(id:"analysis")

        def json = Helper.createCommandForOptionsDiscovery("ANALYSIS", new DefaultHttpClientLifeCycle()).execute(doop.host, doop.port)
        Set<String> supportedOptionIds = json.options.collect { it.id.toLowerCase() } as Set<String>
        doop.options.each { k, v ->
            if (supportedOptionIds.contains(k)) {
                ps.addStringInput(k.toUpperCase(), v as String)
            }
        }

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
