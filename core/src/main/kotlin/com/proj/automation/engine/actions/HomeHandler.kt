package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/** System home navigation. */
class HomeHandler : ActionHandler {
    override val actionType = ActionType.HOME

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val start = context.clock()
        context.throwIfCancelled()
        val ok = context.device.home()
        return context.result(actionType, start, ok, if (ok) null else "Home navigation failed")
    }
}
