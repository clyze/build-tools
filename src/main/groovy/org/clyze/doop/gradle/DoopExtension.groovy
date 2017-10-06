package org.clyze.doop.gradle

import org.clyze.client.web.PostState
import org.gradle.util.ConfigureUtil

class DoopExtension {
    String host
    int port
    String username
    String password
    String orgName
    String projectName
    String projectVersion
    String rating
    String ratingCount
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

    public PostState newPostState(File sources, File jcPluginMetadata, File hprof) {
        return new PostState(host, port, username, password, orgName,
                             projectName, projectVersion, rating, ratingCount,
                             options, sources, jcPluginMetadata, hprof)
    }

    public List<File> getExtraInputFiles(File rootDir) {
        List<String> extraInputs = extraInputs ?: []
        return extraInputs.collect { String fName ->
            File f = new File("${rootDir.canonicalPath}/${fName}")
            if (!f.exists()) { println "Extra input ${f.canonicalPath} does not exist." }
            f
        }
    }
}
