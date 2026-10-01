package com.proj.automation.dsl

import com.proj.automation.parser.Selector
import com.proj.automation.parser.Step

/**
 * DSL v2 syntax tree. A v1 workflow (a flat list of single-action steps) is a valid
 * v2 program whose body contains only [Node.Action] nodes.
 */
sealed class Node {
    /** A built-in action, optionally verified by a post-condition after it succeeds */
    data class Action(val step: Step, val expect: Condition? = null) : Node()

    data class Sequence(val nodes: List<Node>) : Node()

    data class If(val condition: Condition, val then: List<Node>, val otherwise: List<Node>) : Node()

    /** Tries each alternative in order; the first one that completes without failing wins */
    data class FirstThatWorks(val alternatives: List<Node>) : Node()

    /** Runs [body]; on a step failure, exposes `error.code` / `error.message` and runs [onError] */
    data class Try(val body: List<Node>, val onError: List<Node>) : Node()

    /** Calls a named flow with templated arguments; its return value may be bound to [into] */
    data class Call(val flow: String, val args: Map<String, String>, val into: String?) : Node()

    data class SetVars(val values: Map<String, String>) : Node()

    data class Assert(val condition: Condition, val message: String?) : Node()

    data class Return(val value: String) : Node()
}

/** Conditions for `if`, `assert` and `expect`. String operands are templates. */
sealed class Condition {
    data class Exists(val selector: Selector) : Condition()
    data class NotExists(val selector: Selector) : Condition()
    data class Equals(val left: String, val right: String) : Condition()
    data class Contains(val haystack: String, val needle: String) : Condition()
    data class IsSet(val variable: String) : Condition()
    data class Not(val condition: Condition) : Condition()
    data class All(val conditions: List<Condition>) : Condition()
    data class AnyOf(val conditions: List<Condition>) : Condition()
}

data class ParamSpec(val required: Boolean = true, val default: String? = null)

/** A named, parameterized sub-flow. Its variables are local to each call. */
data class Flow(
    val name: String,
    val params: Map<String, ParamSpec>,
    val body: List<Node>
)

data class Program(
    val name: String? = null,
    val description: String? = null,
    val params: Map<String, ParamSpec> = emptyMap(),
    /** Initial variables (v1 `variables:` block) */
    val variables: Map<String, String> = emptyMap(),
    val flows: Map<String, Flow> = emptyMap(),
    val body: List<Node> = emptyList()
)
