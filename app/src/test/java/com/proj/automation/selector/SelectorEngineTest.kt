package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.parser.Selector
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SelectorEngineTest {

    private val engine = SelectorEngine()

    @Test
    fun `returns null when root is null`() {
        val result = engine.resolveWithDetails(Selector.ByText("test"), null)
        assertEquals("null_tree", result.strategy)
        assertNull(result.node)
    }

    @Test
    fun `returns all strategies attempted when no match`() {
        val node: AccessibilityNodeInfo = mockk()
        every { node.text } returns "Different"
        every { node.contentDescription } returns null
        every { node.viewIdResourceName } returns null
        every { node.className } returns null
        every { node.childCount } returns 0

        val root: AccessibilityNodeInfo = mockk()
        every { root.childCount } returns 1
        every { root.getChild(0) } returns node
        every { root.text } returns null
        every { root.contentDescription } returns null
        every { root.viewIdResourceName } returns null
        every { root.className } returns null

        val result = engine.resolveWithDetails(Selector.ByText("test"), root)

        assertEquals("none_matched", result.strategy)
        assertFalse(result.success)
        assertTrue(result.fallbacksAttempted.isNotEmpty())
    }
}
