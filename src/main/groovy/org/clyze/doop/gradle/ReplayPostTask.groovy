package org.clyze.doop.gradle

import org.clyze.client.web.PostState

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.options.Option

class ReplayPostTask extends DefaultTask {

	@InputDirectory
	File fromDir

	@Option(option = 'fromDir', description = 'Set the directory to replay the post from.')
	void setFromDir(String fromDir) {
		this.fromDir = project.file(fromDir)
	}

	@TaskAction
    void replayPost() {
    	PostState bundlePostState, analysisPostState    	
    	try {
    		//check if a bundle post state exists
    		bundlePostState = new PostState(id:"bundle")
    		bundlePostState.loadFrom(fromDir)
    	} catch (any) {
    		project.logger.error "Error bundling state: ${any.getMessage()}"
    		return
    	}

		try {
			//check if an analysis post state exists
			analysisPostState = new PostState(id: "analysis")
			analysisPostState.loadFrom(fromDir)
		} catch (any) {
			project.logger.error "Error bundling state: ${any.message}"
			return
		}

		AnalyzeTask.doPost(DoopExtension.of(project), bundlePostState, analysisPostState)
    }
}
