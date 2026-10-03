package com.proj.automation.engine

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.accessibility.AutomationBridge
import com.proj.automation.parser.Step
import com.proj.automation.resolve.Resolution
import com.proj.automation.resolve.TargetResolver
import com.proj.automation.selector.SelectorEngine
import com.proj.automation.service.EventBus
import com.proj.automation.ui.UiNode
import com.proj.automation.ui.UiSnapshots

/**
 * Context provided to each action handler with access to all runtime services.
 */
data class ActionContext(
    val automation: AutomationBridge,
    val selectorEngine: SelectorEngine,
    val eventBus: EventBus,
    val cancellationToken: CancellationToken,
    val targetResolver: TargetResolver = TargetResolver(),
    /** Current screen as a snapshot; injectable so handlers can be tested on the JVM */
    val snapshot: () -> UiNode? = { automation.getRootNode()?.let { UiSnapshots.fromAccessibility(it) } }
) {
    /** Result of looking up the element a step acts on */
    sealed class Lookup {
        data class Found(val node: AccessibilityNodeInfo, val strategy: String, val confidence: Double) : Lookup()
        data class Missing(val code: ErrorCode, val message: String) : Lookup()
    }

    /**
     * Resolve a selector defined on the current step.
     * @return The resolved node, or null if not found.
     */
    fun resolveStepSelector(step: Step) =
        step.selector?.let { selectorEngine.resolve(it, automation.getRootNode()) }

    /** Finds the element for a step, from its v2 `target` or its v1 `selector`. */
    fun locate(step: Step): Lookup {
        step.target?.let { target ->
            return when (val r = targetResolver.resolve(target, snapshot())) {
                is Resolution.Found -> (r.node.ref as? AccessibilityNodeInfo)
                    ?.let { Lookup.Found(it, r.stage, r.confidence) }
                    ?: Lookup.Missing(ErrorCode.E_ACTION_FAILED, "Element has no live node")
                is Resolution.NotFound -> Lookup.Missing(ErrorCode.E_NOT_FOUND, r.reason)
                is Resolution.Ambiguous -> Lookup.Missing(
                    ErrorCode.E_LOW_CONFIDENCE,
                    r.reason + "; candidates: " + r.top.joinToString { "'${it.first.label?.take(30)}' %.2f".format(it.second) }
                )
            }
        }
        val selector = step.selector
            ?: return Lookup.Missing(ErrorCode.E_ACTION_FAILED, "${step.action.yamlValue} requires a selector or target")
        return selectorEngine.resolve(selector, automation.getRootNode())
            ?.let { Lookup.Found(it, "selector", 1.0) }
            ?: Lookup.Missing(ErrorCode.E_NOT_FOUND, "Element not found")
    }

    /**
     * Cancel the running workflow if a cancellation was requested.
     * Delegates to the shared CancellationToken.
     */
    fun throwIfCancelled() {
        cancellationToken.throwIfCancelled()
    }
}
