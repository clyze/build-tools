package doop.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Created by saiko on 28/7/2015.
 */
class DoopAnalyseTask extends DefaultTask {

    @TaskAction
    void analyse() {
        println "URL: ${project.extensions.doop.url}"
        println "username: ${project.extensions.doop.username}"
        println "Analysis id: ${project.extensions.doop.analysis.id}"
        println "Analysis jars: ${project.extensions.doop.analysis.jar}"
        println "Analysis options: ${project.extensions.doop.analysis.options}"
    }
}
