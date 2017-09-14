package org.clyze.doop.gradle

import groovy.json.JsonSlurper
import org.apache.http.HttpEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpUriRequest
import org.clyze.client.web.Helper
import org.clyze.client.web.RestCommandBase
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.awt.*

class AnalyzeTask extends DefaultTask {

    @TaskAction
    void analyze() {

        if (DoopPlugin.platform.mustRunAgain()) {
            println "ERROR: this looks like a first-time build, please run the 'analyze' task again."
            return
        }

        DoopExtension doop = project.extensions.doop

        doop.options.inputs = DoopPlugin.platform.inputFiles(project)

        File sources = project.tasks.findByName(DoopPlugin.TASK_SOURCES_JAR).outputs.files.files[0]
        File jcPluginMetadata = project.tasks.findByName(DoopPlugin.TASK_JCPLUGIN_ZIP).outputs.files.files[0]
        File hprof = null
        if (doop.hprof != null)
            hprof = new File(doop.hprof)

        println "Connecting to server at ${doop.host}:${doop.port}"
        String token = createLoginCommand(doop).execute(doop.host, doop.port)

        println "Submitting ${doop.projectName} version ${doop.projectVersion} for ${doop.options.analysis} analysis"
        postAndStartAnalysis(doop, sources, jcPluginMetadata, hprof, token)
    }

    private static void postAndStartAnalysis(DoopExtension doop, File sources, File jcPluginMetadata, File hprof, String token) {

        def authenticator = {String h, int p, HttpUriRequest request ->
            //send the token with the request
            request.addHeader(RestCommandBase.HEADER_TOKEN, token)
        }

        String autoLoginToken = null

        RestCommandBase<String> postAnalysis = createPostCommand(doop, sources, jcPluginMetadata, hprof, authenticator)
        String postedId = postAnalysis.execute(doop.host, doop.port)
        println "The analysis has been submitted successfully: $postedId."

        RestCommandBase<String> createAutoLoginToken = createAutoLoginTokenCommand(authenticator)
        try {
            autoLoginToken = createAutoLoginToken.execute(doop.host, doop.port)

        }
        catch(Exception e) {
            println "Autologin failed: ${e.getMessage()}"
        }

        String analysisPageURL = createAnalysisPageURL(doop.host, doop.port, postedId, autoLoginToken)

        RestCommandBase<Void> start = createStartCommand(postedId, authenticator)
        start.onSuccess = { HttpEntity ent ->

            if (autoLoginToken) {
                println "Sit back and relax while we analyze your code..."
                try {
                    openBrowser(analysisPageURL)
                } catch(Exception e) {
                    println "Analysis has been posted to the server, please visit $analysisPageURL"
                }
            }
            else {
                println "Visit $analysisPageURL"
            }
        }
        start.execute(doop.host, doop.port)
    }

    private static RestCommandBase<String> createPostCommand(DoopExtension doop, 
                                                             File sources, 
                                                             File jcPluginMetadata,
                                                             File hprof, 
                                                             Closure authenticator) {

        RestCommandBase<String> command = Helper.createPostDoopAnalysisCommand(
            doop.orgName,
            doop.projectName,
            doop.projectVersion,
            null, //rating
            null, //ratingCount
            sources,
            jcPluginMetadata,
            hprof,
            doop.options
        )
        command.authenticator = authenticator
        return command
    }

    private static RestCommandBase<String> createAutoLoginTokenCommand(Closure authenticator) {
        return new RestCommandBase<String>(
            endPoint: "token",
            requestBuilder:  { String url ->
                return new HttpPost(url)
            },
            onSuccess: { HttpEntity entity ->
                def json = new JsonSlurper().parse(entity.getContent(), "UTF-8")
                return json.token
            },
            authenticator: authenticator
        )
    }

    private static RestCommandBase<Void> createStartCommand(String id, Closure authenticator) {
        RestCommandBase<Void> command = Helper.createStartCommand(id)
        command.authenticator = authenticator
        return command
    }

    private static RestCommandBase<String> createLoginCommand(DoopExtension doop) {
        return Helper.createLoginCommand(doop.username, doop.password)        
    }

    private static String createAnalysisPageURL(String host, int port, String postedId, String token = null) {
        return "http://$host:$port/clue/" + (token ? "?t=$token" : "") + "#/analyses/$postedId"
    }

    private static void openBrowser(String url) {
        File html = File.createTempFile("_doop", ".html")
        html.withWriter('UTF-8') { w ->
            w.write """\
                    <html>
                        <head>
                            <script>
                                document.location="$url"
                            </script>
                        </head>
                        <body>
                        </body>
                    </html>
                    """.stripIndent()
        }
        Desktop.getDesktop().browse(html.toURI())
    }
}
