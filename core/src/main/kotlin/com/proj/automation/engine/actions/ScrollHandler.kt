package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/** Scrolls the main scrollable container once (`direction`: forward/down or backward/up). */
class ScrollHandler : ActionHandler {
    override val actionType = ActionType.SCROLL

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val start = context.clock()
        context.throwIfCancelled()
        val forward = try {
            Scrolling.isForward(step.parameters["direction"] as? String)
        } catch (e: IllegalArgumentException) {
            return context.result(actionType, start, false, e.message)
        }
        val ok = Scrolling.scrollOnce(context, context.snapshot(), forward)
        return context.result(actionType, start, ok, if (ok) null else "Nothing to scroll")
    }
}
