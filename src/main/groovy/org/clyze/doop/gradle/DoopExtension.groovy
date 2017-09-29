package org.clyze.doop.gradle

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.util.ConfigureUtil

class DoopExtension {
    String host
    int port
    String username
    String password
    String orgName
    String projectName
    String projectVersion
    String subprojectName
    String buildType
    File scavengeOutputDir
    String hprof
    List extraInputs
    Map<String, Object> options
    boolean cachePost = false

    Platform platform

    def options(Closure cl) {
        ConfigureUtil.configure(cl, options)
    }

    // Check used to detect 'doop' sections in Gradle scripts.
    public boolean definesProperties() {
        // We don't check for 'options', as that is never empty (but
        // initialized to defaults).
        return (host != null) && (port != 0) && (username != null) && (password != null) && (subprojectName != null) && (buildType != null)
    }

    // Serialize information needed to replay analysis posting.
    public String toCacheJson(File sources, File jcPluginMetadata, File hprof) {
        String optionsJson = new JsonBuilder(options).toPrettyString()
        String sourcesName = sources == null? "null" : "\"${sources.name}\""
        String jcPluginMetadataName = jcPluginMetadata == null? "null" : "\"${jcPluginMetadata.name}\""
        String hprofName = hprof == null? "null" : "\"${hprof.name}\""
        return "{ \"host\" : \"${host}\",\n" +
               "  \"port\" : \"${port}\",\n" +
               "  \"username\" : \"${username}\",\n" +
               "  \"password\" : \"${password}\",\n" +
               "  \"orgName\" : \"${orgName}\",\n" +
               "  \"projectName\" : \"${projectName}\",\n" +
               "  \"projectVersion\" : \"${projectVersion}\",\n" +
               "  \"options\" : ${optionsJson},\n" +
               "  \"sourcesName\" : ${sourcesName},\n" +
               "  \"jcPluginMetadataName\" : ${jcPluginMetadataName},\n" +
               "  \"hprofName\" : ${hprofName}\n" +
               "}"
    }

    // Restore a DoopExtension object that contains enough information
    // to replay the posting of an analysis to the server.
    public static def fromCacheJson(String dir) {
        File jsonFile = new File("${dir}/analysis.json")
        Object obj = (new JsonSlurper()).parseText(jsonFile.text)
        DoopExtension doop = new DoopExtension()
        doop.host = obj.host
        doop.port = obj.port.toInteger()
        doop.username = obj.username
        doop.password = obj.password
        doop.orgName = obj.orgName
        doop.projectName = obj.projectName
        doop.projectVersion = obj.projectVersion
        doop.options = obj.options

        // Fix the paths of inputs to point to the given directory.
        def dirFile = { String n -> n == null ? null : new File("${dir}/${n}") }
        doop.options.inputs = doop.options.inputs.collect { dirFile(it.name) }

        return [ doop             : (DoopExtension)doop,
                 sources          : dirFile(obj.sourcesName),
                 jcPluginMetadata : dirFile(obj.jcPluginMetadataName),
                 hprof            : dirFile(obj.hprofName)
               ]
    }
}
