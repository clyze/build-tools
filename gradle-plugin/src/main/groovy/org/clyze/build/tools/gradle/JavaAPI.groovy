package org.clyze.build.tools.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

/**
 * This class provides access to the internals of the API of the
 * 'java' Gradle plugin without a compile-time dependency.
 */
class JavaAPI {
    static SourceSetContainer getSourceSets(Project project) {
        return project.sourceSets
    }

    static Object getTestSources(Project project) {
        return getSourceSets(project).test.allSource
    }

    static Object getMainSources(Project project) {
        return getSourceSets(project).main.allSource
    }

    static List<String> getRuntimeFiles(Project project) {
        return project.configurations.runtime.files.collect { it.canonicalPath }
    }
}
