package com.clyze.intellijplugin.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.clyze.intellijplugin.services.ClyzeProjectService

internal class ClyzeProjectManagerListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        project.service<ClyzeProjectService>()
    }

    override fun projectClosed(project: Project) {
    }
}
