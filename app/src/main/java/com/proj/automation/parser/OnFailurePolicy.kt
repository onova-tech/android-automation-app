package com.proj.automation.parser

/**
 * Policy for handling step failures during workflow execution.
 */
sealed class OnFailurePolicy {
    /** Stop workflow execution immediately on failure */
    object ABORT : OnFailurePolicy()

    /** Skip the failed step and continue with the next one */
    object CONTINUE : OnFailurePolicy()

    /** Retry the step up to maxAttempts times with delayMs between attempts */
    data class RETRY(val maxAttempts: Int, val delayMs: Long) : OnFailurePolicy()
}
