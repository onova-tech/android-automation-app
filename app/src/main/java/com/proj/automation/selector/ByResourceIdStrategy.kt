package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.parser.Selector

class ByResourceIdStrategy : SelectorStrategy {
    override val name: String = "resource_id"

    override fun find(root: AccessibilityNodeInfo?, selector: Selector): List<AccessibilityNodeInfo> {
        val resourceId = (selector as? Selector.ByResourceId)?.resourceId ?: return emptyList()
        return root?.findAccessibilityNodeInfosByViewId(resourceId) ?: emptyList()
    }

    override fun matches(node: AccessibilityNodeInfo, selector: Selector): Boolean {
        val id = (selector as? Selector.ByResourceId)?.resourceId ?: return false
        return node.viewIdResourceName == id
    }
}
