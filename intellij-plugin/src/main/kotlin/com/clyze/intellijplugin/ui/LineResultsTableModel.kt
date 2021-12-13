package com.clyze.intellijplugin.ui

import com.intellij.ui.table.JBTable
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.TableModel

class LineResultsTableModel(
    private val lineResultsData : MutableList<LineResult>,
    private val listeners : MutableList<TableModelListener> = ArrayList()) : TableModel {
    override fun getRowCount(): Int {
        return lineResultsData.size
    }

    override fun getColumnCount(): Int {
        return 3
    }

    override fun getColumnName(p0: Int): String {
        when (p0) {
            0 -> return "Symbol"
            1 -> return "Type"
            2 -> return "Description"
        }
        return ""
    }

    override fun getColumnClass(p0: Int): Class<*> {
        return String::class.java
    }

    override fun isCellEditable(p0: Int, p1: Int): Boolean {
        return false
    }

    override fun getValueAt(p0: Int, p1: Int): Any {
        val lineResult = lineResultsData[p0]
        when (p1) {
            0 -> return lineResult.symbolId
            1 -> return lineResult.type
            2 -> return lineResult.description
        }
        return ""
    }

    override fun setValueAt(p0: Any?, p1: Int, p2: Int) {
//        TODO("Not yet implemented")
    }

    override fun addTableModelListener(p0: TableModelListener?) {
        if (p0 != null)
            listeners.add(p0)
    }

    override fun removeTableModelListener(p0: TableModelListener?) {
        if (p0 != null)
            listeners.remove(p0)
    }

    fun update(table : JBTable, newLineResults : Collection<LineResult>) {
        lineResultsData.clear()
        lineResultsData.addAll(newLineResults)
        table.repaint()
        listeners.forEach {
            it.tableChanged(TableModelEvent(this))
        }
    }
}