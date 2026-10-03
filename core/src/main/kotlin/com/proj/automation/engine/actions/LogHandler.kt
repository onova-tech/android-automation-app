package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import com.proj.automation.service.EventBus

/** Emits a message to the execution log. */
class LogHandler : ActionHandler {
    override val actionType = ActionType.LOG

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val start = context.clock()
        context.throwIfCancelled()
        val message = step.parameters["message"] as? String ?: "Log entry"
        context.eventBus.publish(EventBus.Event.LogMessage(message))
        return context.result(actionType, start, true, details = mapOf("message" to message))
    }
}
