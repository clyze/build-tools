package doop.gradle

import doop.web.client.Helper
import doop.web.client.RestCommandBase
import groovy.json.JsonSlurper
import org.apache.http.HttpEntity
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.message.BasicNameValuePair
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.awt.*
import java.util.List

/**
 * Created by saiko on 28/7/2015.
 */
class AnalyseTask extends DefaultTask {

    @TaskAction
    void analyse() {

        DoopExtension doop = project.extensions.doop

        File sources = project.tasks.findByName('sourcesJar').outputs.files.files[0]
        File jcPluginMetadata = new File(project.projectDir, "jcplugin.zip")
        if (!jcPluginMetadata.exists()) {
            throw new RuntimeException("The jcplugin.zip is not found")
        }

        println "Connecting to server at ${doop.host}:${doop.port}"
        String token = createLoginCommand(doop).execute(doop.host, doop.port)

        println "Submitting ${doop.projectName}:${doop.projectVersion} for ${doop.analysis.name} analysis"
        postAndStartAnalysis(doop, sources, jcPluginMetadata, token)
    }

    private static void postAndStartAnalysis(DoopExtension doop, File sources, File jcPluginMetadata, String token) {

        def authenticator = {String h, int p, HttpUriRequest request ->
            //send the token with the request
            request.addHeader(RestCommandBase.HEADER_TOKEN, token)
        }

        RestCommandBase<Void> post = createPostCommand(doop, sources, jcPluginMetadata, authenticator)
        post.onSuccess = {HttpEntity entity ->
            String postedId = new JsonSlurper().parse(entity.getContent(), "UTF-8").id
            println "The analysis has been submitted successfully! Starting it (id: $postedId)."
            RestCommandBase<Void> start = createStartCommand(postedId, authenticator)
            start.onSuccess = { HttpEntity ent ->
                println "Sit back and relax while we analyse your code..."
                openBrowser(doop.host, doop.port, postedId, token)
            }
            start.execute(doop.host, doop.port)
        }
        post.execute(doop.host, doop.port)
    }

    private static RestCommandBase<Void> createPostCommand(DoopExtension doop, File sources, File jcPluginMetadata,
                                                           Closure authenticator) {
        return new RestCommandBase<Void>(
            endPoint: "analyses",
            requestBuilder: { String url ->
                HttpPost post = new HttpPost(url)
                MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                //submit a null id for the analysis to make the server generate one automatically
                Helper.buildPostRequest(builder, null, doop.analysis.name) {

                    //process the project name and version
                    builder.addPart("projectName", new StringBody(doop.projectName))
                    builder.addPart("projectVersion", new StringBody(doop.projectVersion))

                    //process the jars
                    Set<File> jars = doop.analysis.jar
                    println "Submitting jars: ${jars}"
                    Helper.addFilesToMultiPart("jar", jars.toList(), builder)

                    //process the sources
                    println "Submitting sources: ${sources}"
                    Helper.addFilesToMultiPart("sources", [sources], builder)

                    //process the jcPluginMetadata
                    println "Submitting jcplugin metadata: ${jcPluginMetadata}"
                    Helper.addFilesToMultiPart("jcPluginMetadata", [jcPluginMetadata], builder)

                    //process the options
                    Map<String, Object> options = doop.analysis.options
                    println "Submitting options: ${options}"
                    options.each { Map.Entry<String, Object> entry ->
                        String optionId = entry.getKey().toUpperCase()
                        Object value = entry.getValue()
                        if (value) {
                            if (optionId == "DYNAMIC") {
                                List<File> dynamicFiles = (value as List<String>).each { String file ->
                                    return project.file(file)
                                }

                                Helper.addFilesToMultiPart("DYNAMIC", dynamicFiles, builder)
                            }
                            else if (Helper.isFileOption(optionId)) {
                                Helper.addFilesToMultiPart(optionId, [project.file(value)], builder)
                            }
                            else {
                                builder.addPart(optionId, new StringBody(value as String))
                            }
                        }
                    }
                }
                HttpEntity entity = builder.build()
                post.setEntity(entity)
                return post
            },
            authenticator: authenticator
        )
    }

    private static RestCommandBase<Void> createStartCommand(String id, Closure authenticator) {
        return new RestCommandBase<Void>(
            endPoint: "analyses",
            requestBuilder: {String url ->
                return new HttpPut("${url}/${id}?status=start")
            },
            authenticator: authenticator
        )
    }

    private static RestCommandBase<String> createLoginCommand(DoopExtension doop) {
        return new RestCommandBase<String>(
            endPoint: "authenticate",
            authenticationRequired: false,
            requestBuilder: { String url ->
                HttpPost post = new HttpPost(url)
                List<NameValuePair> params = new ArrayList<>(2)
                params.add(new BasicNameValuePair("username", doop.username))
                params.add(new BasicNameValuePair("password", doop.password))
                post.setEntity(new UrlEncodedFormEntity(params))
                return post
            },
            onSuccess: { HttpEntity entity ->
                def json = new JsonSlurper().parse(entity.getContent(), "UTF-8")
                return json.token
            }
        )
    }

    private static void openBrowser(String host, int port, String postedId, String token) {
        File html = File.createTempFile("_doop", ".html")
        html.withWriter('UTF-8') { w ->
            w.write """\
                    <html>
                        <head>
                            <script>
                                document.cookie="SESSIONID=$token";
                                document.location="http://$host:$port/jdoop/web/analysis.html?id=$postedId"
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
