package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import kotlinx.coroutines.delay

/**
 * Polls until the step's target is found or the timeout expires.
 */
class WaitForHandler : ActionHandler {
    override val actionType = ActionType.WAIT_FOR

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val startTime = System.currentTimeMillis()
        if (step.target == null) {
            return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "wait_for requires a target"
            )
        }

        val timeoutMs = step.timeoutMs
        val deadline = System.currentTimeMillis() + timeoutMs
        val pollInterval = 500L // Poll every 500ms
        var polls = 0
        var last: ActionContext.Lookup.Missing? = null

        while (System.currentTimeMillis() < deadline) {
            context.throwIfCancelled()
            polls++
            when (val lookup = context.locate(step)) {
                is ActionContext.Lookup.Found -> return StepResult(
                    stepIndex = 0, action = actionType, success = true,
                    durationMs = System.currentTimeMillis() - startTime,
                    strategy = lookup.strategy,
                    details = mapOf("pollAttempts" to polls, "confidence" to lookup.confidence)
                )
                is ActionContext.Lookup.Missing -> last = lookup
            }
            delay(pollInterval)
        }

        // An ambiguous screen is reported as such, not as a plain timeout
        val ambiguous = last?.takeIf { it.code == ErrorCode.E_LOW_CONFIDENCE }
        return StepResult(
            stepIndex = 0, action = actionType, success = false,
            durationMs = System.currentTimeMillis() - startTime,
            errorMessage = ambiguous?.message ?: "Timeout waiting for element after ${timeoutMs}ms",
            errorCode = ambiguous?.code ?: ErrorCode.E_TIMEOUT
        )
    }
}
