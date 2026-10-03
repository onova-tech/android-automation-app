package com.proj.automation.engine

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.accessibility.AutomationBridge
import com.proj.automation.dsl.Scope
import com.proj.automation.dsl.Templates
import com.proj.automation.engine.actions.ReadListHandler
import com.proj.automation.engine.actions.ScrollUntilHandler
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import com.proj.automation.resolve.Hints
import com.proj.automation.resolve.Target
import com.proj.automation.ui.Bounds
import com.proj.automation.ui.UiNode
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ListActionsTest {

    private val listRef: AccessibilityNodeInfo = mockk(relaxed = true)
    private val automation: AutomationBridge = mockk(relaxed = true)

    /** A statement split across pages; scrolling moves to the next page until the last one */
    private val pages = listOf(
        listOf("Day 30", "Pix received\n10:02 · Pix\n+ R$ 100,00", "Market\n09:15 · Debit\nR$ 52,30"),
        listOf("Market\n09:15 · Debit\nR$ 52,30", "Pharmacy\n08:40 · Debit\nR$ 23,10", "Day 29"),
        listOf("Bus\n18:00 · Debit\nR$ 4,40", "Bakery\n07:30 · Debit\nR$ 9,90")
    )
    private var page = 0

    private fun screen() = UiNode(
        bounds = Bounds(0, 0, 1000, 2000),
        children = listOf(
            UiNode(contentDescription = "Voltar", clickable = true, bounds = Bounds(0, 0, 100, 100)),
            UiNode(
                scrollable = true, ref = listRef, bounds = Bounds(0, 200, 1000, 2000),
                children = pages[page].mapIndexed { i, label ->
                    UiNode(
                        contentDescription = label, clickable = label.contains("R$"),
                        bounds = Bounds(0, 300 + i * 300, 1000, 580 + i * 300), ref = mockk<AccessibilityNodeInfo>()
                    )
                }
            )
        )
    )

    private val context = ActionContext(automation, mockk(relaxed = true), CancellationToken(), snapshot = { screen() })

    init {
        every { automation.scrollForward(listRef) } answers { if (page < pages.lastIndex) { page++; true } else true }
    }

    private fun step(action: ActionType, vararg params: Pair<String, String>, target: Target? = null) =
        Step(action, parameters = params.toMap(), target = target)

    @Test
    fun `read_list collects matching items across scrolls without duplicates`() = runTest {
        val result = ReadListHandler().execute(step(ActionType.READ_LIST, "match" to "R\\$", "max" to "10"), context)

        assertTrue(result.success)
        @Suppress("UNCHECKED_CAST")
        val items = result.details["value"] as List<String>
        assertEquals(5, items.size)
        assertEquals("Pix received\n10:02 · Pix\n+ R$ 100,00", items.first())
        assertEquals(1, items.count { it.startsWith("Market") })
        assertEquals(2, result.details["scrolls"])
    }

    @Test
    fun `read_list stops at max and can skip scrolling`() = runTest {
        val two = ReadListHandler().execute(step(ActionType.READ_LIST, "match" to "R\\$", "max" to "2"), context)
        assertEquals(2, (two.details["value"] as List<*>).size)
        assertEquals(0, two.details["scrolls"])

        // clickable items on the first page: "Voltar" plus the two entries
        val noScroll = ReadListHandler().execute(step(ActionType.READ_LIST, "role" to "button", "scroll" to "false"), context)
        assertEquals(listOf("Voltar", "Pix received", "Market"), (noScroll.details["value"] as List<*>).map { it.toString().lines().first() })
    }

    @Test
    fun `read_list rejects an invalid pattern`() = runTest {
        val result = ReadListHandler().execute(step(ActionType.READ_LIST, "match" to "(unclosed"), context)
        assertFalse(result.success)
        assertEquals(ErrorCode.E_ACTION_FAILED, result.errorCode)
    }

    @Test
    fun `scroll_until scrolls until the target appears`() = runTest {
        val result = ScrollUntilHandler().execute(
            step(ActionType.SCROLL_UNTIL, target = Target(hints = Hints(contentDescription = "Bakery\n07:30 · Debit\nR$ 9,90"))),
            context
        )
        assertTrue(result.success, result.errorMessage)
        assertEquals(2, result.details["scrolls"])
    }

    @Test
    fun `scroll_until gives up at the end of the list`() = runTest {
        val result = ScrollUntilHandler().execute(
            step(ActionType.SCROLL_UNTIL, target = Target(hints = Hints(text = "Not there"))), context
        )
        assertFalse(result.success)
        assertEquals(ErrorCode.E_NOT_FOUND, result.errorCode)
        assertEquals(2, result.details["scrolls"]) // a third attempt changed nothing and is not counted
    }

    @Test
    fun `list variables render one per line and support size and index`() {
        val scope = Scope(mapOf("items" to listOf("a", "b", "c")))
        assertEquals("a\nb\nc", Templates.render("\${items}", scope))
        assertEquals("3 items, first a", Templates.render("\${items.size} items, first \${items.0}", scope))
    }
}
