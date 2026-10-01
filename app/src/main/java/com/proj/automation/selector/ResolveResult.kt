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
