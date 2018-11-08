package org.clyze.doop.gradle

import org.clyze.client.web.PostState

import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.options.Option

class ReplayPostTask extends DefaultTask {

	@Input
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
    	}
    	catch(any) {
    		println "Error: ${any.getMessage()}"
    		return
    	}

    	if (bundlePostState) {
    		try {
    			//check if an analysis post state exists
    			analysisPostState = new PostState(id:"analysis")
    			analysisPostState.loadFrom(fromDir)
    		}
    		catch(any) {
    			println "Warning: ${any.getMessage()}"
    		}

    		DoopExtension doop = project.extensions.doop

    		AnalyzeTask.doPost(doop, bundlePostState, analysisPostState)
    	}    	
    }
}