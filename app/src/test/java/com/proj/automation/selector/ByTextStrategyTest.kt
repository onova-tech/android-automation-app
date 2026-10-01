package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.parser.Selector
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ByTextStrategyTest {

    private val strategy = ByTextStrategy()

    @Test
    fun `finds node by matching text (case-insensitive)`() {
        val node: AccessibilityNodeInfo = mockk()
        every { node.text } returns "Hello World"
        every { node.childCount } returns 0

        val selector = Selector.ByText("hello world")
        val root: AccessibilityNodeInfo = mockk()
        every { root.childCount } returns 1
        every { root.getChild(0) } returns node
        every { root.text } returns "Root"

        val results = strategy.find(root, selector)
        assertEquals(node, results[0])
    }

    @Test
    fun `returns empty list when no node matches`() {
        val node: AccessibilityNodeInfo = mockk()
        every { node.text } returns "Different Text"
        every { node.childCount } returns 0

        val selector = Selector.ByText("Hello World")
        val root: AccessibilityNodeInfo = mockk()
        every { root.childCount } returns 1
        every { root.getChild(0) } returns node
        every { root.text } returns "Root"

        val results = strategy.find(root, selector)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `returns empty list for null root`() {
        val selector = Selector.ByText("Hello")
        val results = strategy.find(null, selector)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `matches compares text case-insensitively`() {
        val node: AccessibilityNodeInfo = mockk()
        every { node.text } returns "Button"

        assertTrue(strategy.matches(node, Selector.ByText("button")))
        assertTrue(strategy.matches(node, Selector.ByText("BUTTON")))
        assertTrue(strategy.matches(node, Selector.ByText("Button")))
        assertFalse(strategy.matches(node, Selector.ByText("Other")))
    }
}
