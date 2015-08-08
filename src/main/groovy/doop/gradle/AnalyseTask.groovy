package doop.gradle

import doop.web.client.Authenticator
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

/**
 * Created by saiko on 28/7/2015.
 */
class AnalyseTask extends DefaultTask {

    @TaskAction
    void analyse() {

        String host = project.extensions.doop.host
        int port = project.extensions.doop.port

        String username = project.extensions.doop.username
        String password = project.extensions.doop.password

        String name = project.extensions.doop.analysis.name
        String id = project.extensions.doop.analysis.id
        Set<File> jars = project.extensions.doop.analysis.jar
        Map<String, Object> options = project.extensions.doop.analysis.options

        println "host: ${host}"
        println "port: ${port}"
        println "Analysis name: ${name}"
        println "Analysis id: ${id}"
        println "Analysis jars: ${jars}"
        println "Analysis options: ${options}"

        Authenticator.init()
        String token = Authenticator.getUserToken()
        println "Stored user token: $token"
        if (!token) {
            token = createLoginCommand(username, password).execute(host, port)
            Authenticator.setUserToken(token)
            println "Updated user token: $token"
        }

        def authenticator = {String h, int p, HttpUriRequest request ->
            //send the token with the request
            request.addHeader(RestCommandBase.HEADER_TOKEN, token)
        }

        postAndStartAnalysis(host, port, name, id, jars, options, authenticator)
    }

    private static void postAndStartAnalysis(String host, int port, String name, String id, Set<File> jars,
                                             Map<String, Object> options, Closure authenticator) {

        println "Creating post command..."
        RestCommandBase<Void> post = createPostCommand(name, id, jars, options, authenticator)
        post.onSuccess = {HttpEntity entity ->
            String postedId = new JsonSlurper().parse(entity.getContent(), "UTF-8").id
            println "Executing start command..."
            createStartCommand(postedId, authenticator).execute(host, port)
        }
        println "Executing post command..."
        post.execute(host, port)
    }

    private static RestCommandBase<Void> createPostCommand(String name, String id, Set<File> jars,
                                                           Map<String, Object> options, Closure authenticator) {
        return new RestCommandBase<Void>(
            endPoint: "analyses",
            requestBuilder: { String url ->
                HttpPost post = new HttpPost(url)
                MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                Helper.buildPostRequest(builder, id, name) {

                    //process the jars
                   Helper.addFilesToMultiPart("jar", jars.toList(), builder)

                    //process the options
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
            authenticator: authenticator,
            onSuccess: {HttpEntity entity ->
                println "Sit back and relax while we analyse your code..."
                //TODO: Open the user's browser
            }
        )
    }

    private static RestCommandBase<String> createLoginCommand(String username, String password) {
        return new RestCommandBase<String>(
            endPoint: "authenticate",
            authenticationRequired: false,
            requestBuilder: { String url ->
                HttpPost post = new HttpPost(url)
                List<NameValuePair> params = new ArrayList<>(2)
                params.add(new BasicNameValuePair("username", username))
                params.add(new BasicNameValuePair("password", password))
                post.setEntity(new UrlEncodedFormEntity(params))
                return post
            },
            onSuccess: { HttpEntity entity ->
                def json = new JsonSlurper().parse(entity.getContent(), "UTF-8")
                return json.token
            }
        )
    }
}
