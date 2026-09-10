package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/**
 * Scrolls a scrollable node in the specified direction.
 */
class ScrollHandler : ActionHandler {
    override val actionType = ActionType.SCROLL

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val startTime = System.currentTimeMillis()
        context.throwIfCancelled()

        val direction = (step.parameters["direction"] as? String)?.lowercase() ?: "forward"
        val rootNode = context.automation.getRootNode()
            ?: return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "No accessibility tree available"
            )

        val success = when (direction) {
            "forward", "down", "right" -> context.automation.scrollForward(rootNode)
            "backward", "back", "up", "left" -> context.automation.scrollBackward(rootNode)
            else -> false
        }

        return StepResult(
            stepIndex = 0, action = actionType, success = success,
            durationMs = System.currentTimeMillis() - startTime,
            errorMessage = if (!success) "Scroll failed (direction: $direction)" else null,
            details = mapOf("direction" to direction)
        )
    }
}
