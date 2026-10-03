package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.HandlerException
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/**
 * Injects text into the currently focused editable field.
 * Uses the element currently in focus (set by previous action or explicit).
 */
class TypeHandler : ActionHandler {
    override val actionType = ActionType.TYPE

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val startTime = System.currentTimeMillis()
        context.throwIfCancelled()

        val text = step.parameters["text"] as? String
            ?: throw HandlerException("type requires 'text' parameter", actionType)

        // With a target, type into that element
        if (step.target != null) {
            return when (val lookup = context.locate(step)) {
                is ActionContext.Lookup.Missing -> StepResult(
                    stepIndex = 0, action = actionType, success = false,
                    durationMs = System.currentTimeMillis() - startTime,
                    errorMessage = lookup.message, errorCode = lookup.code
                )
                is ActionContext.Lookup.Found -> {
                    val ok = context.automation.setText(lookup.node, text)
                    StepResult(
                        stepIndex = 0, action = actionType, success = ok,
                        durationMs = System.currentTimeMillis() - startTime,
                        errorMessage = if (ok) null else "Failed to set text on the element",
                        strategy = lookup.strategy,
                        details = mapOf("textLength" to text.length)
                    )
                }
            }
        }

        // Otherwise, find the focused editable field
        val rootNode = context.automation.getRootNode()
        if (rootNode == null) {
            return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "No accessibility tree available — enable Accessibility Service"
            )
        }

        // Try to find a focused editable node
        val focusedNode = findFocusedNode(rootNode)
        if (focusedNode != null) {
            val success = context.automation.setText(focusedNode, text)
            if (!success) {
                return StepResult(
                    stepIndex = 0, action = actionType, success = false,
                    durationMs = System.currentTimeMillis() - startTime,
                    errorMessage = "Failed to set text on focused element"
                )
            }
        } else {
            return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "No focused editable field found"
            )
        }

        return StepResult(
            stepIndex = 0, action = actionType, success = true,
            durationMs = System.currentTimeMillis() - startTime,
            details = mapOf("textLength" to text.length)
        )
    }

    /**
     * Recursively find the first focused editable node in the tree.
     */
    private fun findFocusedNode(root: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
        val stack = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.isFocusable && current.isEditable) {
                return current
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return null
    }
}
