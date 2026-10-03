package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/**
 * Reads the text of the element matched by the target. Falls back to the content
 * description, which is where Flutter apps (e.g. Nubank) expose their labels and values.
 * The value is returned in `details["value"]`; the DSL binds it with `into: <variable>`.
 */
class ReadTextHandler : ActionHandler {
    override val actionType = ActionType.READ_TEXT

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val startTime = System.currentTimeMillis()
        context.throwIfCancelled()

        val found = when (val lookup = context.locate(step)) {
            is ActionContext.Lookup.Missing -> return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = lookup.message,
                errorCode = lookup.code
            )
            is ActionContext.Lookup.Found -> lookup
        }

        val node = found.node
        val value = node.text?.toString()?.takeIf { it.isNotEmpty() }
            ?: node.contentDescription?.toString()
            ?: ""
        return StepResult(
            stepIndex = 0, action = actionType, success = true,
            durationMs = System.currentTimeMillis() - startTime,
            strategy = found.strategy,
            details = mapOf("value" to value, "confidence" to found.confidence)
        )
    }
}
