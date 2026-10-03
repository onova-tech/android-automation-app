package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import com.proj.automation.ui.UiNode

/**
 * Collects the labels of list items on screen, in reading order, scrolling for more until
 * `max` items are found, the list stops changing, or `max_scrolls` is reached.
 *
 * Parameters: `match` (regex an item's label must contain, optional), `role` (`text` = any
 * labeled node, `button` = clickable ones; default `text`), `max` (default 10), `scroll`
 * (default true), `max_scrolls` (default 5), `direction`, `into`.
 * The value is a list of labels; templates render it one item per line.
 */
class ReadListHandler : ActionHandler {
    override val actionType = ActionType.READ_LIST

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val start = context.clock()
        context.throwIfCancelled()

        val p = step.parameters
        val pattern = (p["match"] as? String)?.takeIf { it.isNotEmpty() }?.let {
            try {
                Regex(it)
            } catch (e: IllegalArgumentException) {
                return context.result(actionType, start, false, "Invalid match pattern: ${e.message}", ErrorCode.E_ACTION_FAILED)
            }
        }
        val clickableOnly = (p["role"] as? String) == "button"
        val max = (p["max"] as? String)?.toIntOrNull()?.coerceIn(1, MAX_ITEMS) ?: 10
        val scroll = (p["scroll"] as? String)?.toBooleanStrictOrNull() ?: true
        val maxScrolls = (p["max_scrolls"] as? String)?.toIntOrNull()?.coerceIn(0, MAX_SCROLLS) ?: 5
        val forward = try {
            Scrolling.isForward(p["direction"] as? String)
        } catch (e: IllegalArgumentException) {
            return context.result(actionType, start, false, e.message, ErrorCode.E_ACTION_FAILED)
        }

        val items = LinkedHashSet<String>()
        var screen = context.snapshot() ?: return context.result(actionType, start, false, "No screen available", ErrorCode.E_NOT_FOUND)
        var scrolls = 0
        while (true) {
            screen.walk()
                .filter { it.label != null && (!clickableOnly || it.clickable) }
                .filter { n -> pattern?.containsMatchIn(n.label!!) ?: true }
                .sortedWith(compareBy<UiNode>({ it.bounds.top }, { it.bounds.left }))
                .forEach { if (items.size < max) items.add(it.label!!.take(MAX_LABEL)) }

            if (items.size >= max || !scroll || scrolls >= maxScrolls) break
            context.throwIfCancelled()
            if (!Scrolling.scrollOnce(context, screen, forward)) break
            context.sleep(Scrolling.SETTLE_MS)
            val next = context.snapshot() ?: break
            if (next.signature() == screen.signature()) break // end of the list
            scrolls++
            screen = next
        }
        return context.result(
            actionType, start, true,
            details = mapOf("value" to items.toList(), "count" to items.size, "scrolls" to scrolls)
        )
    }

    companion object {
        const val MAX_ITEMS = 100
        const val MAX_SCROLLS = 30
        const val MAX_LABEL = 300
    }
}
