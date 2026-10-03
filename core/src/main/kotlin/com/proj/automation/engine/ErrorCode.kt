package com.proj.automation.engine

/**
 * Structured error codes for step and run results (see specs/001-execution-engine/contracts/workflow-language.md).
 * Channel adapters turn these into short user-facing replies.
 */
enum class ErrorCode {
    /** Target element not found */
    E_NOT_FOUND,
    /** A step or wait exceeded its time limit */
    E_TIMEOUT,
    /** Candidates exist but none clearly matches the target, so the engine refused to guess */
    E_LOW_CONFIDENCE,
    /** A post-condition (`expect`) or `assert` was false */
    E_VERIFY_FAILED,
    /** The action ran and reported failure for another reason */
    E_ACTION_FAILED,
    /** A plugin tried something its approved capabilities do not allow */
    E_CAPABILITY,
    /** Invalid template, undefined variable or invalid flow call */
    E_EXPR,
    /** The run exceeded the engine's global limits (steps, call depth, duration) */
    E_BUDGET,
    /** The phone cannot run automation now (accessibility service off, screen locked, ...) */
    E_DEVICE,
    /** Stopped by the user */
    E_CANCELLED
}
