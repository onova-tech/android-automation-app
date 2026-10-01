package com.proj.automation.engine.actions

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.engine.ActionContext
import com.proj.automation.ui.UiNode

/** Shared scrolling for list actions: scrolls the largest scrollable container on screen. */
internal object Scrolling {

    /** Time for the list to settle after a scroll before the next snapshot */
    const val SETTLE_MS = 400L

    /** @return false when there is nothing to scroll or the scroll was refused */
    fun scrollOnce(context: ActionContext, screen: UiNode?, forward: Boolean): Boolean {
        val container = screen?.walk()
            ?.filter { it.scrollable && it.ref is AccessibilityNodeInfo }
            ?.maxByOrNull { (it.bounds.right - it.bounds.left).toLong() * (it.bounds.bottom - it.bounds.top) }
            ?: return false
        val node = container.ref as AccessibilityNodeInfo
        return if (forward) context.automation.scrollForward(node) else context.automation.scrollBackward(node)
    }

    fun isForward(direction: String?): Boolean = when (direction?.lowercase()) {
        null, "", "forward", "down", "right" -> true
        "backward", "back", "up", "left" -> false
        else -> throw IllegalArgumentException("Unknown direction '$direction'")
    }
}
