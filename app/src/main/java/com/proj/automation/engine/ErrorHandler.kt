package com.proj.automation.engine

import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.OnFailurePolicy
import com.proj.automation.parser.Step
import kotlinx.coroutines.delay

/**
 * Applies configurable error policies per action step.
 * Handles retry loops, timeouts, and failure policies (ABORT/CONTINUE/RETRY).
 */
class ErrorHandler {

    /**
     * Execute an action with retry and timeout logic per the step's policy.
     */
    suspend fun executeWithPolicy(
        step: Step,
        action: suspend (ActionContext) -> StepResult,
        context: ActionContext
    ): StepResult {
        val maxAttempts = when (step.onFailure) {
            OnFailurePolicy.ABORT -> 1
            OnFailurePolicy.CONTINUE -> 1
            is OnFailurePolicy.RETRY -> step.retries.coerceAtLeast(1)
        }
        val retryDelay = step.retryDelayMs
        val timeoutMs = step.timeoutMs
        val stepStartTime = System.currentTimeMillis()
        val deadline = stepStartTime + timeoutMs

        var lastResult: StepResult? = null

        for (attempt in 1..maxAttempts) {
            context.throwIfCancelled()

            if (System.currentTimeMillis() > deadline) {
                return StepResult(
                    stepIndex = lastResult?.stepIndex ?: 0,
                    action = step.action,
                    success = false,
                    durationMs = System.currentTimeMillis() - stepStartTime,
                    errorMessage = "Timeout after ${timeoutMs}ms"
                )
            }

            lastResult = action(context)

            if (lastResult.success) return lastResult

            if (attempt < maxAttempts) {
                delay(retryDelay)
            }
        }

        return when (step.onFailure) {
            OnFailurePolicy.CONTINUE -> StepResult(
                stepIndex = lastResult?.stepIndex ?: 0,
                action = step.action,
                success = true, // CONTINUE treats failure as success
                durationMs = System.currentTimeMillis() - stepStartTime,
                errorMessage = lastResult?.errorMessage,
                details = mapOf("skipped" to true)
            )
            else -> lastResult ?: StepResult(
                stepIndex = 0, action = step.action, success = false,
                durationMs = 0, errorMessage = "Unknown failure"
            )
        }
    }
}
