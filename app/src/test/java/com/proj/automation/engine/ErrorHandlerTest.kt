package com.proj.automation.engine

import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.OnFailurePolicy
import com.proj.automation.parser.Step
import io.mockk.*
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
}
