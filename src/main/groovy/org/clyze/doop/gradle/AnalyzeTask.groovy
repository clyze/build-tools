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

        doop.options.inputs = p.inputFiles(project) + p.getDependencies()

        File sources = project.tasks.findByName(DoopPlugin.TASK_SOURCES_JAR).outputs.files.files[0]
        File jcPluginMetadata = project.tasks.findByName(DoopPlugin.TASK_JCPLUGIN_ZIP).outputs.files.files[0]
        File hprof = null
        if (doop.hprof != null) {
            hprof = new File(doop.hprof)
        }

        PostState ps = doop.newPostState(sources, jcPluginMetadata, hprof)
        Helper.postAndStartAnalysis(ps, doop.cachePost)
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

        String path = args[0]
        println "Reading state from ${path}..."
        def deserialized = PostState.fromJson(path)
        File sources = deserialized["sources"]
        File hprof = deserialized["hprof"]
        File jcPluginMetadata = deserialized["jcPluginMetadata"]
        DoopExtension doop = deserialized["doop"]

        // Optionally read properties from ~/.gradle/gradle.properties.
        String homeDir = System.getProperty("user.home")
        if (homeDir != null) {
            String propertiesFileName = "${homeDir}/.gradle/gradle.properties"
            println propertiesFileName
            File propertiesFile = new File(propertiesFileName)
            if (propertiesFile.exists()) {
                println "Reading connection information from ${propertiesFile.getCanonicalPath()}"
                Properties properties = new Properties()
                propertiesFile.withInputStream {
                    properties.load(it)
                }
                def readProperty = { String name ->
                    String readVal = properties.getProperty(name)
                    if (readVal != null) {
                        println "Found ${name} = ${readVal}"
                    }
                    readVal
                }
                doop.username = readProperty("clue_user")             ?: doop.username
                doop.password = readProperty("clue_password")         ?: doop.password
                doop.host     = readProperty("clue_host")             ?: doop.host
                doop.port     = readProperty("clue_port").toInteger() ?: doop.port
            }
        }
        connectPostAndStartAnalysis(doop, sources, jcPluginMetadata, hprof)
    }
}
