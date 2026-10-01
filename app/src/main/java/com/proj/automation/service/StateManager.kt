package com.proj.automation.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable snapshot of the current UI state, captured by StateManager on each
 * accessibility event. Lets selectors and debugging reason about the active window
 * without holding a live reference to the accessibility tree.
 */
data class AppState(
    val timestamp: Long,
    val activePackage: String? = null,
    val activeActivity: String? = null,
    val rootNode: AccessibilityNodeInfo? = null,
    val lastEvent: AccessibilityEvent? = null,
)

/**
 * Maintains the current UI state snapshot for selectors and debugging.
 * Captures the accessibility tree root, active package, and activity on each event.
 */
class StateManager {

    private val _eventBus = AtomicReference<EventBus?>(null)

    fun setEventBus(bus: EventBus?) {
        _eventBus.set(bus)
    }

    private val stateRef = AtomicReference<AppState>(
        AppState(timestamp = System.currentTimeMillis())
    )

    /** Take a snapshot of the current state */
    fun takeSnapshot(): AppState = stateRef.get()

    /** Called when the active window state changes (activity switched) */
    fun onWindowStateChanged(packageName: String, className: String) {
        val newState = AppState(
            timestamp = System.currentTimeMillis(),
            activePackage = packageName,
            activeActivity = className,
            lastEvent = null
        )
        stateRef.set(newState)
    }

    /** Called when the content of the active window changes */
    fun onContentChanged(rootNode: AccessibilityNodeInfo?) {
        val current = stateRef.get()
        val newState = AppState(
            timestamp = System.currentTimeMillis(),
            activePackage = current.activePackage,
            activeActivity = current.activeActivity,
            rootNode = rootNode,
            lastEvent = null
        )
        stateRef.set(newState)
    }

    /** Wait for a condition to become true within the timeout */
    fun waitForCondition(
        predicate: (AppState) -> Boolean,
        timeoutMs: Long
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(stateRef.get())) {
                return true
            }
            Thread.sleep(100)
        }
        return false
    }
}
