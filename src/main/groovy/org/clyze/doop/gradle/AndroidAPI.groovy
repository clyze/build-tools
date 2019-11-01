package org.clyze.doop.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath

// This class provides access to the Android Gradle API (including
// internals) without a compile-time dependency.
class AndroidAPI {
    static void forEachSourceFile(Project project, def closure) {
        for (def set1 : project.android.sourceSets) {
            println "Set: ${set1.class}"
            if (set1.name == "main") {
                FileTree srcFiles = set1.java.sourceFiles
                closure(srcFiles)
            }
        }
    }

    static void forEachClasspathEntry(Project project, def closure) {
        project.android.getBootClasspath().collect {
            closure(it.canonicalPath)
        }
    }

    static List<String> getOutputs(Project project, String buildType, String flavor) {
        List<String> ret = [] as List<String>
        iterateOverVariants project, { variant ->
            if ((variant.buildType.name == buildType) &&
                (!flavor || variant.flavorName == flavor)) {
                variant.outputs.each { output ->
                    ret.add output.outputFile.canonicalPath
                }
            }
        }
        return ret
    }

    static List<String> getOutputs(Project project, String packageTask) {
        return project.tasks.findByName(packageTask).outputs.files
            .findAll { it.name.endsWith('.apk') || it.name.endsWith('.aar') }
            .collect { it.canonicalPath }
            .toList() as List<String>
    }

    // Get an internal class from the Android Gradle plugin.
    private static Class getInternalClass(String s) {
        try {
            return Class.forName(s)
        } catch (ClassNotFoundException ex) {
            println "WARNING: class ${s} not found in Android Gradle plugin: ${ex.message}"
            return null
        }
    }

    static void forEachTransform(Project project, def closure) {
        Class transformTask = getInternalClass("com.android.build.gradle.internal.pipeline.TransformTask")
        Class pgTransform = getInternalClass("com.android.build.gradle.internal.transforms.ProguardConfigurable")

        if (!transformTask || !pgTransform) {
            project.logger.info "Could not access internal Android Gradle API, ProGuard files may not be automatically resolved and must be provided via option \"doop.configurationFiles\" in build.gradle."
            return
        }

        project.tasks.each {
            if (transformTask.isInstance(it)) {
                try {
                    if (it.transform && pgTransform.isInstance(it.transform)) {
                        project.logger.info "Processing configuration files in transform: ${it} (${it.class} extends ${it.class.superclass})"
                        FileCollection pros = it.transform.getAllConfigurationFiles()
                        closure(pros)
                    } else {
                        project.logger.debug "Ignoring transform task: ${it}"
                    }
                } catch (Throwable t) {
                    println "Error reading task ${it.transform}: ${t.message}"
                }
            }
        }
    }

    static List<URI> getAsURIs(ClassLoader cLoader) {
        ClassPath cp = ClasspathUtil.getClasspath(cLoader)
        return cp.getAsURIs()
    }

    static Set<Project> getInterestingProjects(Project project) {
        Set<Project> projects = project.subprojects
        projects.add(project)
        return projects
    }

    static void iterateOverVariants(Project project, Closure cl) {
        getInterestingProjects(project).forEach { p ->
            p.android.applicationVariants.all { variant ->
                cl(variant)
            }
        }
    }

    static Set<String> getBuildTypes(Project project) {
        Set<String> bTypes = new HashSet<>()
        try {
            iterateOverVariants project, { variant ->
                bTypes.add(variant.buildType.name)
            }
        } catch (Throwable t) {
            // Just print the error message, without crashing. The
            // code above can fail but should only be used for warnings.
            t.printStackTrace()
        }
        return bTypes
    }

    static Set<String> getFlavors(Project project) {
        Set<String> pFlavors = new HashSet<>()
        try {
            iterateOverVariants project, { variant ->
                String fName = variant.flavorName
                if (fName && fName != "") {
                    pFlavors.add(fName)
                }
            }
        } catch (Throwable t) {
            // Just print the error message, without crashing. The
            // code above can fail but should only be used for warnings.
            t.printStackTrace()
        }
        return pFlavors
    }

    static boolean isMinifyEnabled(Project project, String buildType,
                                   boolean reportError) {
        boolean ret = false
        try {
            iterateOverVariants project, { variant ->
                // println "Examining: ${variant.buildType.name}"
                if (variant.buildType.name == buildType) {
                    ret = variant.buildType.minifyEnabled
                }
            }
        } catch (Throwable t) {
            project.logger.error "ERROR: failed to read 'minifyEnabled' property for build type '${buildType}': ${t.message}"
            // Just print the error message, without crashing. The
            // code above can fail but should only be used for warnings.
            t.printStackTrace()
        }
        if (reportError) {
            project.logger.warn "WARNING: build type '${buildType}' has no 'minifyEnabled' property."
        }
        return ret
    }
}
