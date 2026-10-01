package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import kotlinx.coroutines.delay

/**
 * Suspends execution for a specified duration.
 */
class WaitHandler : ActionHandler {
    override val actionType = ActionType.WAIT

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        context.throwIfCancelled()
        val startTime = System.currentTimeMillis()
        val seconds = (step.parameters["seconds"] as? String)?.toDoubleOrNull() ?: 1.0
        val durationMs = (seconds * 1000).toLong()
        delay(durationMs)
        return StepResult(
            stepIndex = 0, action = actionType, success = true,
            durationMs = System.currentTimeMillis() - startTime
        )
    }
}
