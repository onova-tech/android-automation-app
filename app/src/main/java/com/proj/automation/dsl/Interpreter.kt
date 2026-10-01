package com.proj.automation.dsl

import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionDispatcher
import com.proj.automation.engine.CancelledException
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.ErrorHandler
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.OnFailurePolicy
import com.proj.automation.service.EventBus
import kotlin.coroutines.cancellation.CancellationException

/** Global bounds on a single run, enforced whatever the program says */
data class RunLimits(
    val maxActions: Int = 500,
    val maxNodes: Int = 5_000,
    val maxCallDepth: Int = 8,
    val maxDurationMs: Long = 10 * 60 * 1000L
)

enum class RunStatus { SUCCEEDED, FAILED, CANCELLED }

data class RunResult(
    val status: RunStatus,
    val errorCode: ErrorCode? = null,
    val message: String? = null,
    /** Value of the top-level `return`, if any */
    val returnValue: String? = null,
    /** Every action executed, in order */
    val steps: List<StepResult> = emptyList(),
    val durationMs: Long = 0
)

/**
 * Executes DSL v2 programs. Actions go through the existing [ErrorHandler] (retries, timeouts,
 * `on_failure`) and [ActionDispatcher]. Control flow, variables, post-conditions and global
 * limits are handled here.
 *
 * Failure model: a failing step raises a [StepFailure] that unwinds to the nearest `try` or
 * `first_that_works`, or ends the run. Cancellation, limit overruns and template errors are not
 * catchable by the program, so a workflow can never "handle" them and keep going.
 */
