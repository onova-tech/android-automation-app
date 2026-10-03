package com.proj.automation.engine.actions

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType

/** Builds a [StepResult] timed from [start] (context clock). */
internal fun ActionContext.result(
    action: ActionType,
    start: Long,
    success: Boolean,
    message: String? = null,
    code: ErrorCode? = null,
    strategy: String? = null,
    details: Map<String, Any?> = emptyMap()
) = StepResult(
    stepIndex = 0, action = action, success = success, durationMs = clock() - start,
    errorMessage = message, errorCode = code, strategy = strategy, details = details
)

internal fun ActionContext.missing(action: ActionType, start: Long, lookup: ActionContext.Lookup.Missing) =
    result(action, start, false, lookup.message, lookup.code)
