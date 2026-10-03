package com.proj.automation.dsl

import com.proj.automation.accessibility.AutomationBridge
import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionDispatcher
import com.proj.automation.engine.CancellationToken
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.ErrorHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.Step
import com.proj.automation.ui.Bounds
import com.proj.automation.ui.UiNode
import com.proj.automation.service.EventBus
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InterpreterTest {

    private val dispatcher: ActionDispatcher = mockk()
    private val automation: AutomationBridge = mockk(relaxed = true)
    private val token = CancellationToken()
    /** Element texts that "exist" on the fake screen */
    private val onScreen = mutableSetOf<String>()

    private val context = ActionContext(automation, mockk(relaxed = true), token, snapshot = {
        UiNode(bounds = Bounds(0, 0, 100, 100), children = onScreen.map { UiNode(text = it, clickable = true) })
    })
    private val parser = DslParser()

    /** Steps the dispatcher received, after template rendering */
    private val dispatched = mutableListOf<Step>()

    /** Texts of elements whose click fails */
    private val failingClicks = mutableSetOf<String>()

    @BeforeEach
    fun setup() {
        every { automation.getRootNode() } returns null
        coEvery { dispatcher.dispatch(any(), any()) } answers {
            val step = firstArg<Step>()
            dispatched += step
            val text = step.target?.hints?.text
            when {
                text != null && text in failingClicks ->
                    StepResult(0, step.action, false, 1, "Element not found", ErrorCode.E_NOT_FOUND)
                step.action.yamlValue == "read_text" ->
                    StepResult(0, step.action, true, 1, details = mapOf("value" to "R$ 12,34"))
                else -> StepResult(0, step.action, true, 1)
            }
        }
    }

    private fun interpreter(limits: RunLimits = RunLimits()) =
        Interpreter(dispatcher, ErrorHandler(), mockk<EventBus>(relaxed = true), limits)

    private suspend fun run(yaml: String, args: Map<String, String> = emptyMap(), limits: RunLimits = RunLimits()) =
        interpreter(limits).run(parser.parse(yaml.trimIndent()), args, context)

    @Test
    fun `renders arguments and variables into action parameters`() = runTest {
        val result = run(
            """
            params: { to: {}, text: {} }
            steps:
              - set: { url: "https://wa.me/${'$'}{to}?text=${'$'}{text|urlencode}" }
              - log: { message: "${'$'}{url}" }
            """,
            mapOf("to" to "5511999", "text" to "on my way")
        )
        assertEquals(RunStatus.SUCCEEDED, result.status)
        assertEquals("https://wa.me/5511999?text=on%20my%20way", dispatched.single().parameters["message"])
    }

    @Test
    fun `program arguments are bound literally, never rendered`() = runTest {
        val result = run(
            """
            params: { text: {} }
            steps:
              - log: { message: "${'$'}{text}" }
            """,
            mapOf("text" to "\${secret}")
        )
        assertEquals(RunStatus.SUCCEEDED, result.status)
        assertEquals("\${secret}", dispatched.single().parameters["message"])
    }

    @Test
    fun `if takes the branch matching the screen`() = runTest {
        onScreen += "Esconder saldo."
        run(
            """
            steps:
              - if:
                  exists: { text: "Esconder saldo." }
                  then: [ { log: { message: "visible" } } ]
                  else: [ { log: { message: "hidden" } } ]
            """
        )
        assertEquals("visible", dispatched.single().parameters["message"])
    }

    @Test
    fun `first_that_works falls back to the next alternative`() = runTest {
        failingClicks += "A"
        val result = run(
            """
            steps:
              - first_that_works:
                  - click: { target: { hints: { text: "A" } } }
                  - click: { target: { hints: { text: "B" } } }
            """
        )
        assertEquals(RunStatus.SUCCEEDED, result.status)
        assertEquals(listOf(false, true), result.steps.map { it.success })
    }

    @Test
    fun `first_that_works fails when every alternative fails`() = runTest {
        failingClicks += listOf("A", "B")
        val result = run(
            """
            steps:
              - first_that_works:
                  - click: { target: { hints: { text: "A" } } }
                  - click: { target: { hints: { text: "B" } } }
            """
        )
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals(ErrorCode.E_NOT_FOUND, result.errorCode)
    }

    @Test
    fun `try exposes the error to on_error`() = runTest {
        failingClicks += "A"
        val result = run(
            """
            steps:
              - try:
                  do: [ { click: { target: { hints: { text: "A" } } } } ]
                  on_error: [ { return: "failed with ${'$'}{error.code}" } ]
            """
        )
        assertEquals(RunStatus.SUCCEEDED, result.status)
        assertEquals("failed with E_NOT_FOUND", result.returnValue)
    }

    @Test
    fun `flows get their own scope and return values through into`() = runTest {
        val result = run(
            """
            flows:
              greet:
                params: { name: {}, suffix: { default: "!" } }
                steps:
                  - return: "hi ${'$'}{name}${'$'}{suffix}"
            steps:
              - set: { secret: "caller only" }
              - call: { flow: greet, with: { name: "Maria" }, into: msg }
              - return: "${'$'}{msg}"
            """
        )
        assertEquals("hi Maria!", result.returnValue)
    }

    @Test
    fun `a flow cannot read its caller's variables`() = runTest {
        val result = run(
            """
            flows:
              leak:
                steps:
                  - return: "${'$'}{secret}"
            steps:
              - set: { secret: "caller only" }
              - call: { flow: leak }
            """
        )
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals(ErrorCode.E_EXPR, result.errorCode)
    }

    @Test
    fun `read_text binds its value with into`() = runTest {
        onScreen += "Saldo"
        val result = run(
            """
            steps:
              - read_text: { target: { hints: { text: "Saldo" } }, into: balance }
              - return: "Balance ${'$'}{balance}"
            """
        )
        assertEquals("Balance R$ 12,34", result.returnValue)
    }

    @Test
    fun `failed expect turns a successful action into E_VERIFY_FAILED`() = runTest {
        val result = run(
            """
            steps:
              - click:
                  target: { hints: { text: "Enviar" } }
                  expect: { exists: { text: "sent" } }
              - log: { message: "not reached" }
            """
        )
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals(ErrorCode.E_VERIFY_FAILED, result.errorCode)
        assertEquals(1, dispatched.size)
    }

    @Test
    fun `assert failure carries its message`() = runTest {
        val result = run(
            """
            steps:
              - assert: { exists: { text: "Saldo" }, message: "balance screen not found" }
            """
        )
        assertEquals(ErrorCode.E_VERIFY_FAILED, result.errorCode)
        assertEquals("balance screen not found", result.message)
    }

    @Test
    fun `on_failure continue keeps going`() = runTest {
        failingClicks += "A"
        val result = run(
            """
            steps:
              - click: { target: { hints: { text: "A" } }, on_failure: continue }
              - log: { message: "after" }
            """
        )
        assertEquals(RunStatus.SUCCEEDED, result.status)
        assertEquals(2, dispatched.size)
    }

    @Test
    fun `action limit stops the run and cannot be caught by try`() = runTest {
        val steps = (1..5).joinToString("\n") { "      - log: { message: \"$it\" }" }
        val result = run(
            "steps:\n  - try:\n      do:\n$steps\n      on_error: [ { return: \"handled\" } ]",
            limits = RunLimits(maxActions = 3)
        )
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals(ErrorCode.E_BUDGET, result.errorCode)
        assertEquals(3, dispatched.size)
    }

    @Test
    fun `call depth is limited`() = runTest {
        val result = run(
            """
            flows:
              a: { steps: [ { call: { flow: b } } ] }
              b: { steps: [ { call: { flow: c } } ] }
              c: { steps: [ { log: { message: "deep" } } ] }
            steps:
              - call: { flow: a }
            """,
            limits = RunLimits(maxCallDepth = 2)
        )
        assertEquals(ErrorCode.E_BUDGET, result.errorCode)
    }

    @Test
    fun `missing required argument fails with E_EXPR`() = runTest {
        val result = run("params: { to: {} }\nsteps:\n  - log: { message: \"x\" }")
        assertEquals(ErrorCode.E_EXPR, result.errorCode)
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun `a capability guard denial ends the run and cannot be caught`() = runTest {
        val guard = com.proj.automation.plugin.ActionGuard { step, _ ->
            if (step.action.yamlValue == "click") "not approved" else null
        }
        val program = parser.parse(
            "steps:\n  - try:\n      do: [ { click: { target: { hints: { text: A } } } } ]\n      on_error: [ { return: handled } ]"
        )
        val result = interpreter().run(program, emptyMap(), context, guard)
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals(ErrorCode.E_CAPABILITY, result.errorCode)
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun `cancellation ends the run as cancelled`() = runTest {
        coEvery { dispatcher.dispatch(any(), any()) } answers {
            token.cancel()
            StepResult(0, firstArg<Step>().action, true, 1)
        }
        val result = run("steps:\n  - back: {}\n  - home: {}")
        assertEquals(RunStatus.CANCELLED, result.status)
        assertEquals(1, result.steps.size)
    }

    @Test
    fun `coroutine cancellation propagates`() = runTest {
        coEvery { dispatcher.dispatch(any(), any()) } throws CancellationException("job cancelled")
        var thrown = false
        try {
            run("steps:\n  - back: {}")
        } catch (e: CancellationException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
