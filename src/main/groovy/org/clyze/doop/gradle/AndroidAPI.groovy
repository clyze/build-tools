package org.clyze.doop.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree

// This class provides access to the Android Gradle API without a
// compile-time dependency.
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

    static List<String> getOutputs(Project project, String packageTask) {
        return project.tasks.findByName(packageTask).outputs.files
            .findAll { AndroidPlatform.extension(it.name) == 'apk' ||
                       AndroidPlatform.extension(it.name) == 'aar' }
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
}
