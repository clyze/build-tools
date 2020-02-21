package org.clyze.build.tools.gradle

import groovy.transform.TypeChecked
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import static org.clyze.build.tools.Conventions.msg

/**
 * A wrapper task over the bundle creation subtasks.
 */
@TypeChecked
class CreateBundleTask extends DefaultTask {

    /**
     * The main task action.
     */
    @TaskAction
    void postBundle() {
        project.logger.info msg("Bundle created.")
    }
}
