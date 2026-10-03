package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/** Taps the element of the step's target. One attempt; retries come from the ErrorHandler. */
class ClickHandler : ActionHandler {
    override val actionType = ActionType.CLICK

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val start = context.clock()
        context.throwIfCancelled()
        val found = when (val lookup = context.locate(step)) {
            is ActionContext.Lookup.Missing -> return context.missing(actionType, start, lookup)
            is ActionContext.Lookup.Found -> lookup
        }
        if (!context.device.click(found.node)) {
            return context.result(actionType, start, false, "Element found but click failed")
        }
        return context.result(actionType, start, true, strategy = found.strategy, details = mapOf("confidence" to found.confidence))
    }
}
