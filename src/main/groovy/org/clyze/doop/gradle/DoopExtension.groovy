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
    // Android-only
    String subprojectName
    String flavor
    String buildType
    // List of group-name pairs of artifacts that are resolved by
    // extraInputs (and thus the resolver should ignore them).
    List<List<String>> replacedByExtraInputs

    Platform platform

    // Defaults.
    final String DEFAULT_SUBPROJECT_NAME = "."
    final String DEFAULT_BUILD_TYPE = "debug"

    def options(Closure cl) {
        ConfigureUtil.configure(cl, options)
    }

    // Check used to detect 'doop' sections in Android Gradle scripts.
    boolean definesAndroidProperties(Project project) {
        // We don't check for 'options', as that is never empty (but
        // initialized to defaults).
	    def err = { project.logger.error "ERROR: missing property: '${it}'" }
	    if (host == null) {
	        err 'host'
            return false
	    }
        if (port == 0) {
	        err 'port'
            return false
	    }
        if (username == null) {
	        err 'username'
            return false
	    }
        if (password == null) {
	        err 'password'
            return false
	    }
        if (subprojectName == null) {
            project.logger.warn "WARNING: missing property 'subprojectName', using top-level directory"
	        subprojectName = DEFAULT_SUBPROJECT_NAME
	    }
        if (buildType == null) {
            project.logger.warn "WARNING: missing property 'buildType', assuming buildType=${DEFAULT_BUILD_TYPE}"
            buildType = DEFAULT_BUILD_TYPE
	    }
	    return true
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
