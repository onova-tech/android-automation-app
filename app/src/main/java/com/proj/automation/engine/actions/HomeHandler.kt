package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/**
 * Triggers system home navigation.
 */
class HomeHandler : ActionHandler {
    override val actionType = ActionType.HOME

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        context.throwIfCancelled()
        val startTime = System.currentTimeMillis()
        val success = context.automation.goHome()
        return StepResult(
            stepIndex = 0, action = actionType, success = success,
            durationMs = System.currentTimeMillis() - startTime,
            errorMessage = if (!success) "Home navigation failed" else null
        )
    }
}
