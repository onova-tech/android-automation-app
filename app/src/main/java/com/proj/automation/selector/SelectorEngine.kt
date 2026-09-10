package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.parser.Selector

/**
 * Multi-strategy selector engine that resolves element selectors against
 * the Android accessibility tree using a weighted fallback chain.
 *
 * Strategy priority order (highest weight first):
 * 1. resource_id (weight: 10) — most specific, stable within an app version
 * 2. text (weight: 8) — readable but i18n-sensitive
 * 3. content_description (weight: 7) — accessible label, more stable than text
 * 4. class_name + index (weight: 5) — structural, fragile across UI changes
 */
class SelectorEngine {

    /** Ordered list of strategies by priority (highest weight first) */
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

        // If no strategy matched, try composite fallback explicitly defined in YAML
        if (selector is Selector.Composite) {
            for (subSelector in selector.fallbackOrder) {
                val subResult = resolveWithDetails(subSelector, root)
                if (subResult.node != null) {
                    return subResult
                }
            }
        }

        return result.copy(
            strategy = "none_matched",
            fallbacksAttempted = attempted
        )
    }

    /**
     * Determine which strategies to attempt based on the selector type.
     * For a specific selector (e.g. ByText), only try that strategy first,
     * then fall back to the full chain.
     */
    private fun resolveStrategyOrder(selector: Selector): List<SelectorStrategy> {
        return when (selector) {
            is Selector.ByResourceId -> listOf(strategies.first { it.name == "resource_id" }) + strategies
            is Selector.ByText -> listOf(strategies.first { it.name == "text" }) + strategies
            is Selector.ByContentDescription ->
                listOf(strategies.first { it.name == "content_description" }) + strategies
            is Selector.ByClassName ->
                listOf(strategies.first { it.name == "class_name" }) + strategies
            is Selector.Composite -> strategies // Let composite handle its own order
            else -> strategies
        }
    }
}
