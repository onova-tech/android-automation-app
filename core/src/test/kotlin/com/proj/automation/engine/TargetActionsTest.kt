package com.proj.automation.engine

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
import com.proj.automation.replay.ScreenDevice
import com.proj.automation.service.EventBus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TargetActionsTest {

    private val screen = UiNode(
        bounds = Bounds(0, 0, 1000, 2000),
        children = listOf(
            UiNode(text = "Mensagem", className = "android.widget.EditText", clickable = true, editable = true,
                bounds = Bounds(0, 1900, 800, 2000)),
            UiNode(contentDescription = "Enviar", className = "android.widget.ImageButton", clickable = true,
                bounds = Bounds(850, 1900, 1000, 2000)),
            UiNode(contentDescription = "OK", className = "android.widget.Button", clickable = true, bounds = Bounds(0, 0, 10, 10)),
            UiNode(contentDescription = "OK", className = "android.widget.Button", clickable = true, bounds = Bounds(0, 20, 10, 30))
        )
    )

    private val device = ScreenDevice(screen)
    private val context = ActionContext(device, EventBus(), CancellationToken())

    @Test
    fun `click resolves a target and taps that element`() = runTest {
        val step = Step(ActionType.CLICK, target = Target(intent = "enviar a mensagem", role = Role.BUTTON, region = "bottom-right"))

        val result = ClickHandler().execute(step, context)

        assertTrue(result.success, result.errorMessage)
        assertEquals("ranked", result.strategy)
        assertEquals(listOf("click:Enviar"), device.interactions)
    }

    @Test
    fun `click refuses an ambiguous target with E_LOW_CONFIDENCE`() = runTest {
        val step = Step(ActionType.CLICK, target = Target(hints = Hints(contentDescription = "OK"), intent = "ok"))

        val result = ClickHandler().execute(step, context)

        assertFalse(result.success)
        assertEquals(ErrorCode.E_LOW_CONFIDENCE, result.errorCode)
        assertTrue(device.interactions.isEmpty())
    }

    @Test
    fun `read_text reads the content description of a target`() = runTest {
        val step = Step(ActionType.READ_TEXT, target = Target(hints = Hints(contentDescription = "Enviar")))

        val result = ReadTextHandler().execute(step, context)

        assertEquals("Enviar", result.details["value"])
        assertEquals("hint:content_description", result.strategy)
    }

    @Test
    fun `type writes into the targeted field`() = runTest {
        val step = Step(ActionType.TYPE, parameters = mapOf("text" to "oi"), target = Target(intent = "mensagem", role = Role.EDIT_TEXT))

        val result = TypeHandler().execute(step, context)

        assertTrue(result.success, result.errorMessage)
        assertEquals(listOf("type:Mensagem"), device.interactions)
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
        assertTrue((action.expect as Condition.Exists).negate)
    }

    @Test
    fun `parser rejects bad targets`() {
        assertThrows<YamlParseException> {
            DslParser().parse("steps:\n  - click: { target: { role: button } }")
        }
        assertThrows<YamlParseException> {
            DslParser().parse("steps:\n  - click: { target: { intent: \"\${x|nope}\" } }")
        }
    }
}
