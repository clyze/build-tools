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
    String subprojectName
    String buildType
    File scavengeOutputDir
    String hprof
    List extraInputs
    Map<String, Object> options

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
}
