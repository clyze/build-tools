package org.clyze.doop.gradle

import org.gradle.util.ConfigureUtil

class DoopExtension {
    String host
    int port
    String username
    String password
    String orgName
    String projectName
    String projectVersion
    String clueProject
    String rating
    String ratingCount
    String useSourcesJar
    File scavengeOutputDir
    List<String> hprofs
    // Extra inputs, as a list of paths relative to the project root
    // directory. Can be used to add dependency JARs whose resolutions
    // has failed or extra code.
    List<String> extraInputs
    Map<String, Object> options
    boolean cachePost = false
    String convertUTF8Dir
    boolean dry
    // Android-only
    String subprojectName
    String flavor
    String buildType
    // List of group-name pairs of artifacts that are resolved by
    // extraInputs (and thus the resolver should ignore them).
    List<List<String>> replacedByExtraInputs

    Platform platform

    def options(Closure cl) {
        ConfigureUtil.configure(cl, options)
    }

    // Check used to detect 'doop' sections in Android Gradle scripts.
    public boolean definesAndroidProperties() {
        // We don't check for 'options', as that is never empty (but
        // initialized to defaults).
        return (host != null) && (port != 0) && (username != null) && (password != null) && (subprojectName != null) && (buildType != null)
    }

    public List<String> getExtraInputFiles(File rootDir) {
        if (extraInputs == null) {
            return []
        } else {
            return extraInputs.collect { String fName ->
                File f = new File("${rootDir.canonicalPath}/${fName}")
                if (!f.exists()) {
                    println "Extra input ${f.canonicalPath} does not exist."
                } else {
                    println "Using extra input ${f.canonicalPath}"
                }
                f.canonicalPath
            }
        }
    }
}