class Interpreter(
    private val dispatcher: ActionDispatcher,
    private val errorHandler: ErrorHandler,
    private val eventBus: EventBus,
    private val limits: RunLimits = RunLimits()
) {

    private class StepFailure(val code: ErrorCode, message: String) : RuntimeException(message)
    private class Abort(val code: ErrorCode, message: String) : RuntimeException(message)
    private class ReturnSignal(val value: String) : RuntimeException(null, null, false, false)

    private class Run(val program: Program, val context: ActionContext, val deadline: Long) {
        val steps = mutableListOf<StepResult>()
        var nodes = 0
    }

    suspend fun run(program: Program, args: Map<String, String>, context: ActionContext): RunResult {
        val start = System.currentTimeMillis()
        val run = Run(program, context, start + limits.maxDurationMs)
        fun result(status: RunStatus, code: ErrorCode? = null, message: String? = null, value: String? = null) =
            RunResult(status, code, message, value, run.steps.toList(), System.currentTimeMillis() - start)

        return try {
            val scope = Scope(program.variables)
            // Program arguments come from outside (e.g. an SMS) and are bound literally, never rendered
            bindParams(program.params, args, scope, "program")
            exec(program.body, scope, run, depth = 0)
            result(RunStatus.SUCCEEDED)
        } catch (r: ReturnSignal) {
            result(RunStatus.SUCCEEDED, value = r.value)
        } catch (f: StepFailure) {
            result(RunStatus.FAILED, f.code, f.message)
        } catch (a: Abort) {
            result(RunStatus.FAILED, a.code, a.message)
        } catch (e: ExpressionException) {
            result(RunStatus.FAILED, ErrorCode.E_EXPR, e.message)
        } catch (c: CancelledException) {
            result(RunStatus.CANCELLED, ErrorCode.E_CANCELLED, c.message)
        }
    }

    private suspend fun exec(nodes: List<Node>, scope: Scope, run: Run, depth: Int) {
        for (node in nodes) exec(node, scope, run, depth)
    }

    private suspend fun exec(node: Node, scope: Scope, run: Run, depth: Int) {
        run.context.throwIfCancelled()
        if (++run.nodes > limits.maxNodes) throw Abort(ErrorCode.E_BUDGET, "More than ${limits.maxNodes} steps evaluated")
        if (System.currentTimeMillis() > run.deadline) throw Abort(ErrorCode.E_BUDGET, "Run exceeded ${limits.maxDurationMs}ms")

        when (node) {
            is Node.Action -> action(node, scope, run)
            is Node.Sequence -> exec(node.nodes, scope, run, depth)
            is Node.If -> exec(if (eval(node.condition, scope, run)) node.then else node.otherwise, scope, run, depth)
            is Node.FirstThatWorks -> {
                var last: StepFailure? = null
                for (alt in node.alternatives) {
                    try {
                        exec(alt, scope, run, depth)
                        return
                    } catch (f: StepFailure) {
                        last = f
                    }
                }
                throw StepFailure(last!!.code, "No alternative worked; last error: ${last.message}")
            }
            is Node.Try -> try {
                exec(node.body, scope, run, depth)
            } catch (f: StepFailure) {
                scope["error"] = mapOf("code" to f.code.name, "message" to (f.message ?: ""))
                exec(node.onError, scope, run, depth)
            }
            is Node.Call -> {
                if (depth + 1 > limits.maxCallDepth) throw Abort(ErrorCode.E_BUDGET, "Flow call depth over ${limits.maxCallDepth}")
                val flow = run.program.flows.getValue(node.flow)
                val local = Scope()
                val args = node.args.mapValues { (_, v) -> Templates.render(v, scope) }
                bindParams(flow.params, args, local, "flow '${flow.name}'")
                val value = try {
                    exec(flow.body, local, run, depth + 1)
                    null
                } catch (r: ReturnSignal) {
                    r.value
                }
                node.into?.let { scope[it] = value }
            }
            is Node.SetVars -> node.values.forEach { (k, v) -> scope[k] = Templates.render(v, scope) }
            is Node.Assert -> if (!eval(node.condition, scope, run)) {
                throw StepFailure(
                    ErrorCode.E_VERIFY_FAILED,
                    node.message?.let { Templates.render(it, scope) } ?: "Assertion failed: ${node.condition}"
                )
            }
            is Node.Return -> throw ReturnSignal(Templates.render(node.value, scope))
        }
    }

    private suspend fun action(node: Node.Action, scope: Scope, run: Run) {
        if (run.steps.size >= limits.maxActions) throw Abort(ErrorCode.E_BUDGET, "More than ${limits.maxActions} actions")
        val step = node.step.rendered(scope)
        val index = run.steps.size

        var result = try {
            errorHandler.executeWithPolicy(step, { ctx -> dispatcher.dispatch(step, ctx) }, run.context)
                .copy(stepIndex = index)
        } catch (e: CancelledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StepResult(
                stepIndex = index, action = step.action, success = false, durationMs = 0,
                errorMessage = "Fatal error: ${e.message}", errorCode = ErrorCode.E_ACTION_FAILED
            )
        }

        val expect = node.expect
        if (result.success && expect != null && !eval(expect, scope, run)) {
            result = result.copy(
                success = false, errorCode = ErrorCode.E_VERIFY_FAILED,
                errorMessage = "Post-condition not met: ${node.expect}"
            )
        }

        run.steps.add(result)
        eventBus.publish(
            EventBus.Event.StepCompleted(
                stepIndex = index,
                success = result.success,
                action = step.action.yamlValue,
                errorMessage = result.errorMessage
            )
        )

        if (!result.success && step.onFailure != OnFailurePolicy.CONTINUE) {
            throw StepFailure(result.errorCode ?: ErrorCode.E_ACTION_FAILED, result.errorMessage ?: "Step failed")
        }
        if (result.success) {
            (step.parameters["into"] as? String)?.takeIf { it.isNotBlank() }?.let { name ->
                scope[name] = result.details["value"]?.let { if (it is List<*>) it.map(Any?::toString) else it.toString() }
            }
        }
    }

    private fun eval(condition: Condition, scope: Scope, run: Run): Boolean = when (condition) {
        is Condition.Exists -> resolves(condition.selector, scope, run)
        is Condition.NotExists -> !resolves(condition.selector, scope, run)
        is Condition.TargetExists -> {
            val target = condition.target.map { Templates.render(it, scope) }
            val found = run.context.targetResolver.resolve(target, run.context.snapshot()) is com.proj.automation.resolve.Resolution.Found
            found != condition.negate
        }
        is Condition.Equals -> Templates.render(condition.left, scope) == Templates.render(condition.right, scope)
        is Condition.Contains -> Templates.render(condition.haystack, scope).contains(Templates.render(condition.needle, scope))
        is Condition.IsSet -> scope.isSet(condition.variable)
        is Condition.Not -> !eval(condition.condition, scope, run)
        is Condition.All -> condition.conditions.all { eval(it, scope, run) }
        is Condition.AnyOf -> condition.conditions.any { eval(it, scope, run) }
    }

    private fun resolves(selector: com.proj.automation.parser.Selector, scope: Scope, run: Run): Boolean {
        val rendered = DslParser.mapSelector(selector) { Templates.render(it, scope) }
        return run.context.selectorEngine.resolve(rendered, run.context.automation.getRootNode()) != null
    }

    /** Binds already-rendered [args] and defaults into [target]; missing required ones fail. */
    private fun bindParams(
        params: Map<String, ParamSpec>,
        args: Map<String, String>,
        target: Scope,
        where: String
    ) {
        val unknown = args.keys - params.keys
        if (unknown.isNotEmpty() && params.isNotEmpty()) {
            throw ExpressionException("Unknown arguments $unknown for $where; parameters: ${params.keys}")
        }
        for ((name, spec) in params) {
            val value = args[name]
                ?: spec.default?.let { Templates.render(it, target) }
                ?: if (spec.required) throw ExpressionException("Missing required parameter '$name' for $where") else null
            target[name] = value
        }
        if (params.isEmpty()) args.forEach { (k, v) -> target[k] = v }
    }
}
