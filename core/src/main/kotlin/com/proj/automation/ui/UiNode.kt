package com.proj.automation.ui

/** Screen rectangle in pixels (left, top, right, bottom). */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val isEmpty: Boolean get() = right <= left || bottom <= top
}

/**
 * Immutable snapshot of one accessibility node. Resolution, ranking and replay tests work on
 * these instead of live `AccessibilityNodeInfo`s, so they run on the JVM and on recorded screens.
 *
 * [ref] carries the live node (when the snapshot came from the device) so an action can be
 * performed on the element that was chosen.
 */
data class UiNode(
    val text: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val focused: Boolean = false,
    val bounds: Bounds = Bounds(0, 0, 0, 0),
    val children: List<UiNode> = emptyList(),
    val ref: Any? = null
) {
    /** Visible label: text, else content description (Flutter apps expose labels there) */
    val label: String? get() = text?.takeIf { it.isNotBlank() } ?: contentDescription?.takeIf { it.isNotBlank() }

    /** Depth-first, pre-order traversal including this node */
    fun walk(): Sequence<UiNode> = sequence {
        val stack = ArrayDeque<UiNode>().apply { add(this@UiNode) }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            yield(n)
            for (i in n.children.indices.reversed()) stack.add(n.children[i])
        }
    }

    /** Cheap fingerprint of what is on screen; used to notice that a scroll changed nothing */
    fun signature(): Int = walk().map { listOf(it.className, it.label, it.resourceId) }.toList().hashCode()

    override fun toString(): String =
        "UiNode(${className?.substringAfterLast('.')} label=${label?.take(40)} id=${resourceId?.substringAfterLast('/')})"
}
