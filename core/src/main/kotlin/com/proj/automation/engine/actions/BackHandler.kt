package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/** System back navigation. */
class BackHandler : ActionHandler {
    override val actionType = ActionType.BACK

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val start = context.clock()
        context.throwIfCancelled()
        val ok = context.device.back()
        return context.result(actionType, start, ok, if (ok) null else "Back navigation failed")
    }
}
