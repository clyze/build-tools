import doop.gradle.DoopAnalyseTask
import doop.gradle.DoopAnalysisExtension
import doop.gradle.DoopClientExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class DoopPlugin implements Plugin<Project> {
    static final String DOOP_GROUP = "Doop"

    @Override
    void apply(Project project) {
        project.extensions.create('doop', DoopClientExtension)
        project.extensions.create('analysis', DoopAnalysisExtension)
        configureDefaults(project)
        configureAnalyseTask(project)
    }

    private void configureDefaults(Project project) {
        project.extensions.analysis.id = project.name
        project.extensions.analysis.jars = project.configurations.runtime.files
    }

    private void configureAnalyseTask(Project project) {
        DoopAnalyseTask task = project.tasks.create('analyse', DoopAnalyseTask)
        task.description = 'Starts the analysis of the project'
        task.group = DOOP_GROUP

    }
}