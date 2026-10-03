package com.proj.automation.engine

import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/**
 * Contract for each action handler. Each ActionType gets its own handler.
 */
interface ActionHandler {
    val actionType: ActionType

    /**
     * Execute this action for the given step.
     * @throws HandlerException if the action cannot be performed
     */
    suspend fun execute(step: Step, context: ActionContext): StepResult
}

class HandlerException(
    message: String,
    val actionType: ActionType
) : RuntimeException(message)
