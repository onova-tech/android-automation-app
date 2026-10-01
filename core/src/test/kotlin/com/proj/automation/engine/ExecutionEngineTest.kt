package com.proj.automation.engine

import com.proj.automation.replay.ScreenDevice
import com.proj.automation.dsl.DslParser
import com.proj.automation.dsl.RunStatus
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.Step
import com.proj.automation.plugin.PackageBuilder
import com.proj.automation.service.EventBus
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ExecutionEngineTest {

    private lateinit var engine: ExecutionEngine
    private val dispatcher: ActionDispatcher = mockk()
    private val parser = DslParser()

    @BeforeEach
    fun setup() {
        engine = ExecutionEngine(dispatcher, ErrorHandler(), EventBus(), device = { ScreenDevice(null) })
        coEvery { dispatcher.dispatch(any(), any()) } answers { StepResult(0, firstArg<Step>().action, true, 1) }
    }

    @Test
    fun `runs a program and returns its value`() = runTest {
        val result = engine.run(parser.parse("params: { x: {} }\nsteps:\n  - back: {}\n  - return: \"got \${x}\""), mapOf("x" to "1"))
        assertEquals(RunStatus.SUCCEEDED, result.status)
        assertEquals("got 1", result.returnValue)
    }

    @Test
    fun `a previous cancel does not cancel the next run`() = runTest {
        engine.cancel()
        assertEquals(RunStatus.SUCCEEDED, engine.run(parser.parse("steps:\n  - back: {}")).status)
    }

    @Test
    fun `cancel during a run marks the result cancelled`() = runTest {
        coEvery { dispatcher.dispatch(any(), any()) } answers {
            engine.cancel()
            StepResult(0, firstArg<Step>().action, true, 1)
        }
        val result = engine.run(parser.parse("steps:\n  - back: {}\n  - home: {}"))
        assertEquals(RunStatus.CANCELLED, result.status)
        assertEquals(1, result.steps.size)
    }

    @Test
    fun `coroutine cancellation propagates`() = runTest {
        coEvery { dispatcher.dispatch(any(), any()) } throws CancellationException("job cancelled")
        var thrown = false
        try {
            engine.run(parser.parse("steps:\n  - back: {}"))
        } catch (e: CancellationException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `no device means E_DEVICE`() = runTest {
        val offline = ExecutionEngine(dispatcher, ErrorHandler(), EventBus(), device = { null })
        assertEquals(ErrorCode.E_DEVICE, offline.run(parser.parse("steps:\n  - back: {}")).errorCode)
    }

    @Test
    fun `skills run under the plugin's capabilities`() = runTest {
        val plugin = PackageBuilder.build(File("../plugins/whatsapp"), File("../plugins/libraries")).plugin
        // the foreground app is unknown (no screen), so the first UI-reading action is refused
        val result = engine.runSkill(plugin, "send", mapOf("phone" to "5511999", "text" to "hi"))
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals(ErrorCode.E_CAPABILITY, result.errorCode)
        // open_url to wa.me was allowed and dispatched before that
        coVerify { dispatcher.dispatch(match { it.parameters["url"] == "https://wa.me/5511999?text=hi" }, any()) }
        assertEquals(RunStatus.FAILED, engine.runSkill(plugin, "nope", emptyMap()).status)
    }
}
