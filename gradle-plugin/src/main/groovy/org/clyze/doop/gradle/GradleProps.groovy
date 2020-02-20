package org.clyze.doop.gradle

import org.gradle.api.Project

/**
 * Utility class for reading Gradle project properties.
 */
class GradleProps {
    /**
     * Project property getter.
     *
     * @param project   the project
     * @param prop      the name of the property
     * @return          the value of the property (null if property was not found)
     */
    static Object get(Project project, String prop) {
        if (project.hasProperty(prop)) {
            return project."${prop}"
        }
        return null
    }
}
