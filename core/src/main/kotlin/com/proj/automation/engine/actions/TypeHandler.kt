package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/** Types text into the step's target, or into the focused editable field when there is no target. */
class TypeHandler : ActionHandler {
    override val actionType = ActionType.TYPE

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val start = context.clock()
        context.throwIfCancelled()
        val text = step.parameters["text"] as? String
            ?: return context.result(actionType, start, false, "type requires a 'text' parameter")

        val node = if (step.target != null) {
            when (val lookup = context.locate(step)) {
                is ActionContext.Lookup.Missing -> return context.missing(actionType, start, lookup)
                is ActionContext.Lookup.Found -> lookup.node
            }
        } else {
            context.snapshot()?.walk()?.firstOrNull { it.focused && it.editable }
                ?: return context.result(actionType, start, false, "No focused editable field found", ErrorCode.E_NOT_FOUND)
        }
        val ok = context.device.setText(node, text)
        return context.result(
            actionType, start, ok, if (ok) null else "Failed to set text on the element",
            details = mapOf("textLength" to text.length)
        )
    }
}
