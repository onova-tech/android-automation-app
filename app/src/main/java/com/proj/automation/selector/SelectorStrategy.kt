package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.parser.Selector

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
