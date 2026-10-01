package com.proj.automation.engine.models

/**
 * Result of a single step execution within a workflow.
 */
data class StepResult(
    val stepIndex: Int,
    val action: com.proj.automation.parser.ActionType,
    val success: Boolean,
    val durationMs: Long,
    val errorMessage: String? = null,
    val errorCode: com.proj.automation.engine.ErrorCode? = null,
    val strategy: String? = null,
    val details: Map<String, Any?> = emptyMap()
)
