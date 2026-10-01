package com.proj.automation.parser

import com.proj.automation.resolve.Target

/**
 * One action of a skill or flow, with its parameters, the element it acts on ([target]) and its
 * retry/timeout/failure settings. Control flow around actions lives in [com.proj.automation.dsl.Node].
 */
data class Step(
    val action: ActionType,
    val parameters: Map<String, Any?> = emptyMap(),
    /** Element the action works on; resolved by [com.proj.automation.resolve.TargetResolver] */
    val target: Target? = null,
    val retries: Int = 1,
    val retryDelayMs: Long = 1000,
    val timeoutMs: Long = 30000,
    val onFailure: OnFailurePolicy = OnFailurePolicy.ABORT
)
