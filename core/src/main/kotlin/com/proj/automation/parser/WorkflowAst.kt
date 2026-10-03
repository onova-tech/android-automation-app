package com.proj.automation.parser

/**
 * Top-level workflow object representing a parsed YAML workflow.
 */
data class Workflow(
    val name: String? = null,
    val description: String? = null,
    val steps: List<Step> = emptyList(),
    val variables: Map<String, String> = emptyMap()
)

/**
 * A single step in a workflow — contains exactly one action with parameters.
 */
data class Step(
    val action: ActionType,
    val parameters: Map<String, Any?> = emptyMap(),
    val selector: Selector? = null,
    /** DSL v2 target (intent + hints); resolved by [com.proj.automation.resolve.TargetResolver] */
    val target: com.proj.automation.resolve.Target? = null,
    val retries: Int = 1,
    val retryDelayMs: Long = 1000,
    val timeoutMs: Long = 30000,
    val onFailure: OnFailurePolicy = OnFailurePolicy.ABORT
)
