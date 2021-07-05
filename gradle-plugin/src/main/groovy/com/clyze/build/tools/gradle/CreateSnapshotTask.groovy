package com.clyze.build.tools.gradle

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import static com.clyze.build.tools.Conventions.msg

/**
 * A wrapper task over the snapshot creation subtasks.
 */
@CompileStatic
class CreateSnapshotTask extends DefaultTask {

    /**
     * The main task action.
     */
    @TaskAction
    void postSnapshot() {
        project.logger.info msg("Snapshot created.")
    }
}
