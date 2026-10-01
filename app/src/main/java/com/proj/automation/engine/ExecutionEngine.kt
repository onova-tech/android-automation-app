package com.proj.automation.engine

import com.proj.automation.engine.models.ExecutionResult
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.OnFailurePolicy
import com.proj.automation.parser.Workflow
import com.proj.automation.service.EventBus
import com.proj.automation.selector.SelectorEngine
import kotlin.coroutines.cancellation.CancellationException

/**
 * Core step-loop orchestrator that iterates over parsed workflow steps,
 * dispatches each to the appropriate action handler, applies retry/timeout/error
 * policies, and reports execution results.
 */
class ExecutionEngine(
    private val actionDispatcher: ActionDispatcher,
    private val errorHandler: ErrorHandler,
    private val eventBus: EventBus
) {

    /** Token for the current run; replaced on each [execute] so a previous Stop doesn't carry over. */
    @Volatile
    private var cancellationToken = CancellationToken()

    /**
     * Execute a parsed workflow. Returns the full execution result with
     * per-step results, success/failure status, and timing.
     */
    suspend fun execute(workflow: Workflow): ExecutionResult {
        val token = CancellationToken().also { cancellationToken = it }
        val result = ExecutionResult(
            workflowName = workflow.name,
            startTime = System.currentTimeMillis()
        )

        if (workflow.steps.isEmpty()) {
            result.completedSuccessfully = true
            result.endTime = System.currentTimeMillis()
            result.totalDurationMs = result.endTime - result.startTime
            return result
        }

        val context = ActionContext(
            automation = com.proj.automation.accessibility.AutomationBridge.get(),
            selectorEngine = SelectorEngine(),
            eventBus = eventBus,
            cancellationToken = token
        )

        for ((index, step) in workflow.steps.withIndex()) {
            val stepResult = try {
                context.throwIfCancelled()
                errorHandler.executeWithPolicy(step, { ctx ->
                    val singleResult = actionDispatcher.dispatch(step, ctx)
                    singleResult.copy(stepIndex = index)
                }, context)
            } catch (e: CancelledException) {
                result.cancelled = true
                result.endTime = System.currentTimeMillis()
                result.totalDurationMs = result.endTime - result.startTime
                return result
            } catch (e: CancellationException) {
                // Coroutine cancellation (job cancelled) must propagate, not be recorded as a step failure
                throw e
            } catch (e: Exception) {
                StepResult(
                    stepIndex = index,
                    action = step.action,
                    success = false,
                    durationMs = 0,
                    errorMessage = "Fatal error: ${e.message}"
                )
            }

            result.steps.add(stepResult)

            // Publish step result to event bus for UI updates
            eventBus.publish(
                EventBus.Event.StepCompleted(
                    stepIndex = stepResult.stepIndex,
                    success = stepResult.success,
                    action = stepResult.action.yamlValue,
                    errorMessage = stepResult.errorMessage
                )
            )

            // Publish log messages from LogHandler
            stepResult.details["message"]?.let { message ->
                eventBus.publish(EventBus.Event.LogMessage(message.toString()))
            }

            // On failure with ABORT policy, stop execution
            if (!stepResult.success && step.onFailure == OnFailurePolicy.ABORT) {
                result.endTime = System.currentTimeMillis()
                result.totalDurationMs = result.endTime - result.startTime
                return result
            }
        }

        result.completedSuccessfully = true
        result.endTime = System.currentTimeMillis()
        result.totalDurationMs = result.endTime - result.startTime
        return result
    }

    /** Cancel a running workflow */
    fun cancel() {
        cancellationToken.cancel()
    }
}
