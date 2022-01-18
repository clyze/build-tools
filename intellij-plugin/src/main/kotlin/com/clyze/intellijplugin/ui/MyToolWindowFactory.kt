package com.clyze.intellijplugin.ui

import com.clyze.intellijplugin.Helper.CLYZE_CONFIG
import com.clyze.intellijplugin.Helper.performServerAction
import com.clyze.intellijplugin.Poster
import com.clyze.intellijplugin.services.ClyzeProjectService
import com.clyze.intellijplugin.state.AnalysisRun
import com.clyze.intellijplugin.ui.UIHelper.setMaximumHeight
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.CheckBox
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.reverse
import com.jetbrains.rd.util.first
import java.awt.*
import java.net.URI
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

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

        fun reportError(msg :String) {
            JOptionPane.showMessageDialog(
                JFrame(),
                msg,
                "Server error",
                JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun updateWithSorted(component : ComboBox<String>, items : MutableList<String>) {
        component.removeAllItems()
        items.sort()
        items.forEach { component.addItem(it) }
    }

    private fun addSortedNodeChildren(
        treeModel: DefaultTreeModel, parent: LabelNode,
        items: MutableList<String>
    ) {
        items.sort()
        var nodeIndex = 0
        items.forEach { name ->
            println("Adding node for item: $name")
            val nameNode = LabelNode(name)
            treeModel.insertNodeInto(nameNode, parent, nodeIndex++)
            parent.addChild(nameNode)
        }
    }

    /**
     * Returns the selected path ("project, snapshot, analysis") in the code structure tree.
     * @param tree     the code structure tree component
     * @return         the (project, snapshot, analysis) triple, where some components may be null
     */
    private fun getSelectedPath(tree : Tree) : Triple<String?, String?, String?> {
        val selectionPath = tree.selectionPath
        if (selectionPath == null || selectionPath.pathCount == 0) {
            println("No selection in tree.")
            return Triple(null, null, null)
        }
        val path = selectionPath.path as Array<*>
        @Suppress("ReplaceSizeCheckWithIsNotEmpty")
        val p1 = if (path.size > 1) path[1] else null
        val p2 = if (path.size > 2) path[2] else null
        val p3 = if (path.size > 3) path[3] else null
        val projectName = if (p1 is LabelNode) p1.label else null
        val snapshotName = if (p2 is LabelNode) p2.label else null
        val analysisName = if (p3 is LabelNode) p3.label else null
        return Triple(projectName, snapshotName, analysisName)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.PAGE_AXIS)

        // Panel: Code Snapshot Structure.
        val codePanel = JPanel()
        codePanel.border = BorderFactory.createTitledBorder("Code Snapshot Structure")
        codePanel.layout = BorderLayout()
        // Top: server-sync button.
        val connectBtn = JButton("Sync With Server")
        connectBtn.alignmentX = Component.CENTER_ALIGNMENT
        codePanel.add(connectBtn, BorderLayout.NORTH)
        // Center: code structure tree.
        val codeStructure = HashMap<String, LabelNode>()
        val treeRoot = LabelNode("Root")
        val treeModel = DefaultTreeModel(treeRoot)
        val tree = Tree(treeModel)
        tree.isRootVisible = false
        tree.minimumSize = Dimension(400, 400)
        tree.selectionPath = TreePath(arrayOf(treeRoot))
        codePanel.add(tree, BorderLayout.CENTER)
        // Bottom: "open in browser" button.
        val gotoWebBtn = JButton("Open in Browser")
        gotoWebBtn.alignmentX = Component.CENTER_ALIGNMENT
        codePanel.add(gotoWebBtn, BorderLayout.SOUTH)
        mainPanel.add(codePanel)

        // Panel: Actions.
        val actionsPanel = JPanel()
        actionsPanel.layout = BoxLayout(actionsPanel, BoxLayout.PAGE_AXIS)
        actionsPanel.border = BorderFactory.createTitledBorder("Actions")
        // "Post" action.
        val postPanel = JPanel()
        postPanel.layout = BoxLayout(postPanel, BoxLayout.LINE_AXIS)
        val postBtn = JButton("Post Code Snapshot")
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
        val startAnalysisBtn = JButton("Analyze...")
        analyzePanel.add(startAnalysisBtn)
        val analysisTypes = ComboBox<String>()
        analyzePanel.add(JScrollPane(analysisTypes))
        actionsPanel.add(analyzePanel)
        setMaximumHeight(actionsPanel, 120)
        mainPanel.add(actionsPanel)

        val contentManager = toolWindow.contentManager
        val projectService = project.getService(ClyzeProjectService::class.java)
        val config = projectService.config

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
        val appOnly = CheckBox("App-only", false)
        datasetOptionsPanel.add(appOnly)
        val refreshDatasetBtn = JButton("Refresh")
        datasetOptionsPanel.add(refreshDatasetBtn)
        analysisPanel.add(datasetOptionsPanel)

        val datasetResults = JBTable()
        analysisPanel.add(JScrollPane(datasetResults))

        fun showAnalysisTypes(projectName : String) {
            performServerAction(projectService) { remote ->
                val projectAnalyses = remote.getProjectAnalyses(config.getUser(), projectName)
                analysisTypes.removeAllItems()
                processResults(projectAnalyses) {
                    val name = it["displayName"]
                    if (name is String) {
                        analysisTypes.addItem(name)
                        val profile = it["id"]
                        println("Project analysis profile: $profile")
                        if (profile is String) {
                            projectService.analysisTypeProfiles[name] = profile
                            projectService.analysisProfileDescriptor[profile] = it
                        }
                    }
                }
            }
        }

        fun expandTree() {
            for (i in 0..tree.rowCount) {
                tree.expandRow(i)
            }
            treeModel.reload()
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

        fun updateAnalyses(projectName : String?, snapshotName : String?, analysisName : String?) {
            if (projectName == null || snapshotName == null || analysisName == null) {
                println("Null project/snapshot/analysis!")
                return
            }
            val profile = getAnalysisInfo(projectName, snapshotName, analysisName) { projectService.analysisProfiles }
            println("Found analysis profile: $profile")
            val analysisInfo = projectService.analysisProfileDescriptor[profile]
            println("Found analysis info: $analysisInfo")
            val outputs = analysisInfo?.get("outputs")
            val datasetItems : MutableList<String> = ArrayList()
            if (outputs != null && outputs is List<*>) {
                println("Processing outputs...")
                for (output in outputs) {
                    println("Processing: " + output.toString())
                    if (output is Map<*, *>) {
                        val outputId = output["id"]
                        println("outputId: $outputId")
                        if (outputId != null && outputId is String)
                            datasetItems.add(outputId)
                    }
                }
                updateWithSorted(dataset, datasetItems)

                if (dataset.itemCount > 0)
                    dataset.selectedItem = dataset.getItemAt(0)
            }
        }

        fun updateSnapshotAnalyses(projectName : String, snapshotName : String) {
            val user = config.getUser()
            performServerAction(projectService) { remote ->
                val analysisInfo: Map<String, Any?>?
                try {
                    analysisInfo = remote.getConfiguration(user, projectName, snapshotName, CLYZE_CONFIG)
                } catch (ex : Exception) {
                    println("WARNING: no snapshots found in project $projectName.")
                    return@performServerAction
                }
                println("Analysis information: $analysisInfo")
                val analysisNames = ArrayList<String>()
                if (analysisInfo is Map<*, *>) {
                    val analysesInfo = analysisInfo["analyses"]
                    if (analysesInfo is List<*>) {
                        analysesInfo.forEach {
                            if (it is Map<*, *>) {
                                val analysisName = it["displayName"]
                                println("Found analysis: $analysisName")
                                if (analysisName != null && analysisName is String) {
                                    analysisNames.add(analysisName)
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
                    }
                }
                val snapshotNode = codeStructure[projectName]?.getChildByLabel(snapshotName)
                if (snapshotNode == null) {
                    println("Internal error: no node for $projectName/$snapshotName")
                    return@performServerAction
                }
                addSortedNodeChildren(treeModel, snapshotNode, analysisNames)
            }
        }

        fun updateProjectSnapshots(projectName : String) {
            val user = config.getUser()
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
                showAnalysisTypes(projectName)

                val projectNode = codeStructure[projectName]
                if (projectNode == null) {
                    println("Internal error: no project node for $projectName")
                    return@performServerAction
                }

                addSortedNodeChildren(treeModel, projectNode, snapshotItems)
                snapshotItems.forEach { snapshotName -> updateSnapshotAnalyses(projectName, snapshotName) }
            }
        }

        // Sync the current code structure tree with the server.
        fun syncServer() {
            val user = config.getUser()
            val token = config.getToken()
            val remotePath = config.getServer()
            if (user == "") {
                reportError("No user found, open Project Settings to diagnose.")
                return
            } else if (token == "") {
                reportError("No API key / password found, open Project Settings to diagnose.")
                return
            } else if (remotePath == "") {
                reportError("No server configured, open Project Settings to diagnose.")
                return
            }
            performServerAction(projectService) { remote ->
                val projectsResp = remote.listProjects(user)
                println(projectsResp)
                var currentProjectNode : LabelNode? = null
                processResults(projectsResp) {
                    val name = it["name"]
                    println("Found project: $name")
                    if (name is String) {
                        val projectNode = LabelNode(name)
                        codeStructure[name] = projectNode
                        if (project.name == name)
                            currentProjectNode = projectNode
                    }
                }

                // Update code structure tree.
                treeRoot.removeAllChildren()
                codeStructure.values.forEach { projectNode ->
                    treeModel.insertNodeInto(projectNode, treeRoot, 0)
                }
                // Expand the (just constructed) first level of the tree.
                expandTree()
                codeStructure.values.forEach { projectNode ->
                    updateProjectSnapshots(projectNode.label)
                }
                // Select current project in the tree.
                if (currentProjectNode != null)
                    tree.selectionPath = TreePath(arrayOf(treeRoot, currentProjectNode ))
            }
        }

        fun refreshDataset() {
            val (projectName, snapshotName, analysisName) = getSelectedPath(tree)
            val output = dataset.item
            if (projectName == null) {
                println("Null project!")
                return
            } else if (snapshotName == null) {
                println("Null snapshot!")
                return
            } else if (analysisName == null) {
                println("Null analysis!")
                return
            } else if (output == null) {
                println("Null output!")
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
                                config.getUser(),
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

        // When a tree node is selected, update project/snapshot/analysis state.
        tree.addTreeSelectionListener {
            val (projectName, snapshotName, analysisName) = getSelectedPath(tree)
            projectService.setProjectName(projectName)
            projectService.setSnapshot(snapshotName)
            if (analysisName != null)
                updateAnalyses(projectName, snapshotName, analysisName)
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
            AnalysisPanel(analysisProfileInfo) { syncServer() }.showUI(projectService)
        }

        mainPanel.focusTraversalPolicy = object : FocusTraversalPolicy() {
            val transitions : Map<Component, Component> = mapOf(
                Pair(connectBtn, tree),
                Pair(tree, gotoWebBtn),
                Pair(gotoWebBtn, postBtn),
                Pair(postBtn, postMethodCombo),
                Pair(postMethodCombo, startAnalysisBtn),
                Pair(startAnalysisBtn, connectBtn)
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