package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Selector
import com.proj.automation.parser.Step
import kotlinx.coroutines.delay

/**
 * Polls a selector until the element is found or timeout expires.
 */
class WaitForHandler : ActionHandler {
    override val actionType = ActionType.WAIT_FOR

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val startTime = System.currentTimeMillis()
        val selector = step.selector
            ?: return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "wait_for requires a selector"
            )

        val timeoutMs = step.timeoutMs
        val deadline = System.currentTimeMillis() + timeoutMs
        val pollInterval = 500L // Poll every 500ms

        while (System.currentTimeMillis() < deadline) {
            context.throwIfCancelled()
            val resolved = context.selectorEngine.resolve(
                selector, context.automation.getRootNode()
            )
            if (resolved != null) {
                return StepResult(
                    stepIndex = 0, action = actionType, success = true,
                    durationMs = System.currentTimeMillis() - startTime,
                    strategy = resolved.className?.toString() ?: "node",
                    details = mapOf("pollAttempts" to 1)
                )
            }
            delay(pollInterval)
        }

        return StepResult(
            stepIndex = 0, action = actionType, success = false,
            durationMs = System.currentTimeMillis() - startTime,
            errorMessage = "Timeout waiting for element after ${timeoutMs}ms",
            errorCode = ErrorCode.E_TIMEOUT
        )
    }
}
