package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.parser.Selector

/**
 * Selector engine that resolves element selectors against the Android accessibility tree.
 *
 * A single selector (e.g. `text:`) is matched only by its own strategy — a text value
 * can't be looked up as a resource ID. Fallback across strategies is expressed in YAML
 * with a `fallback:` list, which is tried in the order written. Recommended order:
 * 1. resource_id — most specific, stable within an app version
 * 2. text — readable but i18n-sensitive
 * 3. content_description — accessible label, more stable than text
 * 4. class_name + index — structural, fragile across UI changes
 */
class SelectorEngine {

    private val strategies: List<SelectorStrategy> = listOf(
        ByResourceIdStrategy(),
        ByTextStrategy(),
        ByContentDescriptionStrategy(),
        ByClassNameStrategy()
    )

    /**
     * Resolve a selector against the accessibility tree.
     * @return The first matching node, or null if no strategy finds a match.
     */
    fun resolve(selector: Selector, root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        return resolveWithDetails(selector, root).node
    }

    /**
     * Resolve a selector with full strategy tracking and fallback history.
     */
    fun resolveWithDetails(selector: Selector, root: AccessibilityNodeInfo?): ResolveResult {
        val result = ResolveResult(
            node = null,
            strategy = "none",
            matchedIndex = null,
            matchTimeMs = 0,
            fallbacksAttempted = emptyList()
        )

        if (root == null) {
            return result.copy(strategy = "null_tree")
        }

        val startTime = System.currentTimeMillis()

        // Determine which strategies to try based on selector type
        val strategiesToTry = resolveStrategyOrder(selector)

        val attempted: MutableList<String> = mutableListOf()

        for ((index, strategy) in strategiesToTry.withIndex()) {
            val found = strategy.find(root, selector)
            if (found.isNotEmpty()) {
                val endTime = System.currentTimeMillis()
                return ResolveResult(
                    node = found.first(),
                    strategy = strategy.name,
                    matchedIndex = index,
                    matchTimeMs = endTime - startTime,
                    fallbacksAttempted = attempted
                )
            }
            attempted.add(strategy.name)
        }

        // Composite: try each sub-selector in the order defined in YAML
        if (selector is Selector.Composite) {
            for (subSelector in selector.fallbackOrder) {
                val subResult = resolveWithDetails(subSelector, root)
                if (subResult.node != null) {
                    return subResult.copy(fallbacksAttempted = attempted + subResult.fallbacksAttempted)
                }
                attempted.addAll(subResult.fallbacksAttempted)
            }
        }

        return result.copy(
            strategy = "none_matched",
            fallbacksAttempted = attempted
        )
    }

    /**
     * The strategy matching the selector's type. Composite selectors have none of
     * their own; their sub-selectors are resolved individually.
     */
    private fun resolveStrategyOrder(selector: Selector): List<SelectorStrategy> {
        val name = when (selector) {
            is Selector.ByResourceId -> "resource_id"
            is Selector.ByText -> "text"
            is Selector.ByContentDescription -> "content_description"
            is Selector.ByClassName -> "class_name"
            else -> return emptyList()
        }
        return strategies.filter { it.name == name }
    }
}
