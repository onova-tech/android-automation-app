package com.proj.automation.engine

import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.OnFailurePolicy
import com.proj.automation.parser.Step
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Applies configurable error policies per action step.
 * Handles retry loops, timeouts, and failure policies (ABORT/CONTINUE/RETRY).
 *
 * This is the single place where steps are retried: handlers make one attempt
 * per call, and the step's timeout bounds all attempts together.
 */
class ErrorHandler {

    /**
     * Execute an action with retry and timeout logic per the step's policy.
     *
     * Attempts: `on_failure: retry(n, ms)` uses n attempts with ms between them;
     * otherwise the step's `retries` / `retry_delay` apply.
     */
    suspend fun executeWithPolicy(
        step: Step,
        action: suspend (ActionContext) -> StepResult,
        context: ActionContext
    ): StepResult {
        val policy = step.onFailure
        val maxAttempts = when (policy) {
            is OnFailurePolicy.RETRY -> policy.maxAttempts
            else -> step.retries
        }.coerceAtLeast(1)
        val retryDelay = when (policy) {
            is OnFailurePolicy.RETRY -> policy.delayMs
            else -> step.retryDelayMs
        }
        // A `wait` step's duration is its own parameter, so the step timeout doesn't cut it short.
        val timeoutMs = if (step.action == ActionType.WAIT) Long.MAX_VALUE else step.timeoutMs
        val stepStartTime = System.currentTimeMillis()

        var lastResult: StepResult? = null

        for (attempt in 1..maxAttempts) {
            context.throwIfCancelled()

            val remaining = timeoutMs - (System.currentTimeMillis() - stepStartTime)
            val attemptResult = (if (remaining > 0) withTimeoutOrNull(remaining) { action(context) } else null)
                ?: return StepResult(
                    stepIndex = lastResult?.stepIndex ?: 0,
                    action = step.action,
                    success = false,
                    durationMs = System.currentTimeMillis() - stepStartTime,
                    errorMessage = "Timeout after ${timeoutMs}ms"
                )
            lastResult = attemptResult

            if (attemptResult.success) {
                return if (attempt > 1) {
                    attemptResult.copy(details = attemptResult.details + ("attempt" to attempt))
                } else {
                    attemptResult
                }
            }

            if (attempt < maxAttempts) {
                delay(retryDelay)
            }
        }

        return when (policy) {
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
