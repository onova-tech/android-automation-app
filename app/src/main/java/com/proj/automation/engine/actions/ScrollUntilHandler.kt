package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import kotlinx.coroutines.delay

/**
 * Scrolls until the step's target is on screen. Stops at `max_scrolls`
 * (default 10) or when the list no longer moves. Parameters: `direction`, `max_scrolls`.
 */
class ScrollUntilHandler : ActionHandler {
    override val actionType = ActionType.SCROLL_UNTIL

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val startTime = System.currentTimeMillis()
        val maxScrolls = (step.parameters["max_scrolls"] as? String)?.toIntOrNull()
            ?.coerceIn(0, ReadListHandler.MAX_SCROLLS) ?: 10
        val forward = try {
            Scrolling.isForward(step.parameters["direction"] as? String)
        } catch (e: IllegalArgumentException) {
            return result(startTime, false, e.message, ErrorCode.E_ACTION_FAILED)
        }

        var scrolls = 0
        while (true) {
            context.throwIfCancelled()
            when (val lookup = context.locate(step)) {
                is ActionContext.Lookup.Found ->
                    return result(startTime, true, null, null, lookup.strategy, scrolls)
                is ActionContext.Lookup.Missing ->
                    // Ambiguity will not improve by scrolling past it
                    if (lookup.code == ErrorCode.E_LOW_CONFIDENCE || lookup.code == ErrorCode.E_ACTION_FAILED) {
                        return result(startTime, false, lookup.message, lookup.code, scrolls = scrolls)
                    }
            }
            if (scrolls >= maxScrolls) break
            val before = context.snapshot()
            if (!Scrolling.scrollOnce(context, before, forward)) break
            delay(Scrolling.SETTLE_MS)
            if (context.snapshot()?.signature() == before?.signature()) break // end of the list
            scrolls++
        }
        return result(startTime, false, "Not found after $scrolls scroll(s)", ErrorCode.E_NOT_FOUND, scrolls = scrolls)
    }

    private fun result(
        startTime: Long, success: Boolean, message: String?, code: ErrorCode?,
        strategy: String? = null, scrolls: Int = 0
    ) = StepResult(
        stepIndex = 0, action = actionType, success = success,
        durationMs = System.currentTimeMillis() - startTime,
        errorMessage = message, errorCode = code, strategy = strategy,
        details = mapOf("scrolls" to scrolls)
    )
}
