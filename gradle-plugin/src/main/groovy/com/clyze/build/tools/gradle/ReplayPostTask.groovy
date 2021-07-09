package com.clyze.build.tools.gradle

import com.clyze.build.tools.Conventions
import groovy.transform.CompileStatic
import com.clyze.client.web.Helper
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.options.Option

import static com.clyze.build.tools.Conventions.msg

/**
 * A task that replays the posting of a snapshot.
 */
@CompileStatic
class ReplayPostTask extends PostTask {

    @InputDirectory
    File fromDir

    @Option(option = 'fromDir', description = 'Set the directory to replay the post from.')
    void setFromDir(String fromDir) {
        this.fromDir = project.file(fromDir)
    }

    /**
     * The main task action.
     */
    @TaskAction
    void replayPost() {
        if (!fromDir)
            project.logger.error msg("ERROR: missing input directory (property 'fromDir')")

        Extension ext = Extension.of(project)
        if (ext.dry) {
            project.logger.warn msg("WARNING: ignoring 'dry' option")
            ext.dry = false
        }
        if (ext.cachePostDir) {
            project.logger.warn msg("WARNING: ignoring 'cache' option")
            ext.cachePostDir = null
        }

        Helper.postCachedSnapshot(ext.createPostOptions(false), fromDir, Conventions.SNAPSHOT_ID, ext.platform.printer, true)
    }
}
