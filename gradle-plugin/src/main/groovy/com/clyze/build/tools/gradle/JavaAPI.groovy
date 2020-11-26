package com.clyze.build.tools.gradle

import com.clyze.build.tools.Conventions
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer

/**
 * This class provides access to the internals of the API of the
 * 'java' Gradle plugin without a compile-time dependency.
 */
class JavaAPI {
    /**
     * Thin access method to the source sets of the Gradle build.
     * @param project  the current project
     * @return the source sets of the project
     */
    static SourceSetContainer getSourceSets(Project project) {
        return project.sourceSets
    }

    /**
     * Thin access method to the test sources configured in the Gradle build.
     * @param project  the current project
     * @return the test sources of the project
     */
    static Object getTestSources(Project project) {
        return getSourceSets(project).test.allSource
    }

    /**
     * Thin access method to the "main" sources configured in the Gradle build.
     * @param project  the current project
     * @return the test sources of the project
     */
    static Object getMainSources(Project project) {
        return getSourceSets(project).main.allSource
    }

    /**
     * Thin access method to the runtime code (libraries, dependencies) used
     * in the Gradle plugin.
     * @param project  the current project
     * @return a list of paths
     */
    static Set<String> getRuntimeFiles(Project project) {
        Set<String> ret = getConfigurationFiles(project.configurations.runtime)
        ret.addAll(getConfigurationFiles(project.configurations.runtimeClasspath))
        return ret
    }

    static Set<String> getConfigurationFiles(Configuration conf) {
        return conf.files.collect { it.canonicalPath } as Set<String>
    }
}
