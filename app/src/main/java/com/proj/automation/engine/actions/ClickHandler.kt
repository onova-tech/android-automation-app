package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Selector
import com.proj.automation.parser.Step

/**
 * Finds an element by selector and taps it. Makes a single attempt;
 * retries are applied by [com.proj.automation.engine.ErrorHandler].
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

        context.throwIfCancelled()

        val resolved = context.selectorEngine.resolve(
            selector, context.automation.getRootNode()
        ) ?: return StepResult(
            stepIndex = 0, action = actionType, success = false,
            durationMs = System.currentTimeMillis() - startTime,
            errorMessage = "Element not found",
            errorCode = ErrorCode.E_NOT_FOUND
        )

        if (!context.automation.click(resolved)) {
            return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "Element found but click failed"
            )
        }

        return StepResult(
            stepIndex = 0, action = actionType, success = true,
            durationMs = System.currentTimeMillis() - startTime,
            strategy = resolveStrategyName(selector)
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
