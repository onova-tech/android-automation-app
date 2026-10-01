package com.proj.automation.service

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Typed event bus between the device, the engine and the UI.
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

    // Published from the accessibility and engine threads, subscribed from the UI thread
    private val handlers = ConcurrentHashMap<String, (Event) -> Unit>()

    private val nextId = AtomicInteger(0)

    /**
     * Subscribe to events. Returns a Subscription handle.
     */
    fun subscribe(handler: (Event) -> Unit): Subscription {
        val id = "handler_${nextId.getAndIncrement()}"
        handlers[id] = handler
        return Subscription(id)
    }

    /**
     * Publish an event to all subscribers.
     */
    fun publish(event: Event) {
        for (handler in handlers.values) {
            try {
                handler(event)
            } catch (e: Exception) {
                // Individual subscriber errors shouldn't affect others or the publisher
                System.err.println("EventBus subscriber error: ${e.message}")
            }
        }
    }

    /**
     * Unsubscribe a previously returned subscription.
     */
    fun unsubscribe(subscription: Subscription) {
        handlers.remove(subscription.id)
    }

    companion object {
        /** Process-wide bus shared by the AccessibilityService and the UI. */
        val default: EventBus by lazy { EventBus() }
    }
}
