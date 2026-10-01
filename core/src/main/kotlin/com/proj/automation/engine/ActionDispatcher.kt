package com.proj.automation.engine

import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/**
 * Routes each step to the correct handler based on step.action.
 */
class ActionDispatcher(
    private val handlers: Map<ActionType, ActionHandler>
) {
    /**
     * Dispatch a step to the correct handler.
     * @throws IllegalStateException if no handler is registered for the step's action
     */
    suspend fun dispatch(step: Step, context: ActionContext): StepResult {
        val handler = handlers[step.action]
            ?: throw IllegalStateException(
                "No handler registered for action: ${step.action}"
            )
        return handler.execute(step, context)
    }

    /** Check if a handler exists for the given action type */
    fun hasHandler(actionType: ActionType): Boolean = actionType in handlers
}
