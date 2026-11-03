package com.github.kornilova203.matlab.execution.console

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTable

/**
 * The session's workspace, like MATLAB's Workspace panel: name, value, size and class of each variable,
 * refreshed after each command. Double-click a variable to show it in full in the console.
 */
class MatlabVariablesView(private val session: MatlabConsoleSession) : JPanel(BorderLayout()) {
    val model = ListTableModel<MatlabVariable>(
        column("Name") { it.name },
        column("Value") { it.value },
        column("Size") { it.size },
        column("Class") { it.className },
    )
    val table = JBTable(model)

    init {
        table.emptyText.text = "No variables"
        table.setShowGrid(false)
        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        table.columnModel.getColumn(0).cellRenderer = NameRenderer()
        add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val row = table.selectedRow.takeIf { it >= 0 } ?: return false
                val variable = model.getItem(table.convertRowIndexToModel(row)) ?: return false
                if (!session.isAlive) return false
                session.send(variable.name)
                return true
            }
        }.installOn(table)
        session.workspace.addListener {
            ApplicationManager.getApplication().invokeLater({ show(session.workspace.variables) }, ModalityState.any())
        }
        show(session.workspace.variables)
    }

    private fun show(variables: List<MatlabVariable>) {
        val selected = table.selectedRow.takeIf { it >= 0 }?.let { model.getItem(table.convertRowIndexToModel(it))?.name }
        model.items = variables
        val index = variables.indexOfFirst { it.name == selected }
        if (index >= 0) table.selectionModel.setSelectionInterval(index, index)
    }

    private class NameRenderer : ColoredTableCellRenderer() {
        override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
            icon = AllIcons.Nodes.Variable
            append(value?.toString() ?: "", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
    }

    private companion object {
        fun column(title: String, value: (MatlabVariable) -> String) = object : ColumnInfo<MatlabVariable, String>(title) {
            override fun valueOf(item: MatlabVariable): String = value(item)
        }
    }
}
