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
import kotlinx.coroutines.delay

/**
 * Entry point for running skills. Builds a fresh [ActionContext] per run on the current device
 * and delegates to the [Interpreter].
 */
class ExecutionEngine(
    actionDispatcher: ActionDispatcher,
    errorHandler: ErrorHandler,
    private val eventBus: EventBus,
    /** The device to act on, or null when none is available (e.g. accessibility service off) */
    private val device: () -> DevicePort?,
    limits: RunLimits = RunLimits(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) }
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
        val port = device() ?: return RunResult(RunStatus.FAILED, ErrorCode.E_DEVICE, "No device available")
        val context = ActionContext(port, eventBus, token, clock = clock, sleep = sleep, canSee = { guard?.canSee(it) ?: true })
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
