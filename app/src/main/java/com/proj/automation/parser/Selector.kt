package com.proj.automation.parser

/**
 * Sealed class hierarchy for all supported selector types.
 * Each selector type maps to a specific Android accessibility attribute.
 */
sealed class Selector {
    /** Match by Android resource ID (e.g., "com.app:id/button") */
    data class ByResourceId(val resourceId: String) : Selector()

    /** Match by element text content (case-insensitive) */
    data class ByText(val text: String) : Selector()

    /** Match by accessibility content description */
    data class ByContentDescription(val description: String) : Selector()

    /** Match by class name, optionally by index among matches */
    data class ByClassName(val className: String, val index: Int? = null) : Selector()

    /** Explicit fallback chain — try each sub-selector in order */
    data class Composite(val fallbackOrder: List<Selector>) : Selector()
}
