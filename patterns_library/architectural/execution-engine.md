# Execution Engine Pattern

## Purpose

Guide the implementation of the core step-loop orchestrator that iterates over parsed workflow steps, dispatches each to the appropriate action handler, applies retry/timeout/error policies, and reports execution results. Covers the action dispatcher, error handler, cancellation support, and result data classes.

## When This Pattern Applies

Use this pattern when:
- Iterating over workflow steps and dispatching to action handlers
- Implementing retry/timeout logic per step
- Supporting cancellation mid-execution via a CancellationToken
- Reporting per-step and overall execution results
- Building the ActionContext that provides action handlers access to services

## Core Data Models

### ExecutionResult and StepResult

```kotlin
package com.proj.automation.engine.models

import com.proj.automation.parser.ActionType

data class ExecutionResult(
    val workflowName: String?,
    val steps: List<StepResult> = emptyList(),
    val completedSuccessfully: Boolean = false,
    val cancelled: Boolean = false,
    val totalDurationMs: Long = 0,
    val startTime: Long = 0,
    val endTime: Long = 0
) {
    val stepCount: Int get() = steps.size
    val failedSteps: List<StepResult> get() = steps.filter { !it.success }
    val successRate: Double
        get() = if (steps.isEmpty()) 1.0 else steps.count { it.success }.toDouble() / steps.size
}

data class StepResult(
    val stepIndex: Int,
    val action: ActionType,
    val success: Boolean,
    val durationMs: Long,
    val errorMessage: String? = null,
    val strategy: String? = null,
    val details: Map<String, Any?> = emptyMap()
)
```

### OnFailurePolicy

```kotlin
package com.proj.automation.engine.models

sealed class OnFailurePolicy {
    object ABORT : OnFailurePolicy()
    object CONTINUE : OnFailurePolicy()
    data class RETRY(val maxAttempts: Int, val delayMs: Long) : OnFailurePolicy()
}
```

### ActionContext

```kotlin
package com.proj.automation.engine

import com.proj.automation.parser.Step
import com.proj.automation.selector.SelectorEngine
import com.proj.automation.service.AutomationBridge
import com.proj.automation.service.EventBus

/**
 * Context provided to each action handler with access to all runtime services.
 */
data class ActionContext(
    val automation: AutomationBridge,
    val selectorEngine: SelectorEngine,
    val eventBus: EventBus,
    val cancellationToken: CancellationToken
) {
    /**
     * Resolve a selector defined on the current step.
     * @return The resolved node, or null if not found.
     */
    fun resolveStepSelector(step: Step) =
        step.selector?.let { selectorEngine.resolve(it, automation.getRootNode()) }
}
```

## CancellationToken

```kotlin
package com.proj.automation.engine

class CancellationToken {
    @Volatile
    private var _cancelled: Boolean = false

    val isCancelled: Boolean get() = _cancelled

    fun cancel() {
        _cancelled = true
    }

    fun throwIfCancelled() {
        if (_cancelled) {
            throw CancelledException()
        }
    }
}

class CancelledException : RuntimeException("Workflow execution cancelled by user")
```

## Action Handler Interface

```kotlin
package com.proj.automation.engine

import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.Step

/**
 * Contract for each action handler. Each ActionType gets its own handler.
 */
interface ActionHandler {
    val actionType: ActionType

    /**
     * Execute this action for the given step.
     * @throws HandlerException if the action cannot be performed
     */
    suspend fun execute(step: Step, context: ActionContext): StepResult
}

class HandlerException(
    message: String,
    val actionType: ActionType
) : RuntimeException(message)
```

## Action Dispatcher

Routes each step to the correct handler based on `step.action`.

```kotlin
package com.proj.automation.engine

import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

class ActionDispatcher(
    private val handlers: Map<ActionType, ActionHandler>
) {
    /**
     * Dispatch a step to the correct handler.
     * @throws IllegalStateException if no handler is registered for the step's action
     */
    fun dispatch(step: Step, context: ActionContext): StepResult {
        val handler = handlers[step.action]
            ?: throw IllegalStateException(
                "No handler registered for action: ${step.action}"
            )
        return handler.execute(step, context)
    }

    /** Check if a handler exists for the given action type */
    fun hasHandler(actionType: ActionType): Boolean = actionType in handlers
}
```

## Action Handler Registry

Register all built-in handlers at engine startup:

```kotlin
package com.proj.automation.engine

fun buildHandlerRegistry(): Map<ActionType, ActionHandler> {
    return mapOf(
        ActionType.LAUNCH_APP to LaunchAppHandler(),
        ActionType.WAIT to WaitHandler(),
        ActionType.WAIT_FOR to WaitForHandler(),
        ActionType.CLICK to ClickHandler(),
        ActionType.TYPE to TypeHandler(),
        ActionType.BACK to BackHandler(),
        ActionType.HOME to HomeHandler(),
        ActionType.SCROLL to ScrollHandler(),
        ActionType.LOG to LogHandler()
    )
}
```

### Handler Examples

**LaunchAppHandler** — Starts the target app via implicit Intent.

