package com.proj.automation.accessibility

import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Bridge interface between the execution engine and the Android Accessibility API.
 * Provides tree access, element actions, and global navigation.
 */
interface AutomationBridge {
    /** Get the root of the accessibility tree for the active window */
    fun getRootNode(): AccessibilityNodeInfo?

    /** Click a node */
    fun click(node: AccessibilityNodeInfo): Boolean

    /** Long-click a node */
    fun longClick(node: AccessibilityNodeInfo): Boolean

    /** Set text on an editable node */
    fun setText(node: AccessibilityNodeInfo, text: String): Boolean

    /** Scroll a scrollable node forward */
    fun scrollForward(node: AccessibilityNodeInfo): Boolean

    /** Scroll a scrollable node backward */
    fun scrollBackward(node: AccessibilityNodeInfo): Boolean

    /** Navigate back */
    fun goBack(): Boolean

    /** Navigate to home */
    fun goHome(): Boolean

    /** Start an activity via intent */
    fun startActivity(intent: Intent)

    companion object {
        @Volatile
        var instance: AutomationBridge? = null
            private set

        fun init(bridge: AutomationBridge) {
            instance = bridge
        }

        fun get(): AutomationBridge {
            return instance
                ?: throw IllegalStateException(
                    "AutomationBridge not initialized. " +
                    "Enable Accessibility Service in Settings first."
                )
        }
    }
}
