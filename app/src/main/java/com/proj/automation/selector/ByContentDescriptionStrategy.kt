package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.parser.Selector

class ByContentDescriptionStrategy : SelectorStrategy {
    override val name: String = "content_description"

    override fun find(root: AccessibilityNodeInfo?, selector: Selector): List<AccessibilityNodeInfo> {
        val description = (selector as? Selector.ByContentDescription)?.description ?: return emptyList()
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return results

        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.contentDescription?.toString() == description) {
                results.add(current)
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return results
    }

    override fun matches(node: AccessibilityNodeInfo, selector: Selector): Boolean {
        val description = (selector as? Selector.ByContentDescription)?.description ?: return false
        return node.contentDescription?.toString() == description
    }
}
