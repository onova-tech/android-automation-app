package com.proj.automation.engine

import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.accessibility.AutomationBridge
import com.proj.automation.dsl.Condition
import com.proj.automation.dsl.DslParser
import com.proj.automation.dsl.Node
import com.proj.automation.engine.actions.ClickHandler
import com.proj.automation.engine.actions.ReadTextHandler
import com.proj.automation.engine.actions.TypeHandler
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import com.proj.automation.parser.YamlParseException
import com.proj.automation.resolve.Hints
import com.proj.automation.resolve.Role
import com.proj.automation.resolve.Target
import com.proj.automation.ui.Bounds
import com.proj.automation.ui.UiNode
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TargetActionsTest {

    private val sendRef: AccessibilityNodeInfo = mockk(relaxed = true)
    private val boxRef: AccessibilityNodeInfo = mockk(relaxed = true)
    private val automation: AutomationBridge = mockk(relaxed = true)

    private val screen = UiNode(
        bounds = Bounds(0, 0, 1000, 2000),
        children = listOf(
            UiNode(text = "Mensagem", className = "android.widget.EditText", clickable = true, editable = true,
                bounds = Bounds(0, 1900, 800, 2000), ref = boxRef),
            UiNode(contentDescription = "Enviar", className = "android.widget.ImageButton", clickable = true,
                bounds = Bounds(850, 1900, 1000, 2000), ref = sendRef),
            UiNode(contentDescription = "OK", className = "android.widget.Button", clickable = true, bounds = Bounds(0, 0, 10, 10)),
            UiNode(contentDescription = "OK", className = "android.widget.Button", clickable = true, bounds = Bounds(0, 20, 10, 30))
        )
    )

    private val context = ActionContext(
        automation, mockk(), mockk(relaxed = true), CancellationToken(), snapshot = { screen }
    )

    @Test
    fun `click resolves a target and taps the live node`() = runTest {
        every { automation.click(sendRef) } returns true
        every { sendRef.text } returns null
        val step = Step(ActionType.CLICK, target = Target(intent = "enviar a mensagem", role = Role.BUTTON, region = "bottom-right"))

        val result = ClickHandler().execute(step, context)

        assertTrue(result.success, result.errorMessage)
        assertEquals("ranked", result.strategy)
        verify { automation.click(sendRef) }
    }

    @Test
    fun `click refuses an ambiguous target with E_LOW_CONFIDENCE`() = runTest {
        val step = Step(ActionType.CLICK, target = Target(hints = Hints(contentDescription = "OK"), intent = "ok"))

        val result = ClickHandler().execute(step, context)

        assertFalse(result.success)
        assertEquals(ErrorCode.E_LOW_CONFIDENCE, result.errorCode)
        verify(exactly = 0) { automation.click(any()) }
    }

    @Test
    fun `read_text reads the content description of a target`() = runTest {
        every { sendRef.text } returns null
        every { sendRef.contentDescription } returns "Enviar"
        val step = Step(ActionType.READ_TEXT, target = Target(hints = Hints(contentDescription = "Enviar")))

        val result = ReadTextHandler().execute(step, context)

        assertEquals("Enviar", result.details["value"])
        assertEquals("hint:content_description", result.strategy)
    }

    @Test
    fun `type writes into the targeted field`() = runTest {
        every { automation.setText(boxRef, "oi") } returns true
        val step = Step(ActionType.TYPE, parameters = mapOf("text" to "oi"), target = Target(intent = "mensagem", role = Role.EDIT_TEXT))

        val result = TypeHandler().execute(step, context)

        assertTrue(result.success, result.errorMessage)
        verify { automation.setText(boxRef, "oi") }
    }

    @Test
    fun `parser reads targets on actions and in conditions`() {
        val program = DslParser().parse(
            """
            params: { who: {} }
            steps:
              - click:
                  target:
                    intent: "send ${'$'}{who}"
                    role: button
                    hints: { content_description: "Enviar" }
                    region: bottom-right
                    min_confidence: 0.7
                  expect: { not_exists: { target: { intent: "error dialog", role: text } } }
            """.trimIndent()
        )
        val action = program.body.single() as Node.Action
        val target = action.step.target!!
        assertEquals(Role.BUTTON, target.role)
        assertEquals(0.7, target.minConfidence)
        assertEquals("send \${who}", target.intent)
        assertTrue((action.expect as Condition.TargetExists).negate)
    }

    @Test
    fun `parser rejects selector plus target and bad targets`() {
        assertThrows<YamlParseException> {
            DslParser().parse("steps:\n  - click: { selector: { text: a }, target: { intent: a } }")
        }
        assertThrows<YamlParseException> {
            DslParser().parse("steps:\n  - click: { target: { role: button } }")
        }
        assertThrows<YamlParseException> {
            DslParser().parse("steps:\n  - click: { target: { intent: \"\${x|nope}\" } }")
        }
    }
}
