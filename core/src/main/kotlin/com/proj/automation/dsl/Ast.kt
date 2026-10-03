package com.proj.automation.dsl

import com.proj.automation.parser.Step
import com.proj.automation.resolve.Target

/** Syntax tree of the workflow language used by plugin skills and flows. */
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
    /** `exists` / `not_exists`: true only for a confident match, never a guess */
    data class Exists(val target: Target, val negate: Boolean = false) : Condition()
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

/**
 * Handles an unexpected dialog (permission, rating prompt, ad) whenever it shows up: before each
 * screen action, and once more after an action fails because its element was missing.
 */
data class InterruptRule(
    val name: String,
    val condition: Condition,
    /** Only `click`, `back`, `wait`, `wait_for` and `log` (checked by the parser) */
    val body: List<Node>,
    val maxPerRun: Int
)

data class Program(
    val name: String? = null,
    val description: String? = null,
    val params: Map<String, ParamSpec> = emptyMap(),
    val flows: Map<String, Flow> = emptyMap(),
    val body: List<Node> = emptyList(),
    val interrupts: List<InterruptRule> = emptyList(),
    /** Plugin texts by language (`i18n/<lang>.yaml`), read in templates as `${t.key}` */
    val strings: Map<String, Map<String, String>> = emptyMap(),
    /** Language used when the device's language has no texts */
    val defaultLanguage: String? = null
) {
    /** The texts for [language] (e.g. "pt" or "pt-BR"), falling back to the default language */
    fun stringsFor(language: String?): Map<String, String> {
        val lang = language?.lowercase()
        return lang?.let { strings[it] ?: strings[it.substringBefore('-')] }
            ?: defaultLanguage?.let { strings[it] }
            ?: emptyMap()
    }
}
