package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/**
 * Reads the label of the step's target: its text, or its content description (where Flutter
 * apps such as Nubank expose labels and values). The DSL binds it with `into: <variable>`.
 */
class ReadTextHandler : ActionHandler {
    override val actionType = ActionType.READ_TEXT

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val start = context.clock()
        context.throwIfCancelled()
        val found = when (val lookup = context.locate(step)) {
            is ActionContext.Lookup.Missing -> return context.missing(actionType, start, lookup)
            is ActionContext.Lookup.Found -> lookup
        }
        return context.result(
            actionType, start, true, strategy = found.strategy,
            details = mapOf("value" to (found.node.label ?: ""), "confidence" to found.confidence)
        )
    }
}
