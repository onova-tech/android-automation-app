package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.parser.Selector

class ByTextStrategy : SelectorStrategy {
    override val name: String = "text"

    override fun find(root: AccessibilityNodeInfo?, selector: Selector): List<AccessibilityNodeInfo> {
        val text = (selector as? Selector.ByText)?.text ?: return emptyList()
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return results

        // Iterative traversal to avoid stack overflow on deep trees
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.text?.toString()?.equals(text, ignoreCase = true) == true) {
                results.add(current)
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return results
    }

    override fun matches(node: AccessibilityNodeInfo, selector: Selector): Boolean {
        val text = (selector as? Selector.ByText)?.text ?: return false
        val nodeText = node.text?.toString() ?: return false
        // Case-insensitive matching
        return nodeText.equals(text, ignoreCase = true)
    }
}
