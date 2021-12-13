package com.clyze.intellijplugin.ui

import com.clyze.client.web.api.Remote
import com.clyze.intellijplugin.services.ClyzeProjectService
import com.intellij.openapi.application.ApplicationManager
import javax.swing.JFrame
import javax.swing.JOptionPane

/**
 * A helper class for UI functionality.
 */
object Helper {
    /** Default Clyze configuration to use. */
    const val CLYZE_CONFIG = "clyze.json"

    /**
     * Helper function to connect to the server (async) and
     * show an error dialog on failure.
     */
    fun performServerAction(projectService: ClyzeProjectService, action: (Remote) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            try {
                action(projectService.getRemote())
            } catch (ex: Exception) {
                println("Connectivity error: ${ex.message}")
                ex.printStackTrace()
                JOptionPane.showMessageDialog(
                    JFrame(),
                    "Could not reach Clyze server!",
                    "Server error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
}