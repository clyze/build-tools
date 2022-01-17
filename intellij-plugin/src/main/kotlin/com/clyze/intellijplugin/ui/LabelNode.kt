package com.clyze.intellijplugin.ui

import javax.swing.tree.DefaultMutableTreeNode

/**
 * A node with a label in the code structure tree. Supports fast
 * look up children by label.
 */
class LabelNode(val label: String, private var childrenByLabel: MutableMap<String, LabelNode> = HashMap()) : DefaultMutableTreeNode(label) {
    /**
     * Add a child node (should also be a label node).
     * @param childNode   the child (label) node to add to this node
     */
    fun addChild(childNode: LabelNode) {
        super.add(childNode)
        this.childrenByLabel[childNode.label] = childNode
    }

    /**
     * Finds a node with the given label that is a child of this node.
     * @param label   the search label
     * @return        the child (label) node with the selected label or null on failure
     */
    fun getChildByLabel(label : String) : LabelNode? {
        return this.childrenByLabel[label]
    }
}
