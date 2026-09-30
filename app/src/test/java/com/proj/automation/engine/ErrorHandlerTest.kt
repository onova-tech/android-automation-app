package com.proj.automation.engine

import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.OnFailurePolicy
import com.proj.automation.parser.Step
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ErrorHandlerTest {

    private val errorHandler = ErrorHandler()
    private val context: ActionContext = mockk(relaxed = true)

    @Test
    fun `returns success on first attempt`() = runTest {
        val step = Step(action = ActionType.LOG)
        val result = errorHandler.executeWithPolicy(step, { _ -> StepResult(0, ActionType.LOG, true, 10) }, context)

        assertTrue(result.success)
    }

    @Test
    fun `retries on failure up to max attempts`() = runTest {
        var attempts = 0
        val step = Step(
            action = ActionType.CLICK,
            retries = 3,
            retryDelayMs = 10,
            onFailure = OnFailurePolicy.RETRY(maxAttempts = 3, delayMs = 10)
        )

        coEvery { context.throwIfCancelled() } just Runs

        val result = errorHandler.executeWithPolicy(step, {
            attempts++
            if (attempts < 3) {
                StepResult(0, ActionType.CLICK, false, 10, "Not found")
            } else {
                StepResult(0, ActionType.CLICK, true, 10)
            }
        }, context)

        assertTrue(result.success)
        assertEquals(3, attempts)
    }

    @Test
    fun `returns failure after all retries exhausted`() = runTest {
        val step = Step(
            action = ActionType.CLICK,
            retries = 2,
            retryDelayMs = 10,
            onFailure = OnFailurePolicy.RETRY(maxAttempts = 2, delayMs = 10)
        )

        val result = errorHandler.executeWithPolicy(step, {
            StepResult(0, ActionType.CLICK, false, 10, "Always fails")
        }, context)

        assertFalse(result.success)
    }

    @Test
    fun `RETRY policy uses its own attempts and delay, not step retries`() = runTest {
        var attempts = 0
        val step = Step(
            action = ActionType.CLICK,
            retries = 1,
            onFailure = OnFailurePolicy.RETRY(maxAttempts = 4, delayMs = 10)
        )

        val result = errorHandler.executeWithPolicy(step, {
            attempts++
            StepResult(0, ActionType.CLICK, false, 10, "Not found")
        }, context)

        assertFalse(result.success)
        assertEquals(4, attempts)
    }

    @Test
    fun `step retries apply under ABORT policy`() = runTest {
        var attempts = 0
        val step = Step(action = ActionType.CLICK, retries = 3, retryDelayMs = 10)

        val result = errorHandler.executeWithPolicy(step, {
            attempts++
            StepResult(0, ActionType.CLICK, attempts == 3, 10)
        }, context)

        assertTrue(result.success)
        assertEquals(3, attempts)
    }

    @Test
    fun `timeout interrupts a long-running attempt`() = runTest {
        val step = Step(action = ActionType.WAIT_FOR, timeoutMs = 100)

        val result = errorHandler.executeWithPolicy(step, {
            delay(10_000)
            StepResult(0, ActionType.WAIT_FOR, true, 10_000)
        }, context)

        assertFalse(result.success)
        assertEquals("Timeout after 100ms", result.errorMessage)
    }

    @Test
    fun `wait step is not cut off by the step timeout`() = runTest {
        val step = Step(action = ActionType.WAIT, timeoutMs = 100)

        val result = errorHandler.executeWithPolicy(step, {
            delay(1_000)
            StepResult(0, ActionType.WAIT, true, 1_000)
        }, context)

        assertTrue(result.success)
    }
}
