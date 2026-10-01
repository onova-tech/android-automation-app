package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.parser.Selector

class ByClassNameStrategy : SelectorStrategy {
    override val name: String = "class_name"

    override fun find(root: AccessibilityNodeInfo?, selector: Selector): List<AccessibilityNodeInfo> {
        val classSelector = selector as? Selector.ByClassName ?: return emptyList()
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return results

        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.className?.toString()?.equals(classSelector.className, ignoreCase = true) == true) {
                results.add(current)
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { stack.addLast(it) }
            }
        }

        // Apply index filter if specified
        return if (classSelector.index != null && classSelector.index in results.indices) {
            listOf(results[classSelector.index])
        } else {
            results
        }
    }

    override fun matches(node: AccessibilityNodeInfo, selector: Selector): Boolean {
        val classSelector = selector as? Selector.ByClassName ?: return false
        return node.className?.toString()?.equals(classSelector.className, ignoreCase = true) == true
    }
}
