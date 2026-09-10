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
    val strategy: String? = null,
    val details: Map<String, Any?> = emptyMap()
)

/**
 * Complete result of a workflow execution, including per-step results.
 */
data class ExecutionResult(
    var workflowName: String? = null,
    var steps: MutableList<StepResult> = mutableListOf(),
    var completedSuccessfully: Boolean = false,
    var cancelled: Boolean = false,
    var totalDurationMs: Long = 0,
    var startTime: Long = 0,
    var endTime: Long = 0
) {
    val stepCount: Int get() = steps.size
    val failedSteps: List<StepResult> get() = steps.filter { !it.success }
    val successRate: Double
        get() = if (steps.isEmpty()) 1.0 else steps.count { it.success }.toDouble() / steps.size
}
