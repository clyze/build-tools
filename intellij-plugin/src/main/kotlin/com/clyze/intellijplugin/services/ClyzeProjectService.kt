package com.clyze.intellijplugin.services

import com.clyze.client.web.api.Remote
import com.intellij.openapi.project.Project
import com.clyze.intellijplugin.MyBundle
import com.clyze.intellijplugin.state.AnalysisRun
import com.clyze.intellijplugin.state.Config
import com.clyze.intellijplugin.ui.LineResultsTableModel
import com.intellij.ui.table.JBTable

/**
 * This service holds the plugin state for a specific open project.
 */
class ClyzeProjectService(project: Project) {
    val config = Config(Config.defaultRemote, "", "", null, null)
    var lineResults : Triple<JBTable, LineResultsTableModel, () -> Unit>? = null
    /** Map analysis run to analysis profile. */
    val analysisProfiles : MutableMap<AnalysisRun, String> = HashMap()
    /** Map analysis run to (low-level) analysis id. */
    val analysisIds : MutableMap<AnalysisRun, String> = HashMap()
    /** Map analysis type to analysis profile. */
    val analysisTypeProfiles : MutableMap<String, String> = HashMap()
    /** Map analysis profile to its description. */
    val analysisProfileDescriptor : MutableMap<String, Map<*, *>> = HashMap()

    init {
        println(MyBundle.message("projectService", project.name))
    }

    fun getRemote() : Remote {
        return config.getRemote()
    }

    fun setProjectName(projectName: String?) {
        config.projectName = projectName
    }

    fun setSnapshot(snapshotName: String?) {
        config.snapshotName = snapshotName
    }
}
