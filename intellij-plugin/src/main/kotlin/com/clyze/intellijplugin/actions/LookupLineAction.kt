package com.clyze.intellijplugin.actions

import com.clyze.intellijplugin.services.ClyzeProjectService
import com.clyze.intellijplugin.Helper
import com.clyze.intellijplugin.Helper.CLYZE_CONFIG
import com.clyze.intellijplugin.ui.LineResult
import com.clyze.intellijplugin.ui.MyToolWindowFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import kotlin.math.min

/**
 * An action that queries the server for interesting data
 * about the current source code line.
 */
class LookupLineAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            println("Null project!")
            return
        }
        val feManager = FileEditorManager.getInstance(project)
        val editor = feManager.selectedTextEditor ?: return

        val caretModel = editor.caretModel
        println("position: " + caretModel.logicalPosition)

        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        project.projectFilePath

        val canonicalPath = file.containingDirectory.virtualFile.canonicalPath
        val commonPrefix = getCommonPrefix(project.projectFilePath, canonicalPath)
        val codeFile = canonicalPath?.substring(commonPrefix.length + "/src/".length) + "/" + file.name
        println("code file: $codeFile")

        val projectService = project.getService(ClyzeProjectService::class.java)
        val config = projectService.config
        Helper.performServerAction(projectService) { remote ->
            val symbols = remote.getSymbols(
                config.user,
                config.projectName,
                config.snapshotName,
                CLYZE_CONFIG,
                codeFile,
                (caretModel.logicalPosition.line + 1).toString()
            )
            println("symbols: $symbols")

            val tableTriple = projectService.lineResults
            if (tableTriple == null) {
                println("Null table information!")
                return@performServerAction
            }
            val (table, tableModel, tabFocus) = tableTriple
            val lineResults = ArrayList<LineResult>()
            MyToolWindowFactory.processResults(symbols) {
                val symbolId = it["symbolId"] ?: (if (it["analysisId"] != null) "Analysis result" else "")
                val type = it["resultType"] ?: ""
                val desc = it["message"] ?: ""
                if (symbolId is String && type is String && desc is String)
                    lineResults.add(LineResult(symbolId, type, desc))
                else
                    println("Bad line result data: $it")
            }
            tableModel.update(table, lineResults)
            tabFocus()
        }
    }

    private fun getCommonPrefix(s1 : String?, s2 : String?) : String {
        if (s1 == null || s2 == null)
            return ""
        val maxPrefixLen = min(s1.length, s2.length)
        var lastEqualIdx = 0
        for (i in 0 until maxPrefixLen)
            if (s1[i] == s2[i])
                lastEqualIdx = i
            else
                break
        return s1.substring(0, lastEqualIdx)
    }
}
