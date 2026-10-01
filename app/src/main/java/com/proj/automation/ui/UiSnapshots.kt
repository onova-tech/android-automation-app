package com.proj.automation.ui

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Builds [UiNode] snapshots from the live accessibility tree. XML fixtures: see [UiXml]. */
object UiSnapshots {

    private const val MAX_NODES = 5_000

    fun fromAccessibility(root: AccessibilityNodeInfo): UiNode {
        var count = 0
        fun convert(node: AccessibilityNodeInfo): UiNode {
            count++
            val rect = Rect().also { node.getBoundsInScreen(it) }
            val children = (0 until node.childCount).mapNotNull { i ->
                if (count >= MAX_NODES) null else node.getChild(i)?.let { convert(it) }
            }
            return UiNode(
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                resourceId = node.viewIdResourceName,
                className = node.className?.toString(),
                packageName = node.packageName?.toString(),
                clickable = node.isClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                focused = node.isFocused,
                bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                children = children,
                ref = node
            )
        }
        return convert(root)
    }
}
