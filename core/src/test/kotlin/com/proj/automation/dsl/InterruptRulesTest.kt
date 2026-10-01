package com.proj.automation.dsl

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionDispatcher
import com.proj.automation.engine.CancellationToken
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.ErrorHandler
import com.proj.automation.engine.buildHandlerRegistry
import com.proj.automation.parser.YamlParseException
import com.proj.automation.parser.YamlParser
import com.proj.automation.replay.ScreenDevice
import com.proj.automation.replay.Transition
import com.proj.automation.service.EventBus
import com.proj.automation.ui.Bounds
import com.proj.automation.ui.UiNode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InterruptRulesTest {

    private val parser = DslParser()
    private fun rules(yaml: String) = parser.parseInterrupts(YamlParser().loadDocument(yaml.trimIndent()))

    private fun screen(vararg labels: String) = UiNode(
        bounds = Bounds(0, 0, 1000, 2000),
        children = labels.mapIndexed { i, l ->
            UiNode(text = l, className = "android.widget.Button", clickable = true, bounds = Bounds(0, i * 100, 500, i * 100 + 80))
        }
    )

    private val home = screen("Enviar")
    private val dialog = screen("Enviar", "Agora não")

    private val dismissRule = """
        rules:
          - name: dialog
            when: { exists: { text: "Agora não" } }
            do:
              - click: { target: { hints: { text: "Agora não" } } }
    """

    private suspend fun runWith(device: ScreenDevice, program: Program, now: () -> Long = { 0 }): RunResult {
        var t = 0L
        val ctx = ActionContext(device, EventBus(), CancellationToken(), clock = { t }, sleep = { t += it })
        return Interpreter(ActionDispatcher(buildHandlerRegistry()), ErrorHandler(), EventBus()).run(program, emptyMap(), ctx)
    }

    private fun program(steps: String, rulesYaml: String = dismissRule) =
        parser.parse(steps.trimIndent()).copy(interrupts = rules(rulesYaml))

    @Test
    fun `a rule clears a dialog before the action`() = runTest {
        val device = ScreenDevice(dialog, listOf(Transition("click", "Agora não", home)))
        val result = runWith(device, program("steps:\n  - click: { target: { hints: { text: Enviar } } }"))
        assertEquals(RunStatus.SUCCEEDED, result.status, result.message)
        assertEquals(listOf("click:Agora não", "click:Enviar"), device.interactions)
    }

    @Test
    fun `an action that failed is retried once after a rule fires`() = runTest {
        // the dialog appears only after the first tap, hiding the next target
        val afterTap = screen("Agora não")
        val device = ScreenDevice(home, listOf(
            Transition("click", "Enviar", afterTap),
            Transition("click", "Agora não", screen("OK"))
        ))
        val result = runWith(device, program("steps:\n  - click: { target: { hints: { text: Enviar } } }\n  - click: { target: { hints: { text: OK } } }"))
        assertEquals(RunStatus.SUCCEEDED, result.status, result.message)
        assertEquals(listOf("click:Enviar", "click:Agora não", "click:OK"), device.interactions)
    }

    @Test
    fun `max_per_run bounds a rule that keeps matching`() = runTest {
        // the dialog never goes away
        val device = ScreenDevice(dialog)
        val result = runWith(device, program(
            "steps:\n  - click: { target: { hints: { text: Enviar } } }\n  - click: { target: { hints: { text: Enviar } } }"
        ))
        assertEquals(RunStatus.SUCCEEDED, result.status)
        assertEquals(2, device.interactions.count { it == "click:Agora não" }) // default max_per_run = 2
    }

    @Test
    fun `a failing rule step does not fail the skill`() = runTest {
        val device = ScreenDevice(dialog)
        val broken = """
            rules:
              - name: broken
                when: { exists: { text: "Agora não" } }
                do:
                  - click: { target: { hints: { text: "Not there" } } }
        """
        val result = runWith(device, program("steps:\n  - click: { target: { hints: { text: Enviar } } }", broken))
        assertEquals(RunStatus.SUCCEEDED, result.status, result.message)
    }

    @Test
    fun `rules do not see the skill's variables`() = runTest {
        val device = ScreenDevice(dialog)
        val peeking = """
            rules:
              - name: peek
                when: { is_set: secret }
                do:
                  - back: {}
        """
        val result = runWith(device, program("steps:\n  - set: { secret: x }\n  - click: { target: { hints: { text: Enviar } } }", peeking))
        assertEquals(RunStatus.SUCCEEDED, result.status)
        assertFalse("back" in device.interactions)
    }

    @Test
    fun `rule steps are limited to harmless actions`() {
        for (step in listOf("type: { text: x }", "open_url: { url: \"https://x.com\" }", "launch_app: { package: a.b }", "call: { flow: f }", "set: { a: b }")) {
            val e = assertThrows<YamlParseException>(step) {
                rules("rules:\n  - name: r\n    when: { exists: { text: X } }\n    do:\n      - $step")
            }
            assertTrue(e.message!!.contains("not allowed"), e.message)
        }
        assertThrows<YamlParseException> { rules("rules:\n  - name: r\n    do: [ { back: {} } ]") }
        assertThrows<YamlParseException> { rules("rules:\n  - { name: r, when: { exists: { text: X } }, do: [ { back: {} } ], max_per_run: 50 }") }
        assertThrows<YamlParseException> { rules("rules:\n  - { name: r, when: { exists: { text: X } }, do: [ { back: {} } ] }\n  - { name: r, when: { exists: { text: Y } }, do: [ { back: {} } ] }") }
    }

    @Test
    fun `no rules, no extra interactions`() = runTest {
        val device = ScreenDevice(dialog)
        val result = runWith(device, parser.parse("steps:\n  - click: { target: { hints: { text: Enviar } } }"))
        assertEquals(RunStatus.SUCCEEDED, result.status)
        assertEquals(listOf("click:Enviar"), device.interactions)
    }

    @Test
    fun `error code is unchanged when no rule helps`() = runTest {
        val device = ScreenDevice(home)
        val result = runWith(device, program("steps:\n  - click: { target: { hints: { text: Missing } } }"))
        assertEquals(ErrorCode.E_NOT_FOUND, result.errorCode)
        assertTrue(device.interactions.isEmpty())
    }
}
