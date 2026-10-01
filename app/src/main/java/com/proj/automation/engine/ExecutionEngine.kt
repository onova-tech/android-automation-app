package com.proj.automation.engine

import com.proj.automation.dsl.Interpreter
import com.proj.automation.dsl.Node
import com.proj.automation.dsl.Program
import com.proj.automation.dsl.RunLimits
import com.proj.automation.dsl.RunResult
import com.proj.automation.dsl.RunStatus
import com.proj.automation.engine.models.ExecutionResult
import com.proj.automation.parser.Workflow
import com.proj.automation.service.EventBus
import com.proj.automation.selector.SelectorEngine

/**
 * Entry point for running workflows. Builds a fresh [ActionContext] per run and delegates
 * to the DSL v2 [Interpreter]; v1 workflows run as programs made only of actions.
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

    /** Run a DSL v2 program with literal arguments (for example, values from a command). */
    suspend fun run(program: Program, args: Map<String, String> = emptyMap()): RunResult {
        val token = CancellationToken().also { cancellationToken = it }
        val context = ActionContext(
            automation = com.proj.automation.accessibility.AutomationBridge.get(),
            selectorEngine = SelectorEngine(),
            eventBus = eventBus,
            cancellationToken = token
        )
        return interpreter.run(program, args, context)
    }

    /**
     * Execute a v1 workflow. Returns the full execution result with
     * per-step results, success/failure status, and timing.
     */
    suspend fun execute(workflow: Workflow): ExecutionResult {
        val startTime = System.currentTimeMillis()
        if (workflow.steps.isEmpty()) {
            return ExecutionResult(
                workflowName = workflow.name, completedSuccessfully = true,
                startTime = startTime, endTime = startTime
            )
        }
        val program = Program(
            name = workflow.name,
            description = workflow.description,
            variables = workflow.variables,
            body = workflow.steps.map { Node.Action(it) }
        )
        val run = run(program)
        val endTime = System.currentTimeMillis()
        return ExecutionResult(
            workflowName = workflow.name,
            steps = run.steps.toMutableList(),
            completedSuccessfully = run.status == RunStatus.SUCCEEDED,
            cancelled = run.status == RunStatus.CANCELLED,
            totalDurationMs = endTime - startTime,
            startTime = startTime,
            endTime = endTime
        )
    }

    /** Cancel a running workflow */
    fun cancel() {
        cancellationToken.cancel()
    }
}
