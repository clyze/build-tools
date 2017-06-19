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
    Map<String, Object> options

    def options(Closure cl) {
        ConfigureUtil.configure(cl, options)
    }
}
