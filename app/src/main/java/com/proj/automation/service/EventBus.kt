package com.proj.automation.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Typed event bus for decoupling Accessibility Service events from the Execution Engine.
 * Supports publish/subscribe pattern for UI events, dialog detection, and navigation changes.
 */
class EventBus {

    sealed class Event {
        /** Window/activity changed */
        data class WindowChanged(val packageName: String, val activity: String) : Event()

        /** Dialog detected on screen */
        data class DialogDetected(val dialogText: String) : Event()

        /** Toast message detected */
        data class ToastDetected(val text: String) : Event()

        /** Accessibility tree changed (root node updated) */
        data class AccessibilityTreeChanged(val rootNode: AccessibilityNodeInfo?) : Event()

        /** Accessibility Service disconnected */
        data class ServiceDisconnected(val reason: String) : Event()

        /** Service connected */
        object ServiceConnected : Event()

        /** Step completed with result */
        data class StepCompleted(val stepIndex: Int, val success: Boolean, val action: String, val errorMessage: String? = null) : Event()

        /** Log message from a step */
        data class LogMessage(val message: String) : Event()
    }

    /** Subscription handle for unsubscribing */
    class Subscription internal constructor(
        internal val id: String
    )

    private val handlers = mutableMapOf<String, (Event) -> Unit>()
    private val handlerIds = mutableMapOf<String, String>()

    private var nextId = 0

    /**
     * Subscribe to events. Returns a Subscription handle.
     */
    fun subscribe(handler: (Event) -> Unit): Subscription {
        val id = "handler_${nextId++}"
        handlers[id] = handler
        handlerIds[id] = handler.hashCode().toString()
        return Subscription(id)
    }

    /**
     * Publish an event to all subscribers.
     */
    fun publish(event: Event) {
        // Copy the map to avoid ConcurrentModificationException
        val snapshot = handlers.toList()
        for ((_, handler) in snapshot) {
            try {
                handler(event)
            } catch (e: Exception) {
                // Log but don't crash — individual subscriber errors shouldn't affect others
                android.util.Log.w("EventBus", "Subscriber error: ${e.message}")
            }
        }
    }

    /**
     * Unsubscribe a previously returned subscription.
     */
    fun unsubscribe(subscription: Subscription) {
        handlers.remove(subscription.id)
        handlerIds.remove(subscription.id)
    }
}
