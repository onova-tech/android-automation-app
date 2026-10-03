package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/**
 * Finds the element of the step's target and taps it. Makes a single attempt;
 * retries are applied by [com.proj.automation.engine.ErrorHandler].
 */
class ClickHandler : ActionHandler {
    override val actionType = ActionType.CLICK

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val startTime = System.currentTimeMillis()
        context.throwIfCancelled()

        val found = when (val lookup = context.locate(step)) {
            is ActionContext.Lookup.Missing -> return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = lookup.message,
                errorCode = lookup.code
            )
            is ActionContext.Lookup.Found -> lookup
        }

        if (!context.automation.click(found.node)) {
            return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "Element found but click failed"
            )
        }

        return StepResult(
            stepIndex = 0, action = actionType, success = true,
            durationMs = System.currentTimeMillis() - startTime,
            strategy = found.strategy,
            details = mapOf("confidence" to found.confidence)
        )
    }
}
