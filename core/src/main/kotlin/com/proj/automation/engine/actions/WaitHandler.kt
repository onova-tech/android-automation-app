package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/** Pauses for `seconds` (default 1). */
class WaitHandler : ActionHandler {
    override val actionType = ActionType.WAIT

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val start = context.clock()
        context.throwIfCancelled()
        val seconds = (step.parameters["seconds"] as? String)?.toDoubleOrNull() ?: 1.0
        context.sleep((seconds * 1000).toLong())
        return context.result(actionType, start, true)
    }
}
