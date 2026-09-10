package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Selector
import com.proj.automation.parser.Step
import kotlinx.coroutines.delay

/**
 * Finds an element by selector and taps it, with configurable retry support.
 */
class ClickHandler : ActionHandler {
    override val actionType = ActionType.CLICK

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val startTime = System.currentTimeMillis()
        val selector = step.selector
            ?: return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "click requires a selector"
            )

        val maxAttempts = step.retries.coerceAtLeast(1)
        val retryDelay = step.retryDelayMs

        for (attempt in 1..maxAttempts) {
            context.throwIfCancelled()

            val resolved = context.selectorEngine.resolve(
                selector, context.automation.getRootNode()
            )
            if (resolved != null) {
                val clicked = context.automation.click(resolved)
                if (clicked) {
                    return StepResult(
                        stepIndex = 0, action = actionType, success = true,
                        durationMs = System.currentTimeMillis() - startTime,
                        strategy = resolveStrategyName(selector),
                        details = mapOf("attempt" to attempt)
                    )
                }
            }

            if (attempt < maxAttempts) {
                delay(retryDelay)
            }
        }

        return StepResult(
            stepIndex = 0, action = actionType, success = false,
            durationMs = System.currentTimeMillis() - startTime,
            errorMessage = "Element not found after $maxAttempts attempts"
        )
    }

    private fun resolveStrategyName(selector: Selector): String = when (selector) {
        is Selector.ByResourceId -> "resource_id"
        is Selector.ByText -> "text"
        is Selector.ByContentDescription -> "content_description"
        is Selector.ByClassName -> "class_name"
        is Selector.Composite -> "composite"
        else -> "unknown"
    }
}
