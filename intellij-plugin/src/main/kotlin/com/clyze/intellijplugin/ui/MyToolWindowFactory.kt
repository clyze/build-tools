package com.clyze.intellijplugin.ui

import com.clyze.intellijplugin.Poster
import com.clyze.intellijplugin.services.ClyzeProjectService
import com.clyze.intellijplugin.state.AnalysisRun
import com.clyze.intellijplugin.ui.Helper.CLYZE_CONFIG
import com.clyze.intellijplugin.ui.Helper.performServerAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.CheckBox
import com.intellij.ui.table.JBTable
import com.intellij.util.containers.reverse
import com.jetbrains.rd.util.first
import java.awt.*
import java.net.URI
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

/**
 * This is the main tool window that appears in the IDE and
 * interacts with the Clyze server.
 */
class MyToolWindowFactory : ToolWindowFactory, DumbAware {
    companion object {
        const val GRADLE = "Gradle"
        const val CLI = "CLI"

        fun processResults(map : Map<*, *>?, proc : (m : Map<*, *>) -> Unit) {
            if (map == null)
                return
            val results = map["results"]
            if (results != null && results is List<*>)
                results.forEach { res ->
                    if (res is Map<*, *>)
                        proc(res)
                }
        }
    }

    private fun setMaximumHeight(jc : JComponent, height : Int) {
        jc.maximumSize = Dimension(jc.maximumSize.width, height)
    }

    private fun updateWithSorted(component : ComboBox<String>, items : MutableList<String>) {
        component.removeAllItems()
        items.sort()
        items.forEach { component.addItem(it) }
    }

    private fun setInputModelUpdater(input : JTextField, setter : (t : String) -> Unit) {
        input.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(p0: DocumentEvent?) {
                setter(input.text)
            }

            override fun removeUpdate(p0: DocumentEvent?) {
                setter(input.text)
            }

            override fun changedUpdate(p0: DocumentEvent?) {
                setter(input.text)
            }
        })
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.PAGE_AXIS)

        val connectionPanel = JPanel()
        connectionPanel.border = BorderFactory.createTitledBorder("Setup")
        connectionPanel.layout = BoxLayout(connectionPanel, BoxLayout.PAGE_AXIS)
        val connectionForm = JPanel()
        connectionForm.layout = GridLayout(5, 2)
        // Host
        connectionForm.add(JLabel("Host:"))
        val hostInput = JTextField("localhost")
        connectionForm.add(hostInput)
        // Port
        connectionForm.add(JLabel("Port:"))
        val portInput = JTextField("8020")
        connectionForm.add(portInput)
        // Base path
        connectionForm.add(JLabel("Base path:"))
        val basePathInput = JTextField("/clue")
        connectionForm.add(basePathInput)
        // User
        connectionForm.add(JLabel("User:"))
        val authUserInput = JTextField("user000")
        connectionForm.add(authUserInput)
        connectionForm.add(JLabel("Token:"))
        val authTokenInput = JTextField("ccc03dba18bcb2a5bd1a6e2f")
        connectionForm.add(authTokenInput)
        connectionPanel.add(connectionForm)
        val connectBtn = JButton("Sync With Server")
        connectionPanel.add(connectBtn)
        setMaximumHeight(connectionPanel, 250)
        mainPanel.add(connectionPanel)

        val codePanel = JPanel()
        codePanel.border = BorderFactory.createTitledBorder("Code")
        codePanel.layout = BoxLayout(codePanel, BoxLayout.PAGE_AXIS)
        val codeStructurePanel = JPanel()
        codeStructurePanel.layout = GridLayout(3, 2)
        codeStructurePanel.add(JLabel("Project:"))
        val projects = ComboBox<String>()
        codeStructurePanel.add(JScrollPane(projects))
        codeStructurePanel.add(JLabel("Snapshot:"))
        val snapshots = ComboBox<String>()
        codeStructurePanel.add(JScrollPane(snapshots))
        codeStructurePanel.add(JLabel("Analysis:"))
        val analyses = ComboBox<String>()
        codeStructurePanel.add(JScrollPane(analyses))
        setMaximumHeight(codeStructurePanel, 200)
        codePanel.add(codeStructurePanel)
        val gotoWebBtn = JButton("Open in Browser")
        codePanel.add(gotoWebBtn)
        mainPanel.add(codePanel)

        val actionsPanel = JPanel()
        actionsPanel.layout = BoxLayout(actionsPanel, BoxLayout.PAGE_AXIS)
        actionsPanel.border = BorderFactory.createTitledBorder("Actions")
        // "Post" action.
        val postPanel = JPanel()
        postPanel.layout = BoxLayout(postPanel, BoxLayout.LINE_AXIS)
        val postBtn = JButton("Post Project")
        postPanel.add(postBtn)
        postPanel.add(JLabel("Method:"))
        val postMethodCombo = ComboBox<String>()
