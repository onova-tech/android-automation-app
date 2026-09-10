package com.proj.automation.engine

/**
 * Signal for cancelling a running workflow execution.
 * Checked at each step boundary and during long-running operations.
 */
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
