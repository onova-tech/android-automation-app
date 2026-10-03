package com.proj.automation.engine

import com.proj.automation.dsl.Interpreter
import com.proj.automation.dsl.Program
import com.proj.automation.dsl.RunLimits
import com.proj.automation.dsl.RunResult
import com.proj.automation.dsl.RunStatus
import com.proj.automation.plugin.ActionGuard
import com.proj.automation.plugin.CapabilityGuard
import com.proj.automation.plugin.Plugin
import com.proj.automation.service.EventBus

/**
 * Entry point for running skills. Builds a fresh [ActionContext] per run and delegates to the
 * [Interpreter].
 */
class ExecutionEngine(
    actionDispatcher: ActionDispatcher,
    errorHandler: ErrorHandler,
    private val eventBus: EventBus,
    limits: RunLimits = RunLimits()
) {

    private val interpreter = Interpreter(actionDispatcher, errorHandler, eventBus, limits)

    /** Token for the current run; replaced on each run so a previous Stop doesn't carry over. */
    @Volatile
    private var cancellationToken = CancellationToken()

    /**
     * Run a program with literal arguments (for example, values from a command).
     * [guard] restricts what the program may touch (a plugin's approved capabilities).
     */
    suspend fun run(program: Program, args: Map<String, String> = emptyMap(), guard: ActionGuard? = null): RunResult {
        val token = CancellationToken().also { cancellationToken = it }
        val context = ActionContext(
            automation = com.proj.automation.accessibility.AutomationBridge.get(),
            eventBus = eventBus,
            cancellationToken = token
        )
        return interpreter.run(program, args, context, guard)
    }

    /** Runs one of a plugin's skills under that plugin's capabilities. */
    suspend fun runSkill(plugin: Plugin, skill: String, args: Map<String, String>): RunResult {
        val s = plugin.skills[skill] ?: return RunResult(RunStatus.FAILED, ErrorCode.E_EXPR, "Unknown skill '$skill'")
        return run(s.program, args, CapabilityGuard(plugin.manifest.capabilities))
    }

    /** Cancel a running workflow */
    fun cancel() {
        cancellationToken.cancel()
    }
}
