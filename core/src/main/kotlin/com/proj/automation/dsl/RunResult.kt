package com.proj.automation.dsl

import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.models.StepResult

/** Global bounds on a single run, enforced whatever the program says */
data class RunLimits(
    val maxActions: Int = 500,
    val maxNodes: Int = 5_000,
    val maxCallDepth: Int = 8,
    val maxDurationMs: Long = 10 * 60 * 1000L
)

enum class RunStatus { SUCCEEDED, FAILED, CANCELLED }

data class RunResult(
    val status: RunStatus,
    val errorCode: ErrorCode? = null,
    val message: String? = null,
    /** Value of the top-level `return`, if any */
    val returnValue: String? = null,
    /** Every action executed, in order */
    val steps: List<StepResult> = emptyList(),
    val durationMs: Long = 0
)
