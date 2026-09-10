package com.proj.automation.engine

import com.proj.automation.accessibility.AutomationBridge
import com.proj.automation.parser.Step
import com.proj.automation.selector.SelectorEngine
import com.proj.automation.service.EventBus

/**
 * Context provided to each action handler with access to all runtime services.
 */
data class ActionContext(
    val automation: AutomationBridge,
    val selectorEngine: SelectorEngine,
    val eventBus: EventBus,
    val cancellationToken: CancellationToken
) {
    /**
     * Resolve a selector defined on the current step.
     * @return The resolved node, or null if not found.
     */
    fun resolveStepSelector(step: Step) =
        step.selector?.let { selectorEngine.resolve(it, automation.getRootNode()) }

    /**
     * Cancel the running workflow if a cancellation was requested.
     * Delegates to the shared CancellationToken.
     */
    fun throwIfCancelled() {
        cancellationToken.throwIfCancelled()
    }
}
