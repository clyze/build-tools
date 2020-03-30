package org.clyze.build.tools.gradle

import groovy.transform.TypeChecked
import org.clyze.client.web.PostState
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.options.Option

import static org.clyze.build.tools.Conventions.msg

/**
 * A task that replays the posting of a bundle.
 */
@TypeChecked
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
        PostState bundlePostState
        if (!fromDir)
            project.logger.error msg("ERROR: missing input directory (property 'fromDir')")
        try {
            // Check if a bundle post state exists.
            bundlePostState = new PostState(id:"bundle")
            bundlePostState.loadFrom(fromDir)
        } catch (any) {
            project.logger.error msg("Error bundling state: ${any.message}")
            return
        }

        Extension ext = Extension.of(project)
        if (ext.dry) {
            project.logger.warn msg("WARNING: ignoring 'dry' option")
            ext.dry = false
        }
        if (ext.cachePost) {
            project.logger.warn msg("WARNING: ignoring 'cache' option")
            ext.cachePost = false
        }
        postBundlePostState(bundlePostState)
    }
}
