package com.proj.automation.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.service.EventBus
import com.proj.automation.service.StateManager
import java.util.concurrent.atomic.AtomicReference

class AutomationService : AccessibilityService(), AutomationBridge {

    companion object {
        const val TAG = "AutomationService"

        @Volatile
        private var _instance: AutomationService? = null
        val instance: AutomationService
            get() = _instance
                ?: throw IllegalStateException(
                    "AutomationService not connected. " +
                    "Enable Accessibility Service in Settings first."
                )
    }

    private val rootNodeRef = AtomicReference<AccessibilityNodeInfo?>(null)
    private var _connected = false

    // State Manager reference (initialized in onServiceConnected)
    @Suppress("unused")
    private var stateManager: com.proj.automation.service.StateManager? = null

    // Event Bus reference (initialized in onServiceConnected)
    @Suppress("unused")
    private var eventBus: com.proj.automation.service.EventBus? = null

    override fun onCreate() {
        super.onCreate()
        _instance = this
        AutomationBridge.init(this)
    }

    override fun onServiceConnected() {
        _connected = true
        android.util.Log.i(TAG, "AutomationService connected — ready for automation")

        // Use the process-wide bus so the editor UI receives this service's events
        stateManager = StateManager()
        eventBus = EventBus.default

        // Wire EventBus to publish events from this service
        stateManager?.setEventBus(eventBus)

        // Publish connection event
        eventBus?.publish(com.proj.automation.service.EventBus.Event.ServiceConnected)
    }

    override fun onInterrupt() {
        // No-op for this POC.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        android.util.Log.i(TAG, "AutomationService unbinding")
        _connected = false
        eventBus?.publish(com.proj.automation.service.EventBus.Event.ServiceDisconnected("User disabled service"))
        return super.onUnbind(intent)
    }

    // ——— AccessibilityEvent Handling ———

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                val className = event.className?.toString() ?: ""
                stateManager?.onWindowStateChanged(packageName, className)
                eventBus?.publish(
                    com.proj.automation.service.EventBus.Event.WindowChanged(packageName, className)
                )
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val root = getRootNode()
                stateManager?.onContentChanged(root)
                eventBus?.publish(
                    com.proj.automation.service.EventBus.Event.AccessibilityTreeChanged(root)
                )
            }

            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                detectDialogOrToast(event)
            }
        }
    }

    private fun detectDialogOrToast(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: return
        if (className.contains("Dialog", ignoreCase = true)) {
            eventBus?.publish(com.proj.automation.service.EventBus.Event.DialogDetected(className))
        }
        if (className.contains("Toast", ignoreCase = true)) {
            val text = event.text?.firstOrNull()?.toString() ?: ""
            if (text.isNotBlank()) {
                eventBus?.publish(com.proj.automation.service.EventBus.Event.ToastDetected(text))
            }
        }
    }

    // ——— AutomationBridge Implementation ———

    override fun getRootNode(): AccessibilityNodeInfo? {
        val node = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            rootInActiveWindow
        } else {
            null
        }
        rootNodeRef.set(node)
        return node
    }

    override fun click(node: AccessibilityNodeInfo): Boolean {
        return try {
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            // Recycle after use to prevent memory leaks
            // Note: callers are responsible for recycling nodes they own
            success
        } catch (e: Exception) {
            android.util.Log.w(TAG, "click failed: ${e.message}")
            false
        }
    }

    override fun longClick(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    override fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "setText failed: ${e.message}")
            false
        }
    }

    override fun scrollForward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    override fun scrollBackward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    override fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun startActivity(intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            super.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "startActivity failed: ${e.message}")
        }
    }
}
