package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/**
 * Emits a log message to the event bus for UI display.
 */
class LogHandler : ActionHandler {
    override val actionType = ActionType.LOG

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        context.throwIfCancelled()
        val message = step.parameters["message"] as? String ?: "Log entry"
        context.eventBus.publish(com.proj.automation.service.EventBus.Event.LogMessage(message))
        return StepResult(
            stepIndex = 0, action = actionType, success = true,
            durationMs = 0,
            details = mapOf("message" to message)
        )
    }
}
