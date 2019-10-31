package org.clyze.doop.gradle

import groovy.transform.TypeChecked
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

@TypeChecked
class DoopExtension {
    String host
    int port
    String username
    String password
    String orgName
    String projectName
    String projectVersion
    String clueProject
    String useSourcesJar
    File scavengeOutputDir
    // The configration files to use.
    List<String> configurationFiles
    List<String> hprofs
    // Extra inputs, as a list of paths relative to the project root
    // directory. Can be used to add dependency JARs whose resolutions
    // has failed or extra code.
    List<String> extraInputs
    Map<String, Object> options
    boolean cachePost = false
    String convertUTF8Dir
    boolean dry
    String profile
    // Android-only
    String subprojectName
    String flavor
    String buildType
    String apkFilter
    // List of group-name pairs of artifacts that are resolved by
    // extraInputs (and thus the resolver should ignore them).
    List<List<String>> replacedByExtraInputs

    Platform platform

    def options(Closure cl) {
        ConfigureUtil.configure(cl, options)
    }

    List<String> getExtraInputFiles(File rootDir) {
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

    static DoopExtension of(Project project) {
        String sectionName = "doop"
        Object sec = project.extensions.getByName(sectionName)
        if (sec == null) {
            throw new RuntimeException("Missing section \"${sectionName}\" in build.gradle.")
        }
        return (sec instanceof DoopExtension) ? sec : null
    }
}
