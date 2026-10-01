# Selector Engine Pattern

## Purpose

Guide the implementation of a multi-strategy selector engine that resolves element selectors against the Android accessibility tree using a weighted fallback chain. Covers the strategy interface, individual strategy implementations, composite selectors, and result tracking.

## When This Pattern Applies

Use this pattern when:
- Resolving a `Selector` definition into an `AccessibilityNodeInfo`
- Implementing the weighted fallback chain (resource_id → text → content_description → class_name + index)
- Building strategy implementations for new selector types
- Tracking which strategy matched and how many fallbacks were attempted
- Handling case-insensitive text matching
- Dealing with null/empty/deep accessibility trees

## Strategy Interface

```kotlin
package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Interface for a single selector strategy.
 * Each strategy searches the accessibility tree using a specific attribute.
 */
interface SelectorStrategy {
    /** Stable identifier for this strategy (used in ResolveResult.strategy) */
    val name: String

    /**
     * Search the tree for nodes matching this strategy's criterion.
     * @return List of matching nodes (may be empty or have duplicates)
     */
    fun find(root: AccessibilityNodeInfo?, selector: Selector): List<AccessibilityNodeInfo>

    /**
     * Check whether a single node matches this strategy's criterion.
     * @return true if the node matches, false otherwise
     */
    fun matches(node: AccessibilityNodeInfo, selector: Selector): Boolean
}
```

## Fallback Chain Order (Weighted)

The engine tries strategies in priority order. Higher weight = tried first:

```
1. resource_id   (weight: 10) — most specific, stable within an app version
2. text          (weight: 8)  — readable but i18n-sensitive
3. content_description (weight: 7) — accessible label, more stable than text
4. class_name + index (weight: 5) — structural, fragile across UI changes
5. composite fallback (weight: 3) — explicit fallback chain in YAML
```

## Strategy Implementations

### ByResourceId Strategy

```kotlin
package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo

class ByResourceIdStrategy : SelectorStrategy {
    override val name: String = "resource_id"

    override fun find(root: AccessibilityNodeInfo?, selector: Selector): List<AccessibilityNodeInfo> {
        val resourceId = (selector as? Selector.ByResourceId)?.resourceId ?: return emptyList()
        return root?.findAccessibilityNodeInfosByViewId(resourceId) ?: emptyList()
    }

    override fun matches(node: AccessibilityNodeInfo, selector: Selector): Boolean {
        val id = (selector as? Selector.ByResourceId)?.resourceId ?: return false
        return node.viewIdResourceName == id
    }
}
```

### ByText Strategy

```kotlin
package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo

class ByTextStrategy : SelectorStrategy {
    override val name: String = "text"

    override fun find(root: AccessibilityNodeInfo?, selector: Selector): List<AccessibilityNodeInfo> {
        val text = (selector as? Selector.ByText)?.text ?: return emptyList()
        return findAllMatching(root, text) { it.text?.toString() == it.text }
    }

    override fun matches(node: AccessibilityNodeInfo, selector: Selector): Boolean {
        val text = (selector as? Selector.ByText)?.text ?: return false
        val nodeText = node.text?.toString() ?: return false
        // Case-insensitive matching
        return nodeText.equals(text, ignoreCase = true)
    }

    private fun findAllMatching(
        root: AccessibilityNodeInfo?,
        target: String,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return results

        // Iterative traversal to avoid stack overflow on deep trees
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (predicate(current)) {
                results.add(current)
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return results
    }
}
```

### ByContentDescription Strategy

```kotlin
package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo

class ByContentDescriptionStrategy : SelectorStrategy {
    override val name: String = "content_description"

    override fun find(root: AccessibilityNodeInfo?, selector: Selector): List<AccessibilityNodeInfo> {
        val description = (selector as? Selector.ByContentDescription)?.description ?: return emptyList()
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return results

        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.contentDescription?.toString() == description) {
                results.add(current)
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return results
    }

    override fun matches(node: AccessibilityNodeInfo, selector: Selector): Boolean {
        val description = (selector as? Selector.ByContentDescription)?.description ?: return false
        return node.contentDescription?.toString() == description
    }
}
```

