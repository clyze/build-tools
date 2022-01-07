package com.clyze.intellijplugin.ui

import java.awt.Dimension
import javax.swing.JComponent

/**
 * A helper class for UI functionality.
 */
object UIHelper {
    fun setMaximumHeight(jc : JComponent, height : Int) {
        jc.maximumSize = Dimension(jc.maximumSize.width, height)
    }
}