package com.proj.automation.engine

import com.proj.automation.accessibility.AutomationBridge
import com.proj.automation.engine.models.ExecutionResult
import com.proj.automation.parser.OnFailurePolicy
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import com.proj.automation.parser.Workflow
import com.proj.automation.selector.SelectorEngine
import com.proj.automation.service.EventBus
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ExecutionEngineTest {

    private lateinit var engine: ExecutionEngine
    private val dispatcher: ActionDispatcher = mockk()
    private val errorHandler: ErrorHandler = ErrorHandler()
    private val eventBus: EventBus = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        AutomationBridge.init(mockk<AutomationBridge>(relaxed = true))
        engine = ExecutionEngine(dispatcher, errorHandler, eventBus)
    }

    @Test
    fun `executes all steps in order`() = runTest {
        val steps = listOf(
            Step(action = ActionType.BACK),
            Step(action = ActionType.WAIT),
            Step(action = ActionType.LOG, parameters = mapOf("message" to "done"))
        )
        val workflow = Workflow(name = "Test", steps = steps)

        coEvery { dispatcher.dispatch(any(), any()) } returnsMany listOf(
            StepResult(0, ActionType.BACK, true, 10),
            StepResult(1, ActionType.WAIT, true, 1000),
            StepResult(2, ActionType.LOG, true, 1)
        )

        val result = engine.execute(workflow)

        assertTrue(result.completedSuccessfully)
        assertEquals(3, result.steps.size)
        assertEquals(3, result.stepCount)
    }

    @Test
    fun `stops on failure with ABORT policy`() = runTest {
        val steps = listOf(
            Step(action = ActionType.CLICK, onFailure = OnFailurePolicy.ABORT),
            Step(action = ActionType.BACK)
        )
        val workflow = Workflow(name = "Test", steps = steps)

        coEvery { dispatcher.dispatch(any(), any()) } returnsMany listOf(
            StepResult(0, ActionType.CLICK, false, 100, errorMessage = "Not found"),
            StepResult(1, ActionType.BACK, true, 10)
        )

        val result = engine.execute(workflow)

        assertFalse(result.completedSuccessfully)
        assertEquals(1, result.steps.size) // Only first step executed
    }

    @Test
    fun `empty workflow returns immediately`() = runTest {
        val workflow = Workflow(name = "Empty", steps = emptyList())
        val result = engine.execute(workflow)
        assertTrue(result.completedSuccessfully)
        assertEquals(0, result.steps.size)
    }

    @Test
    fun `a previous cancel does not cancel the next run`() = runTest {
        engine.cancel()
        val workflow = Workflow(name = "Test", steps = listOf(Step(action = ActionType.BACK)))
        coEvery { dispatcher.dispatch(any(), any()) } returns StepResult(0, ActionType.BACK, true, 10)

        val result = engine.execute(workflow)

        assertFalse(result.cancelled)
        assertTrue(result.completedSuccessfully)
    }

    @Test
    fun `cancel during a run marks the result cancelled`() = runTest {
        val steps = listOf(Step(action = ActionType.BACK), Step(action = ActionType.HOME))
        coEvery { dispatcher.dispatch(any(), any()) } answers {
            engine.cancel()
            StepResult(0, ActionType.BACK, true, 10)
        }

        val result = engine.execute(Workflow(name = "Test", steps = steps))

        assertTrue(result.cancelled)
        assertEquals(1, result.steps.size)
    }

    @Test
    fun `coroutine cancellation propagates instead of being recorded as a failed step`() = runTest {
        val workflow = Workflow(name = "Test", steps = listOf(Step(action = ActionType.BACK)))
        coEvery { dispatcher.dispatch(any(), any()) } throws CancellationException("job cancelled")

        var thrown: Throwable? = null
        try {
            engine.execute(workflow)
        } catch (e: CancellationException) {
            thrown = e
        }

        assertNotNull(thrown)
    }
}
