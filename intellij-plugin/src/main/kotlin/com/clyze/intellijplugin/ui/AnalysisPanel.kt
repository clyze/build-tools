package com.clyze.intellijplugin.ui

import com.clyze.intellijplugin.Helper
import com.clyze.intellijplugin.Helper.CLYZE_CONFIG
import com.clyze.intellijplugin.services.ClyzeProjectService
import com.intellij.openapi.ui.ComboBox
import java.awt.GridLayout
import javax.swing.*

/**
 * This class dynamically creates a "configure and start analysis" dialog for a given analysis profile.
 */
class AnalysisPanel(private val profile : Map<*, *>) {
    /**
     * Create the UI (dialog) for the given analysis profile.
     * @param projectService    the service object of the current project
     */
    fun showUI(projectService : ClyzeProjectService) {
        val analysisName = profile["displayName"]

        val optionsPanel = JPanel()
        optionsPanel.border = BorderFactory.createTitledBorder("Options")
        val options = profile["options"]
        val optionInfo = HashMap<Int, Pair<String, JComponent>>()
        var optionsNum = 0
        if (options != null) {
            if (options is List<*>) {
                options.forEach { option ->
                    var optionId : String? = null
                    var optionComponent : JComponent? = null
                    if (option is Map<*, *>) {
                        val name = option["name"]
                        if (name is String) {
                            optionsPanel.add(JLabel(name))
                            val id = option["id"]
                            if (id is String) {
                                optionId = id
                                val validValues = option["validValues"]
                                if (validValues is List<*>) {
                                    if (validValues.size == 0) {
                                        val isBoolean = option["isBoolean"]
                                        if (isBoolean != null && isBoolean is Boolean && isBoolean == true) {
                                            val checkbox = JCheckBox()
                                            ifDefaultValue(option) { checkbox.isSelected = (it == "true") }
                                            optionComponent = checkbox
                                        } else {
                                            val field = JTextField()
                                            ifDefaultValue(option) { field.text = it }
                                            optionComponent = field
                                        }
                                    } else {
                                        val multipleValues = option["multipleValues"]
                                        if (multipleValues is Boolean && multipleValues == true) {
                                            val list = JList(validValues.toTypedArray())
                                            list.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
                                            ifDefaultValue(option) { list.setSelectedValue(it, true) }
                                            optionComponent = list
                                        } else {
                                            val comboBox = ComboBox<String>()
                                            validValues.forEach { if (it is String) comboBox.addItem(it) }
                                            ifDefaultValue(option) { comboBox.selectedItem = it }
                                            optionComponent = comboBox
                                        }
                                    }
                                }
                            } else
                                println("Ignoring option: $name")
                            if (optionId == null || optionComponent == null) {
                                println("WARNING: ignoring $option")
                                return@forEach
                            }
                            optionInfo[optionsNum] = Pair(optionId, optionComponent)
                            optionsPanel.add(if (optionComponent is JList<*>) JScrollPane(optionComponent) else optionComponent)
                            optionsNum++
                        }
                    }
                }
            }
            optionsPanel.layout = GridLayout(optionsNum, 2)
        }

        val frame = JFrame()
        frame.title = "New analysis: $analysisName"

        val panel = frame.contentPane
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(optionsPanel)
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.LINE_AXIS)
        val startBtn = JButton("Start")
        buttonPanel.add(startBtn)
        val analysisStatus = JLabel()
        buttonPanel.add(analysisStatus)
        startBtn.addActionListener {
            Helper.performServerAction(projectService) { remote ->
                val config = projectService.config
                val anOptions = ArrayList<String>()
                for (i in 0 until optionsNum) {
                    val oi = optionInfo[i]
                    if (oi == null) {
                        println("ERROR: no option info for #$i")
                    } else {
                        val prefix = oi.first + "="
                        when (val comp = oi.second) {
                            is ComboBox<*> -> anOptions.add(prefix + comp.item.toString())
                            is JTextField -> anOptions.add(prefix + comp.text)
                            is JCheckBox -> anOptions.add(prefix + comp.isSelected.toString())
                            is JList<*> -> comp.selectedValuesList.forEach {
                                anOptions.add(prefix + it.toString())
                            }
                            else -> println("ERROR: Ignoring component $comp")
                        }
                    }
                }
                println("Analysis options: $anOptions")
                val projectName = config.projectName
                if (projectName == null) {
                    MyToolWindowFactory.reportError("No project selected in the code tree.")
                    return@performServerAction
                }
                val snapshotName = config.snapshotName
                if (snapshotName == null) {
                    MyToolWindowFactory.reportError("No snapshot selected in the code tree.")
                    return@performServerAction
                }
                analysisStatus.text = "Analyzing..."
                remote.analyze(config.getUser(), projectName, snapshotName, CLYZE_CONFIG, profile["id"]?.toString(), anOptions)
                analysisStatus.text = "Done."
                frame.dispose()
            }
        }
        panel.add(buttonPanel)

        frame.pack()
        frame.isVisible = true
    }

    private fun ifDefaultValue(option : Map<*, *>, proc : (String) -> Unit) {
        val defaultValue = option["defaultValue"]
        if (defaultValue != null && defaultValue is String)
            proc(defaultValue)
    }
}