```kotlin
package com.proj.automation.engine.actions

import android.content.Intent
import android.content.pm.PackageManager
import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionHandler
import com.proj.automation.engine.HandlerException
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import kotlinx.coroutines.delay

class LaunchAppHandler : ActionHandler {
    override val actionType = ActionType.LAUNCH_APP

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val startTime = System.currentTimeMillis()
        context.throwIfCancelled()

        val packageName = step.parameters["package"] as? String
            ?: throw HandlerException("launch_app requires 'package' parameter", actionType)

        // Verify the package is installed
        val pm = App.instance.packageManager
        if (!isPackageInstalled(pm, packageName)) {
            return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "Package not found: $packageName"
            )
        }

        // Resolve the launchable activity
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?: return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "No launchable activity found for: $packageName"
            )

        context.automation.startActivity(intent)
        // Wait for the app to settle
        delay(1000)

        return StepResult(
            stepIndex = 0, action = actionType, success = true,
            durationMs = System.currentTimeMillis() - startTime,
            details = mapOf("package" to packageName)
        )
    }

    private fun isPackageInstalled(pm: android.content.pm.PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }
}
```

**WaitHandler** — Suspends execution for a specified duration.

```kotlin
class WaitHandler : ActionHandler {
    override val actionType = ActionType.WAIT

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        context.throwIfCancelled()
        val startTime = System.currentTimeMillis()
        val seconds = (step.parameters["seconds"] as? String)?.toDoubleOrNull() ?: 1.0
        val durationMs = (seconds * 1000).toLong()
        delay(durationMs)
        return StepResult(
            stepIndex = 0, action = actionType, success = true,
            durationMs = System.currentTimeMillis() - startTime
        )
    }
}
```

**ClickHandler** — Resolves selector, taps the element with retry support.

```kotlin
class ClickHandler : ActionHandler {
    override val actionType = ActionType.CLICK

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        val startTime = System.currentTimeMillis()
        val selector = step.selector
            ?: return StepResult(
                stepIndex = 0, action = actionType, success = false,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = "click requires a selector"
            )

        val maxAttempts = step.retries.coerceAtLeast(1)
        val retryDelay = step.retryDelayMs

        for (attempt in 1..maxAttempts) {
            context.throwIfCancelled()

            val resolved = context.selectorEngine.resolve(selector, context.automation.getRootNode())
            if (resolved != null) {
                val clicked = context.automation.click(resolved)
                if (clicked) {
                    return StepResult(
                        stepIndex = 0, action = actionType, success = true,
                        durationMs = System.currentTimeMillis() - startTime,
                        strategy = when (selector) {
                            is Selector.ByResourceId -> "resource_id"
                            is Selector.ByText -> "text"
                            is Selector.ByContentDescription -> "content_description"
                            is Selector.ByClassName -> "class_name"
                            is Selector.Composite -> "composite"
                            else -> "unknown"
                        },
                        details = mapOf("attempt" to attempt)
                    )
                }
            }

            if (attempt < maxAttempts) {
                delay(retryDelay)
            }
        }

        return StepResult(
            stepIndex = 0, action = actionType, success = false,
            durationMs = System.currentTimeMillis() - startTime,
            errorMessage = "Element not found after $maxAttempts attempts"
        )
    }
}
```

**BackHandler** — Triggers system back navigation.

```kotlin
class BackHandler : ActionHandler {
    override val actionType = ActionType.BACK

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        context.throwIfCancelled()
        val startTime = System.currentTimeMillis()
        val success = context.automation.goBack()
        return StepResult(
            stepIndex = 0, action = actionType, success = success,
            durationMs = System.currentTimeMillis() - startTime,
            errorMessage = if (!success) "Back navigation failed" else null
        )
    }
}
```

**LogHandler** — Emits a log message to the event bus.

```kotlin
class LogHandler : ActionHandler {
    override val actionType = ActionType.LOG

    override suspend fun execute(step: Step, context: ActionContext): StepResult {
        context.throwIfCancelled()
        val message = step.parameters["message"] as? String ?: "Log entry"
        context.eventBus.publish(EventBus.Event.LogMessage(message))
        return StepResult(
            stepIndex = 0, action = actionType, success = true,
            durationMs = 0,
            details = mapOf("message" to message)
        )
    }
}
```

## Error Handler — Retry and Timeout

