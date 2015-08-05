package doop.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Created by saiko on 28/7/2015.
 */
class DoopAnalyseTask extends DefaultTask {

    @TaskAction
    void analyse() {
        println "Analysis id: ${project.extensions.doop.analysis.id}"
        println "Analysis jars: ${project.extensions.doop.analysis.jars}"
    }
}
