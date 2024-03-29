package com.clyze.intellijplugin.ui

import com.clyze.intellijplugin.Helper.CLYZE_CONFIG
import com.clyze.intellijplugin.Helper.performServerAction
import com.clyze.intellijplugin.Poster
import com.clyze.intellijplugin.services.ClyzeProjectService
import com.clyze.intellijplugin.state.AnalysisRun
import com.clyze.intellijplugin.ui.UIHelper.setMaximumHeight
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.CheckBox
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.reverse
import com.jetbrains.rd.util.first
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
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
class ClyzeToolWindowFactory : ToolWindowFactory, DumbAware {
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
         * @param tree     the code structure tree (UI component)
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
    }

    /** The number of posts that have not yet terminated. */
    private var currentlyPosting : AtomicInteger = AtomicInteger(0)

    /**
     * Update the "analysis types" drop-down menu with the analyses supported
     * by this project.
     * @param projectService   the Clyze plugin service object
     * @param projectName      the name of the project
     * @param analysisTypes    the UI component to update
     */
    private fun showAnalysisTypes(projectService : ClyzeProjectService,
                                  projectName : String, analysisTypes : ComboBox<String>) {
        performServerAction(projectService) { remote ->
            val projectAnalyses = remote.getProjectAnalyses(projectService.config.getUser(), projectName)
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

    /**
     * Expand the code structure tree.
     * @param tree        the tree to expand
     * @param treeModel   the model of the tree (supporting {@code repaint()})
     */
    private fun expandTree(tree : Tree, treeModel : DefaultTreeModel) {
        for (i in 0..tree.rowCount) {
            tree.expandRow(i)
        }
        treeModel.reload()
    }

    private fun getAnalysisInfo(projectName : String, snapshotName : String, analysisName : String,
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

    /**
     * Updates the dataset drop-down for a given analysis.
     * @param projectService   the Clyze plugin service object
     * @param dataset          the UI component to update
     * @param projectName      the name of the project
     * @param snapshotName     the name of the snapshot
     * @param analysisName     the name of the analysis
     */
    private fun updateAnalysisDatasets(projectService : ClyzeProjectService, dataset : ComboBox<String>,
                                       projectName : String?, snapshotName : String?, analysisName : String?) {
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

    /**
     * Update the code structure tree to show the analyses for a given project/snapshot.
     * @param projectService   the Clyze plugin service object
     * @param codeStructure    the code structure object being visualized in the tree
     * @param treeModel        the model of the code structure tree
     * @param projectName      the name of the project
     * @param snapshotName     the name of the snapshot
     */
    private fun updateSnapshotAnalyses(projectService : ClyzeProjectService, codeStructure : Map<String, LabelNode>,
                                       treeModel : DefaultTreeModel, projectName : String, snapshotName : String) {
        val user = projectService.config.getUser()
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

    /**
     * Update the code structure tree to show the snapshots and the analysis
     * types supported by a project.
     * @param projectService   the Clyze plugin service object
     * @param codeStructure    the code structure object being visualized in the tree
     * @param treeModel        the model of the code structure tree
     * @param analysisTypes    the drop-down (containg the analysis types) to update
     * @param projectName      the name of the project
     */
    private fun updateProjectInfo(projectService : ClyzeProjectService,
                                  codeStructure : Map<String, LabelNode>,
                                  treeModel : DefaultTreeModel,
                                  analysisTypes : ComboBox<String>,
                                  projectName : String) {
        val user = projectService.config.getUser()
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
            showAnalysisTypes(projectService, projectName, analysisTypes)

            val projectNode = codeStructure[projectName]
            if (projectNode == null) {
                println("Internal error: no project node for $projectName")
                return@performServerAction
            }

            addSortedNodeChildren(treeModel, projectNode, snapshotItems)
            snapshotItems.forEach { snapshotName -> updateSnapshotAnalyses(projectService, codeStructure, treeModel, projectName, snapshotName) }
        }
    }

    /**
     * Sync the current code structure tree with the server.
     * @param project          the current IDE project
     * @param projectService   the Clyze plugin service object
     * @param tree             the code structure tree (UI component)
     * @param treeModel        the model of the code structure tree
     * @param codeStructure    the code structure object being visualized in the tree
     * @param analysisTypes    the drop-down (containg the analysis types) to update
     */
    private fun syncServer(project : Project,
                           projectService : ClyzeProjectService,
                           tree : Tree,
                           treeModel : DefaultTreeModel,
                           treeRoot : LabelNode,
                           codeStructure : HashMap<String, LabelNode>,
                           analysisTypes : ComboBox<String>) {
        val config = projectService.config
        val user = config.getUser()
        val token = config.getToken()
        val remotePath = config.getServer()
        if (user == "") {
            UIHelper.reportError("No user found, open Project Settings to diagnose.")
            return
        } else if (token == "") {
            UIHelper.reportError("No API key / password found, open Project Settings to diagnose.")
            return
        } else if (remotePath == "") {
            UIHelper.reportError("No server configured, open Project Settings to diagnose.")
            return
        }
        performServerAction(projectService) { remote ->
            val projectsResp = remote.listProjects(user)
            println(projectsResp)
            codeStructure.clear()
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
            expandTree(tree, treeModel)
            codeStructure.values.forEach { projectNode ->
                updateProjectInfo(projectService, codeStructure, treeModel, analysisTypes, projectNode.label)
            }
            // Select current project in the tree.
            if (currentProjectNode != null)
                tree.selectionPath = TreePath(arrayOf(treeRoot, currentProjectNode ))
        }
    }

    private fun refreshDataset(projectService : ClyzeProjectService,
                               tree : Tree,
                               dataset : ComboBox<String>,
                               start : JSpinner,
                               count : JSpinner,
                               appOnly : JCheckBox,
                               datasetResults : JBTable) {
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
                            projectService.config.getUser(),
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

    /**
     * Opens a file and takes the user to a given position.
     * @param project   the current IDE project
     * @param filename  the file name to open (should be a local path, relative to the project)
     * @param line      the source line (1-based)
     * @param column    the source column (1-based)
     * @return          if false, the operation failed
     */
    private fun openFilePosition(project : Project, filename : String, line : String, column : String) : Boolean {
        val projectFilePath = project.projectFilePath
        if (projectFilePath == null) {
            println("ERROR: project file path not available.")
            return false
        }
        val projectDir = File(projectFilePath).parentFile?.parentFile
        val filePath = File(projectDir, filename).path
        val file = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (file == null) {
            println("ERROR: could not find file $filename (resolved as $filePath)")
            return false
        }
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFile(file, true)
        val editor = fileEditorManager.selectedTextEditor
        if (editor == null) {
            println("ERROR: could not find editor object.")
            return false
        }
        try {
            val pos = LogicalPosition(Integer.valueOf(line) - 1, Integer.valueOf(column) - 1)
            editor.caretModel.moveToLogicalPosition(pos)
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        } catch (_: NumberFormatException) {
            println("ERROR: bad line/column: $line/$column")
            return false
        }

        return true
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
        val appOnly = CheckBox("App-only", false, "Show only results about the application code.")
        datasetOptionsPanel.add(appOnly)
        val refreshDatasetBtn = JButton("Refresh")
        datasetOptionsPanel.add(refreshDatasetBtn)
        analysisPanel.add(datasetOptionsPanel)

        val datasetResults = JBTable()
        analysisPanel.add(JScrollPane(datasetResults))

        val sync : () -> Unit = { syncServer(project, projectService, tree, treeModel, treeRoot, codeStructure, analysisTypes) }
        val datasetRefresh : () -> Unit = { refreshDataset(projectService, tree, dataset, start, count, appOnly, datasetResults) }


        dataset.addActionListener {
            datasetRefresh()
        }

        refreshDatasetBtn.addActionListener {
            datasetRefresh()
        }

        connectBtn.addActionListener {
            sync()
        }

        postBtn.addActionListener {
            when (postMethodCombo.selectedItem) {
                GRADLE -> { println("Gradle integration is currently not supported via the UI.")  }
                CLI -> {
                    println("base path: " + project.basePath)
                    postStatus.text = "Posting..."
                    currentlyPosting.incrementAndGet()
                    Thread {
                        Poster().postWithCLI(project, config)
                        if (currentlyPosting.decrementAndGet() == 0)
                            postStatus.text = "Done."
                        sync()
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
                updateAnalysisDatasets(projectService, dataset, projectName, snapshotName, analysisName)
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
            AnalysisPanel(analysisProfileInfo) { sync() }.showUI(projectService)
        }

        // Handle clicks on output dataset cells.
        datasetResults.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                if (e == null)
                    return
                val row = datasetResults.rowAtPoint(e.point)
                val col = datasetResults.columnAtPoint(e.point)
                val cellValue = datasetResults.model.getValueAt(row, col)
                if (cellValue is String) {
                    val parts = cellValue.split(":")
                    if (parts.size != 3) {
                        println("Cell value is not supported: $parts")
                        return
                    }
                    val filename = parts[0]
                    val line = parts[1]
                    val column = parts[2]
                    // Try both "filename" and "src/filename", since different ways of
                    // uploading snapshots to the server (CLI, Gradle plugin, crawler)
                    // may follow different source structure conventions.
                    if (!openFilePosition(project, filename, line, column))
                        openFilePosition(project, "src/$filename", line, column)
                }
            }
        })

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