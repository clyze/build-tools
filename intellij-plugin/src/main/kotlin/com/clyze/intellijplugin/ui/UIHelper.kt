package com.clyze.intellijplugin.ui

import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JOptionPane

/**
 * A helper class for UI functionality.
 */
object UIHelper {
    fun setMaximumHeight(jc : JComponent, height : Int) {
        jc.maximumSize = Dimension(jc.maximumSize.width, height)
    }

    fun reportError(msg :String) {
        JOptionPane.showMessageDialog(
            JFrame(),
            msg,
            "Server error",
            JOptionPane.ERROR_MESSAGE)
    }

    fun reportNoProjectSelected() {
        reportError("No project selected in the code tree.")
    }

    fun reportNoSnapshotSelected() {
        reportError("No snapshot selected in the code tree.")
    }
}