```kotlin
package com.proj.automation.engine

import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.Step
import kotlinx.coroutines.delay

class ErrorHandler {

    /**
     * Execute an action with retry and timeout logic per the step's policy.
     */
    suspend fun executeWithPolicy(
        step: Step,
        action: suspend (ActionContext) -> StepResult,
        context: ActionContext
    ): StepResult {
        val maxAttempts = when (step.onFailure) {
            OnFailurePolicy.ABORT -> 1
            OnFailurePolicy.CONTINUE -> 1
            is OnFailurePolicy.RETRY -> step.retries.coerceAtLeast(1)
        }
        val retryDelay = step.retryDelayMs
        val timeoutMs = step.timeoutMs
        val deadline = System.currentTimeMillis() + timeoutMs

        var lastResult: StepResult? = null

        for (attempt in 1..maxAttempts) {
            context.throwIfCancelled()

            if (System.currentTimeMillis() > deadline) {
                return StepResult(
                    stepIndex = stepIndexOrDefault(step, lastResult),
                    action = step.action,
                    success = false,
                    durationMs = System.currentTimeMillis() - (deadline - timeoutMs),
                    errorMessage = "Timeout after ${timeoutMs}ms"
                )
            }

            lastResult = action(context)

            if (lastResult.success) return lastResult

            if (attempt < maxAttempts) {
                delay(retryDelay)
            }
        }

        return when (step.onFailure) {
            OnFailurePolicy.CONTINUE -> StepResult(
                stepIndex = lastResult?.stepIndex ?: 0,
                action = step.action,
                success = true, // Continue treats failure as success
                durationMs = System.currentTimeMillis() - (deadline - timeoutMs),
                errorMessage = lastResult?.errorMessage,
                details = mapOf("skipped" to true)
            )
            else -> lastResult ?: StepResult(
                stepIndex = 0, action = step.action, success = false,
                durationMs = 0, errorMessage = "Unknown failure"
            )
        }
    }

    private fun stepIndexOrDefault(step: Step, lastResult: StepResult?): Int {
        return lastResult?.stepIndex ?: 0
    }
}
```

## Execution Engine — Core Step Loop

```kotlin
package com.proj.automation.engine

import com.proj.automation.engine.models.ExecutionResult
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.Step
import com.proj.automation.parser.Workflow
import kotlinx.coroutines.delay

class ExecutionEngine(
    private val actionDispatcher: ActionDispatcher,
    private val errorHandler: ErrorHandler,
    private val eventBus: com.proj.automation.service.EventBus
) {

    private val cancellationToken = CancellationToken()

    fun execute(workflow: Workflow): ExecutionResult {
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
            automation = AutomationBridge.instance,
            selectorEngine = SelectorEngine(),
            eventBus = eventBus,
            cancellationToken = cancellationToken
        )

        for ((index, step) in workflow.steps.withIndex()) {
            context.throwIfCancelled()

            val stepResult = try {
                errorHandler.executeWithPolicy(step) { ctx ->
                    val singleResult = actionDispatcher.dispatch(step, ctx)
                    singleResult.copy(stepIndex = index)
                }
            } catch (e: CancelledException) {
                result.cancelled = true
                result.endTime = System.currentTimeMillis()
                result.totalDurationMs = result.endTime - result.startTime
                return result
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
            eventBus.publish(EventBus.Event.StepCompleted(stepResult))

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
```

## Supported Actions Summary

| Handler | Selector Required | Retry Supported | Description |
|---------|-------------------|-----------------|-------------|
| `LaunchAppHandler` | No | No | Starts target app via `Intent` |
| `WaitHandler` | No | No | Sleeps for `seconds` |
| `WaitForHandler` | Yes | Yes | Polls selector until found or timeout |
| `ClickHandler` | Yes | Yes | Finds element, taps it |
| `TypeHandler` | No* | No | Injects text into focused field |
| `BackHandler` | No | No | `performGlobalAction(BACK)` |
| `HomeHandler` | No | No | `performGlobalAction(HOME)` |
| `ScrollHandler` | Yes | Yes | Scrolls a scrollable node |
| `LogHandler` | No | No | Emits log message via EventBus |

*Type uses the element currently in focus.

## Customization Guide

| Parameter | Default | Notes |
|-----------|---------|-------|
| `retries` per step | 1 | Override in YAML with `retries: N` |
| `retryDelayMs` per step | 1000ms | Override in YAML with `retry_delay: N` |
| `timeoutMs` per step | 30000ms (30s) | Override in YAML with `timeout: N` |
| `onFailure` per step | `ABORT` | Override in YAML with `on_failure: continue` or `on_failure: retry(N, delayMs)` |
| `CancellationToken` | Active per `execute()` call | Call `engine.cancel()` to stop mid-execution |

## Validation

Before using the execution engine end-to-end, verify:

- [ ] `execute(workflow)` returns `ExecutionResult` with all steps completed
- [ ] Each `StepResult` has correct `stepIndex`, `action`, `success`, `durationMs`
- [ ] `completedSuccessfully = true` when all steps succeed
- [ ] `completedSuccessfully = false` when any step with `ABORT` fails
- [ ] `cancelled = true` and remaining steps are not executed after `cancel()`
- [ ] `onFailure: CONTINUE` marks failed step as success and continues
- [ ] `onFailure: RETRY(3, 500)` retries up to 3 times with 500ms delay
- [ ] Timeout triggers after `timeoutMs` and returns failure StepResult
- [ ] Empty workflow returns immediately with `completedSuccessfully = true`
- [ ] `ActionDispatcher.dispatch()` throws `IllegalStateException` for unregistered action types
- [ ] `ActionContext` provides access to `automation`, `selectorEngine`, `eventBus`, `cancellationToken`
- [ ] Step results are published to `EventBus` for UI consumption