//        postMethodCombo.addItem(GRADLE)
        postMethodCombo.addItem(CLI)
        postMethodCombo.selectedItem = CLI
        postPanel.add(JScrollPane(postMethodCombo))
        val postStatus = JLabel()
        postPanel.add(postStatus)
        actionsPanel.add(postPanel)
        // "Analyze" action.
        val analyzePanel = JPanel()
        analyzePanel.layout = BoxLayout(analyzePanel, BoxLayout.LINE_AXIS)
        val startAnalysisBtn = JButton("Analyze")
        analyzePanel.add(startAnalysisBtn)
        val analysisTypes = ComboBox<String>()
        analyzePanel.add(JScrollPane(analysisTypes))
        actionsPanel.add(analyzePanel)
        setMaximumHeight(actionsPanel, 120)
        mainPanel.add(actionsPanel)

        val contentManager = toolWindow.contentManager
        val projectService = project.getService(ClyzeProjectService::class.java)
        val config = projectService.config

        // Update project-specific configurations.
        config.host = hostInput.text
        config.port = portInput.text
        config.basePath = basePathInput.text
        config.user = authUserInput.text
        config.token = authTokenInput.text
        config.projectName = projects.item
        config.snapshotName = snapshots.item
        setInputModelUpdater(hostInput) { s -> config.host = s }
        setInputModelUpdater(portInput) { s -> config.port = s }
        setInputModelUpdater(basePathInput) { s -> config.basePath = s }
        setInputModelUpdater(authUserInput) { s -> config.user = s }
        setInputModelUpdater(authTokenInput) { s -> config.token = s }

        // Results pane
        val lineResultsPanel = JPanel()
        lineResultsPanel.layout = BoxLayout(lineResultsPanel, BoxLayout.PAGE_AXIS)
        val resultsTableModel = LineResultsTableModel(ArrayList())
        val table = JBTable(resultsTableModel)
        lineResultsPanel.add(JScrollPane(table))

        // Analysis pane
        val analysisPanel = JPanel()
        analysisPanel.layout = BoxLayout(analysisPanel, BoxLayout.PAGE_AXIS)
        val dataset = ComboBox<String>()
        setMaximumHeight(dataset, 40)
        analysisPanel.add(dataset)

        val datasetOptionsPanel = JPanel()
        datasetOptionsPanel.layout = BoxLayout(datasetOptionsPanel, BoxLayout.LINE_AXIS)
        datasetOptionsPanel.add(JLabel("Start:"))
        val start = JSpinner(SpinnerNumberModel(0, 0, 10_000, 1))
        setMaximumHeight(start, 30)
        datasetOptionsPanel.add(start)
        datasetOptionsPanel.add(JLabel("Count:"))
        val count = JSpinner(SpinnerNumberModel(50, 0, 10_000, 1))
        setMaximumHeight(count, 30)
        datasetOptionsPanel.add(count)
        val appOnly = CheckBox("App-only filter", false)
        datasetOptionsPanel.add(appOnly)
        val refreshDatasetBtn = JButton("Refresh")
        datasetOptionsPanel.add(refreshDatasetBtn)
        analysisPanel.add(datasetOptionsPanel)

        val datasetResults = JBTable()
        analysisPanel.add(JScrollPane(datasetResults))

        fun getSelectedProject() : String? {
            return projects.item
        }

        fun getSelectedSnapshot() : String? {
            return snapshots.item
        }

        fun showAnalysisTypes() {
            performServerAction(projectService) { remote ->
                val projectAnalyses = remote.getProjectAnalyses(config.user, config.projectName)
                analysisTypes.removeAllItems()
                processResults(projectAnalyses) {
                    val name = it["displayName"]
                    if (name is String) {
                        analysisTypes.addItem(name)
                        val profile = it["id"]
                        println("Project analysis profile: " + profile)
                        if (profile is String) {
                            projectService.analysisTypeProfiles[name] = profile
                            projectService.analysisProfileDescriptor[profile] = it
                        }
                    }
                }
            }
        }

        fun syncServer() {
            val user = config.user ?: return
            performServerAction(projectService) { remote ->
                val projectsResp = remote.listProjects(user)
                println(projectsResp)
                projects.removeAllItems()
                processResults(projectsResp) {
                    val name = it["name"]
                    println("Found project: $name")
                    if (name is String) {
                        projects.addItem(name)
                        if (project.name == name)
                            projects.selectedItem = name
                    }
                }
            }
        }

        fun getAnalysisInfo(projectName : String, snapshotName : String, analysisName : String,
                            mapGetter : () -> Map<AnalysisRun, String>?) : String? {
            val anInfo = mapGetter()
            if (anInfo == null) {
                println("No analysis information for current project.")
                return null
            }
            val res = anInfo.filter {
                it.key.project == projectName && it.key.snapshot == snapshotName && it.key.analysis == analysisName
            }
            return if (res.isNotEmpty()) res.first().value else null
        }

        fun refreshDataset() {
            val projectName = getSelectedProject()
            val snapshotName = getSelectedSnapshot()
            val analysisName = analyses.item
            val output = dataset.item
            if (projectName == null || snapshotName == null || analysisName == null || output == null) {
                println("Null project/snapshot/analysis/output!")
                return
            }
            val analysisId = getAnalysisInfo(projectName, snapshotName, analysisName) { projectService.analysisIds }
            val profile = getAnalysisInfo(projectName, snapshotName, analysisName) { projectService.analysisProfiles }
            val profileInfo = projectService.analysisProfileDescriptor[profile]
            val outputInfo = profileInfo?.get("outputs")
            if (outputInfo != null && outputInfo is List<*>) {
                val outputSpec = outputInfo.find { (it is Map<*, *>) && (it["id"] == output) }
                if (outputSpec is Map<*, *>) {
                    val attributes = outputSpec["attributes"]
                    if (attributes is List<*>) {
                        val columns : MutableList<String> = ArrayList()
                        val attributeIds : MutableList<String> = ArrayList()
                        for (attribute in attributes) {
                            if (attribute is Map<*, *>) {
                                val displayName = attribute["displayName"]
                                if (displayName != null && displayName is String) {
                                    columns.add(displayName)
                                    val attributeId = attribute["id"]
                                    if (attributeId != null && attributeId is String)
                                        attributeIds.add(attributeId)
                                }
                            }
                        }
                        val datasetTableModel = DefaultTableModel(columns.toTypedArray(), 0)
                        datasetResults.model = datasetTableModel
                        performServerAction(projectService) { remote ->
                            val data = remote.getOutput(
                                config.user,
                                projectName,
                                snapshotName,
                                CLYZE_CONFIG,
                                analysisId,
                                output,
                                start.value.toString(),
                                count.value.toString(),
                                appOnly.isSelected.toString()
                            )
                            println("output dataset data = $data")
                            if (data == null)
                                return@performServerAction
                            processResults(data) {
                                val rowData = ArrayList<String>()
                                for (attributeId in attributeIds) {
                                    val attrValue = it[attributeId]
                                    if (attrValue != null)
                                        rowData.add(attrValue.toString())
                                }
                                datasetTableModel.addRow(rowData.toTypedArray())
                            }
                            datasetResults.repaint()
                        }
                    }
                }
            }
        }

        dataset.addActionListener {
            refreshDataset()
        }

        refreshDatasetBtn.addActionListener {
            refreshDataset()
        }

        connectBtn.addActionListener {
            syncServer()
        }

        projects.addActionListener {
            val projectName = getSelectedProject()
            projectService.setProjectName(projectName)
            if (projectName != null) {
                val user = config.user ?: return@addActionListener
                performServerAction(projectService) { remote ->
                    val projectInfo = remote.listSnapshots(user, projectName)
                    println(projectInfo)
                    val snapshotItems = ArrayList<String>()
                    processResults(projectInfo) {
                        val name = it["displayName"]
                        println("Found snapshot: $name")
                        if (name is String)
                            snapshotItems.add(name)
                    }
                    updateWithSorted(snapshots, snapshotItems)
                    showAnalysisTypes()
                }
            }
        }

        snapshots.addActionListener {
            val projectName = getSelectedProject()
            val snapshotName = getSelectedSnapshot()
            projectService.setSnapshot(snapshotName)
            if (projectName != null && snapshotName != null) {
                val user = config.user ?: return@addActionListener
                performServerAction(projectService) { remote ->
                    val analysisInfo = remote.getConfiguration(user, projectName, snapshotName, CLYZE_CONFIG)
                    println("Analysis information: $analysisInfo")
                    analyses.removeAllItems()
                    if (analysisInfo is Map<*, *>) {
                        val analysesInfo = analysisInfo["analyses"]
                        if (analysesInfo is List<*>) {
                            analysesInfo.forEach {
                                if (it is Map<*, *>) {
                                    val analysisName = it["displayName"]
                                    println("Found analysis: $analysisName")
                                    if (analysisName != null && analysisName is String) {
                                        analyses.addItem(analysisName)
                                        // Remember analysis id/profile.
                                        val id = it["id"]
                                        val profile = it["profile"]
                                        println("$analysisName : id = $id, profile = $profile")
                                        val analysisRun = AnalysisRun(projectName, snapshotName, analysisName)
                                        if (profile != null && profile is String)
                                            projectService.analysisProfiles[analysisRun] = profile
                                        if (id != null && id is String)
                                            projectService.analysisIds[analysisRun] = id
                                    }
                                }
                            }
                            if (analyses.itemCount > 0)
                                analyses.selectedItem = analyses.getItemAt(0)
                        }
                    }
                }
            }
        }

        analyses.addActionListener {
            val projectName = getSelectedProject()
            val snapshotName = getSelectedSnapshot()
            val analysisName = analyses.item
            if (projectName == null || snapshotName == null || analysisName == null) {
                println("Null project/snapshot/analysis!")
                return@addActionListener
            }
            val profile = getAnalysisInfo(projectName, snapshotName, analysisName) { projectService.analysisProfiles }
            println("Found analysis profile: " + profile)
            val analysisInfo = projectService.analysisProfileDescriptor[profile]
            println("Found analysis info: " + analysisInfo)
            val outputs = analysisInfo?.get("outputs")
            val datasetItems : MutableList<String> = ArrayList()
            if (outputs != null && outputs is List<*>) {
                println("Processing outputs...")
                for (output in outputs) {
                    println("Processing: " + output.toString())
                    if (output is Map<*, *>) {
                        val outputId = output["id"]
                        println("outputId: " + outputId)
                        if (outputId != null && outputId is String) {
                            datasetItems.add(outputId)
                        }
                    }
                }
                updateWithSorted(dataset, datasetItems)

                if (dataset.itemCount > 0)
                    dataset.selectedItem = dataset.getItemAt(0)
            }
        }

        postBtn.addActionListener {
            when (postMethodCombo.selectedItem) {
                GRADLE -> { println("Gradle integration is currently not supported via the UI.")  }
                CLI -> {
                    println("base path: " + project.basePath)
                    postStatus.text = "Posting..."
                    Thread {
                        Poster().postWithCLI(project, config)
                        postStatus.text = "Done."
                        syncServer()
                    }.start()
                }
            }
        }

        gotoWebBtn.addActionListener {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.BROWSE))
                    desktop.browse(URI(projectService.config.getWebPath()))
            }
        }

        startAnalysisBtn.addActionListener {
            val analysisType = analysisTypes.item
            if (analysisType == null) {
                println("ERROR: no analysis type available!")
                return@addActionListener
            }
            val profile = projectService.analysisTypeProfiles[analysisType]
            if (profile == null) {
                println("ERROR: no profile for analysis: $analysisType")
                return@addActionListener
            }
            val analysisProfileInfo = projectService.analysisProfileDescriptor[profile]
            println("analysisProfileInfo=$analysisProfileInfo")
            if (analysisProfileInfo == null) {
                println("ERROR: no profile info for profile: $profile")
                return@addActionListener
            }
            AnalysisPanel(analysisProfileInfo).showUI(projectService)
        }

        mainPanel.focusTraversalPolicy = object : FocusTraversalPolicy() {
            val transitions : Map<Component, Component> = mapOf(
                Pair(hostInput, portInput),
                Pair(portInput, basePathInput),
                Pair(basePathInput, authUserInput),
                Pair(authUserInput, authTokenInput),
                Pair(authTokenInput, connectBtn),
                Pair(connectBtn, projects),
                Pair(projects, snapshots),
                Pair(snapshots, analyses),
                Pair(analyses, gotoWebBtn),
                Pair(gotoWebBtn, postBtn),
                Pair(postBtn, postMethodCombo),
                Pair(postMethodCombo, startAnalysisBtn)
            )
            val transitionsReverse = transitions.reverse()

            override fun getComponentAfter(p0: Container?, p1: Component?): Component {
                return transitions[p1] ?: connectBtn
            }

            override fun getComponentBefore(p0: Container?, p1: Component?): Component {
                return transitionsReverse[p1] ?: connectBtn
            }

            override fun getFirstComponent(p0: Container?): Component {
                return connectBtn
            }

            override fun getLastComponent(p0: Container?): Component {
                return startAnalysisBtn
            }

            override fun getDefaultComponent(p0: Container?): Component {
                return connectBtn
            }

        }

        contentManager.addContent(contentManager.factory.createContent(mainPanel, "Main", true))
        val lineTab = contentManager.factory.createContent(lineResultsPanel, "Line Results", true)
        contentManager.addContent(lineTab)
        contentManager.addContent(contentManager.factory.createContent(analysisPanel, "Analysis Outputs", true))

        projectService.lineResults = Triple(table, resultsTableModel) { contentManager.setSelectedContent(lineTab, true) }
    }
}