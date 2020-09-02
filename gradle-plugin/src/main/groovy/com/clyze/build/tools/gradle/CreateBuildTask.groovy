package com.clyze.build.tools.gradle

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import static com.clyze.build.tools.Conventions.msg

/**
 * A wrapper task over the build creation subtasks.
 */
@CompileStatic
class CreateBuildTask extends DefaultTask {

    /**
     * The main task action.
     */
    @TaskAction
    void postBuild() {
        project.logger.info msg("Build created.")
    }
}