### ByClassName Strategy

```kotlin
package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo

class ByClassNameStrategy : SelectorStrategy {
    override val name: String = "class_name"

    override fun find(root: AccessibilityNodeInfo?, selector: Selector): List<AccessibilityNodeInfo> {
        val classSelector = selector as? Selector.ByClassName ?: return emptyList()
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return results

        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.className?.equals(classSelector.className, ignoreCase = true) == true) {
                results.add(current)
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { stack.addLast(it) }
            }
        }

        // Apply index filter if specified
        return if (classSelector.index != null && classSelector.index in results.indices) {
            listOf(results[classSelector.index])
        } else {
            results
        }
    }

    override fun matches(node: AccessibilityNodeInfo, selector: Selector): Boolean {
        val classSelector = selector as? Selector.ByClassName ?: return false
        return node.className?.equals(classSelector.className, ignoreCase = true) == true
    }
}
```

## Engine — Multi-Strategy Orchestrator

```kotlin
package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo

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
```

## ResolveResult

```kotlin
package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Result of a selector resolution attempt, including which strategy matched
 * and which fallbacks were attempted before finding the match (or failing).
 */
data class ResolveResult(
    val node: AccessibilityNodeInfo?,
    val strategy: String,
    val matchedIndex: Int?,
    val matchTimeMs: Long,
    val fallbacksAttempted: List<String>
) {
    val success: Boolean get() = node != null
}
```

## Composite Selector — Fallback Chain in YAML

When the YAML defines a `fallback` list, the engine tries each sub-selector in order:

```kotlin
/**
 * Composite selector that tries multiple strategies as a fallback chain.
 * Defined in YAML as:
 *   selector:
 *     fallback:
 *       - text: "Submit"
 *       - content_description: "submit_button"
 *       - resource_id: "com.app:id/submit"
 */
```

The `resolveWithDetails` method already handles `Selector.Composite` by iterating
the `fallbackOrder` list and returning the first match.

## Null Safety for Tree Traversal

All strategies use iterative traversal (ArrayDeque stack) to handle deep trees
without stack overflow. The engine guards against null roots:

```kotlin
fun resolve(selector: Selector, root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
    if (root == null) return null  // Guard at entry point
    // ... strategies execute with null-checks inside
}
```

## Customization Guide

| Parameter | Default | Notes |
|-----------|---------|-------|
| Strategy order | `resource_id → text → content_description → class_name` | Change `strategies` list in `SelectorEngine` constructor |
| Composite fallback | YAML `selector.fallback` list | Order determined by YAML author |
| Text matching | Case-insensitive | Set in `ByTextStrategy.matches()` |
| Index filtering | None (returns all matches) | `ByClassName.index` filters to Nth match |
| Tree depth limit | None (full traversal) | Add depth parameter if trees are excessively deep (>500 nodes) |

## Validation

Before integrating with the execution engine, verify:

- [ ] `resolve(ByText("2"), calculatorRoot)` returns the node with text "2"
- [ ] `resolve(ByText("2"), root)` matches case-insensitively ("2" matches "2")
- [ ] `resolve(ByResourceId("com.android.calculator2:id/clear"), root)` finds the clear button
- [ ] `resolve(ByContentDescription("add"), root)` finds the "+" button by its accessibility label
- [ ] `resolve(ByClassName("android.widget.Button", index=0), root)` returns the first Button
- [ ] Fallback chain: `resolve(ByResourceId("nonexistent"), root)` falls through to text, then content_description
- [ ] `resolve(any, null)` returns null (null-safe for empty trees)
- [ ] `resolveWithDetails` returns correct `strategy` field indicating which strategy matched
- [ ] `fallbacksAttempted` lists strategies that were tried but found no match
- [ ] `Composite` selector tries sub-selectors in YAML-defined order
- [ ] Tree traversal does not overflow stack on deep trees (test with 1000+ node tree)
- [ ] Multiple matches: `resolve(ByText("C"), root)` returns the first match only
