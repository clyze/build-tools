package org.clyze.doop.gradle

import org.clyze.client.web.Helper
import org.clyze.client.web.PostState
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class AnalyzeTask extends DefaultTask {

    @TaskAction
    void analyze() {

        DoopExtension doop = project.extensions.doop
        Platform p = doop.platform
        if (p.mustRunAgain()) {
            println "ERROR: this looks like a first-time build, please run the 'analyze' task again."
            return
        }

        // We expect sources/jcPluginMetadata to always exist.
        File sources
        String sourcesJar = doop.useSourcesJar
        if (sourcesJar != null) {
            sources = new File(sourcesJar)
            if (!sources.exists()) {
                printn "ERROR: sources JAR ${sourcesJar} does not exist."
            }
        } else {
            sources = project.tasks.findByName(DoopPlugin.TASK_SOURCES_JAR).outputs.files.files[0]
        }
        File jcPluginMetadata = project.tasks.findByName(DoopPlugin.TASK_JCPLUGIN_ZIP).outputs.files.files[0]
        // The HPROF input is optional.
        File hprof = doop.hprof != null ? new File(doop.hprof) : null

        // Filter out empty inputs.
        doop.options.inputs = p.inputFiles(project).findAll { String n ->
            boolean isEmpty = (new File(n)).length() == 0
            if (isEmpty) {
                println "Skipping empty file ${n}"
            }
            !isEmpty
        }
        // Package all information needed to post the analysis.
        PostState ps = doop.newPostState(sources, jcPluginMetadata, hprof)
        Helper.postAndStartAnalysis(ps, doop.cachePost, doop.dry)
    }


    // Entry point to call when replaying a previously posted
    // analysis. Example:
    //   ./gradlew replayPost -Pargs="/tmp/1486846789163549904"
    public static void main(String[] args) {
        if (args.size() == 0) {
            println "Usage: AnalyzeTask path-of-state-directory"
            println "Replay posting of an analysis to the server."
            System.exit(0)
        }

        Helper.replayPost(args[0])
    }
}
