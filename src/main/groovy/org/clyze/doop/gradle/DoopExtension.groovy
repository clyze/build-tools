package org.clyze.doop.gradle

import org.gradle.api.Project
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

    def options(Closure cl) {
        ConfigureUtil.configure(cl, options)
    }

    // Check used to detect 'doop' sections in Android Gradle scripts.
    boolean definesAndroidProperties() {
        // We don't check for 'options', as that is never empty (but
        // initialized to defaults).
	def err = { println("Error: missing property: '${it}'") }
	if (host == null) {
	    err 'host'
	} else if (port == 0) {
	    err 'port'
	} else if (username == null) {
	    err 'username'
	} else if (password == null) {
	    err 'password'
	} else if (subprojectName == null) {
	    err 'subprojectName'
	} else if (buildType == null) {
	    err 'buildType'
	} else {
	    return true
	}
	return false
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
