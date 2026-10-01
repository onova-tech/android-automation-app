package com.proj.automation.engine

import com.proj.automation.parser.Step
import com.proj.automation.resolve.Resolution
import com.proj.automation.resolve.TargetResolver
import com.proj.automation.service.EventBus
import com.proj.automation.ui.UiNode
import kotlinx.coroutines.delay

/**
 * Context provided to each action handler: the device, events, cancellation, element
 * resolution, and time. [clock] and [sleep] are injectable so replays run in virtual time.
 */
class ActionContext(
    val device: DevicePort,
    val eventBus: EventBus,
    val cancellationToken: CancellationToken,
    val targetResolver: TargetResolver = TargetResolver(),
    val clock: () -> Long = System::currentTimeMillis,
    val sleep: suspend (Long) -> Unit = { delay(it) }
) {
    /** Result of looking up the element a step acts on */
    sealed class Lookup {
        data class Found(val node: UiNode, val strategy: String, val confidence: Double) : Lookup()
        data class Missing(val code: ErrorCode, val message: String) : Lookup()
    }

    fun snapshot(): UiNode? = device.snapshot()

    /** Finds the element for a step from its `target`. */
    fun locate(step: Step): Lookup {
        val target = step.target
            ?: return Lookup.Missing(ErrorCode.E_ACTION_FAILED, "${step.action.yamlValue} requires a target")
        return when (val r = targetResolver.resolve(target, snapshot())) {
            is Resolution.Found -> Lookup.Found(r.node, r.stage, r.confidence)
            is Resolution.NotFound -> Lookup.Missing(ErrorCode.E_NOT_FOUND, r.reason)
            is Resolution.Ambiguous -> Lookup.Missing(
                ErrorCode.E_LOW_CONFIDENCE,
                r.reason + "; candidates: " + r.top.joinToString { "'${it.first.label?.take(30)}' %.2f".format(it.second) }
            )
        }
    }

    /** Throws [CancelledException] if the user stopped the run. */
    fun throwIfCancelled() {
        cancellationToken.throwIfCancelled()
    }
}
