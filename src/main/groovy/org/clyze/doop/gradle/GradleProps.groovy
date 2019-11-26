package org.clyze.doop.gradle

import org.gradle.api.Project

class GradleProps {
    static Object get(Project project, String prop) {
        if (project.hasProperty(prop)) {
            return project."${prop}"
        }
        return null
    }
}
