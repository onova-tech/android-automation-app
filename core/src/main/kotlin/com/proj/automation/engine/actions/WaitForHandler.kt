package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/** Polls until the step's target is found or the step's timeout expires. */
class WaitForHandler : ActionHandler {
    override val actionType = ActionType.WAIT_FOR

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val start = context.clock()
        if (step.target == null) return context.result(actionType, start, false, "wait_for requires a target")

        val deadline = start + step.timeoutMs
        var polls = 0
        var last: ActionContext.Lookup.Missing? = null
        while (context.clock() < deadline) {
            context.throwIfCancelled()
            polls++
            when (val lookup = context.locate(step)) {
                is ActionContext.Lookup.Found -> return context.result(
                    actionType, start, true, strategy = lookup.strategy,
                    details = mapOf("pollAttempts" to polls, "confidence" to lookup.confidence)
                )
                is ActionContext.Lookup.Missing -> last = lookup
            }
            context.sleep(POLL_MS)
        }
        // An ambiguous screen is reported as such, not as a plain timeout
        val ambiguous = last?.takeIf { it.code == ErrorCode.E_LOW_CONFIDENCE }
        return context.result(
            actionType, start, false,
            ambiguous?.message ?: "Timeout waiting for element after ${step.timeoutMs}ms",
            ambiguous?.code ?: ErrorCode.E_TIMEOUT
        )
    }

    companion object {
        const val POLL_MS = 500L
    }
}
