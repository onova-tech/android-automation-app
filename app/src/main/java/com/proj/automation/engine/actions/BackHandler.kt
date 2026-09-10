package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/**
 * Triggers system back navigation.
 */
class BackHandler : ActionHandler {
    override val actionType = ActionType.BACK

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        context.throwIfCancelled()
        val startTime = System.currentTimeMillis()
        val success = context.automation.goBack()
        return StepResult(
            stepIndex = 0, action = actionType, success = success,
            durationMs = System.currentTimeMillis() - startTime,
            errorMessage = if (!success) "Back navigation failed" else null
        )
    }
}
