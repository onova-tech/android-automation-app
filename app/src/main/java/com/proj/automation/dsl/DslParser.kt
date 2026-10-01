package com.proj.automation.dsl

import com.proj.automation.parser.Selector
import com.proj.automation.parser.Step
import com.proj.automation.parser.YamlParseException
import com.proj.automation.parser.YamlParser

/**
 * Parses DSL v2 programs. Accepts every v1 workflow unchanged; adds control-flow steps
 * (`sequence`, `if`, `first_that_works`, `try`, `call`, `set`, `assert`, `return`),
 * `expect` post-conditions on actions, `into` bindings, `params` and reusable `flows`.
 *
 * Everything that can be checked statically is checked here: template syntax and filters,
 * condition shapes, unknown flows and recursive flow calls.
 */
class DslParser(private val yamlParser: YamlParser = YamlParser()) {

    fun parse(yamlString: String): Program = parseDocument(yamlParser.loadDocument(yamlString))

    fun parseDocument(doc: Map<String, Any?>): Program {
        val unknown = doc.keys - TOP_LEVEL_KEYS
        if (unknown.isNotEmpty()) fail("top level", "unknown keys $unknown; allowed: $TOP_LEVEL_KEYS")

        val flows = (doc["flows"] as? Map<*, *>).orEmpty().map { (k, v) ->
            val name = k.toString()
            val map = v as? Map<*, *> ?: fail("flows.$name", "must be a mapping with 'steps'")
            Flow(
                name = name,
                params = parseParams(map["params"], "flows.$name.params"),
                body = parseSteps(map["steps"], "flows.$name.steps")
            )
        }.associateBy { it.name }

        val program = Program(
            name = doc["name"] as? String,
            description = doc["description"] as? String,
            params = parseParams(doc["params"], "params"),
            variables = (doc["variables"] as? Map<*, *>).orEmpty()
                .entries.associate { it.key.toString() to it.value.toString() },
            flows = flows,
            body = parseSteps(doc["steps"], "steps")
        )
        checkCalls(program)
        return program
    }

    // ——— Steps ———

    private fun parseSteps(raw: Any?, path: String): List<Node> {
        if (raw == null) return emptyList()
        val list = raw as? List<*> ?: fail(path, "must be a list of steps")
        return list.mapIndexed { i, item -> parseNode(item, "$path[$i]", i + 1) }
    }

    private fun parseNode(raw: Any?, path: String, stepNumber: Int): Node {
        val map = raw as? Map<*, *> ?: fail(path, "a step must be a mapping (key: value)")
        if (map.size != 1) fail(path, "a step must have exactly one key, found ${map.keys}")
        val key = map.keys.first().toString()
        val value = map.values.first()

        return when (key) {
            "sequence" -> Node.Sequence(parseSteps(value, "$path.sequence"))
            "first_that_works" -> {
                val alts = value as? List<*> ?: fail("$path.first_that_works", "must be a list")
                if (alts.isEmpty()) fail("$path.first_that_works", "needs at least one alternative")
                Node.FirstThatWorks(alts.mapIndexed { i, a -> parseNode(a, "$path.first_that_works[$i]", i + 1) })
            }
            "if" -> {
                val m = value as? Map<*, *> ?: fail("$path.if", "must be a mapping")
                val condition = parseCondition(m.filterKeys { it != "then" && it != "else" }, "$path.if")
                Node.If(
                    condition,
                    parseSteps(m["then"], "$path.if.then"),
                    parseSteps(m["else"], "$path.if.else")
                )
            }
            "try" -> {
                val m = value as? Map<*, *> ?: fail("$path.try", "must be a mapping with 'do' and 'on_error'")
                if (m["do"] == null) fail("$path.try", "missing 'do'")
                Node.Try(parseSteps(m["do"], "$path.try.do"), parseSteps(m["on_error"], "$path.try.on_error"))
            }
            "call" -> {
                val m = value as? Map<*, *> ?: fail("$path.call", "must be a mapping with 'flow'")
                val flow = m["flow"]?.toString() ?: fail("$path.call", "missing 'flow'")
                val args = (m["with"] as? Map<*, *>).orEmpty()
                    .entries.associate { it.key.toString() to template(it.value, "$path.call.with.${it.key}") }
                Node.Call(flow, args, m["into"]?.toString())
            }
            "set" -> {
                val m = value as? Map<*, *> ?: fail("$path.set", "must be a mapping of variable: value")
                Node.SetVars(m.entries.associate { it.key.toString() to template(it.value, "$path.set.${it.key}") })
            }
            "assert" -> {
                val m = value as? Map<*, *> ?: fail("$path.assert", "must be a mapping")
                Node.Assert(
                    parseCondition(m.filterKeys { it != "message" }, "$path.assert"),
                    m["message"]?.let { template(it, "$path.assert.message") }
                )
            }
            "return" -> Node.Return(template(value, "$path.return"))
            else -> parseAction(key, value, path, stepNumber)
        }
    }

    private fun parseAction(key: String, value: Any?, path: String, stepNumber: Int): Node.Action {
        val params = (value as? Map<*, *>).orEmpty()
        val expect = params["expect"]?.let {
            parseCondition(it as? Map<*, *> ?: fail("$path.$key.expect", "must be a condition"), "$path.$key.expect")
        }
        val cleaned = mapOf(key to params.filterKeys { it != "expect" })
        val step = try {
            yamlParser.parseStep(cleaned, stepNumber)
        } catch (e: YamlParseException) {
            fail(path, e.message ?: "invalid action")
        }
        step.parameters.forEach { (k, v) -> (v as? String)?.let { template(it, "$path.$key.$k") } }
        step.selector?.let { selectorStrings(it).forEach { s -> template(s, "$path.$key.selector") } }
        step.target?.let { it.strings().forEach { s -> template(s, "$path.$key.target") } }
        return Node.Action(step, expect)
    }

    // ——— Conditions ———

    private fun parseCondition(raw: Map<*, *>, path: String): Condition {
        val keys = raw.keys.map { it.toString() }.filter { it in CONDITION_KEYS }
        if (keys.size != 1) fail(path, "needs exactly one condition of $CONDITION_KEYS, found ${raw.keys}")
        val key = keys.first()
        val value = raw[key]
        return when (key) {
            "exists", "not_exists" -> {
                val negate = key == "not_exists"
                val targetRaw = (value as? Map<*, *>)?.get("target")
                if (targetRaw != null) {
                    val t = try {
                        com.proj.automation.resolve.Target.parse(targetRaw as? Map<*, *> ?: fail("$path.$key.target", "must be a mapping"))
                    } catch (e: IllegalArgumentException) {
                        fail("$path.$key.target", e.message ?: "invalid target")
                    }
                    t.strings().forEach { template(it, "$path.$key.target") }
                    Condition.TargetExists(t, negate)
                } else if (negate) Condition.NotExists(selector(value, "$path.$key"))
                else Condition.Exists(selector(value, "$path.$key"))
            }
            "equals" -> pair(value, "$path.equals").let { (a, b) -> Condition.Equals(a, b) }
            "contains" -> pair(value, "$path.contains").let { (a, b) -> Condition.Contains(a, b) }
            "is_set" -> Condition.IsSet(value?.toString() ?: fail("$path.is_set", "needs a variable name"))
            "not" -> Condition.Not(parseCondition(value as? Map<*, *> ?: fail("$path.not", "must be a condition"), "$path.not"))
            "all", "any" -> {
                val list = value as? List<*> ?: fail("$path.$key", "must be a list of conditions")
                val parsed = list.mapIndexed { i, c ->
                    parseCondition(c as? Map<*, *> ?: fail("$path.$key[$i]", "must be a condition"), "$path.$key[$i]")
                }
                if (key == "all") Condition.All(parsed) else Condition.AnyOf(parsed)
            }
            else -> error("unreachable")
        }
    }

    private fun selector(raw: Any?, path: String): Selector {
        val s = yamlParser.parseSelector(raw as? Map<*, *>) ?: fail(path, "invalid selector")
        selectorStrings(s).forEach { template(it, path) }
        return s
    }

    private fun pair(raw: Any?, path: String): Pair<String, String> {
        val list = raw as? List<*>
        if (list == null || list.size != 2) fail(path, "needs a list of exactly two values")
        return template(list[0], "$path[0]") to template(list[1], "$path[1]")
    }

    // ——— Params, templates, flow calls ———

    private fun parseParams(raw: Any?, path: String): Map<String, ParamSpec> =
        (raw as? Map<*, *>).orEmpty().entries.associate { (k, v) ->
            val spec = v as? Map<*, *>
            k.toString() to ParamSpec(
                required = (spec?.get("required") as? Boolean) ?: (spec?.get("default") == null),
                default = spec?.get("default")?.let { template(it, "$path.$k.default") }
            )
        }

    private fun template(raw: Any?, path: String): String {
        val s = raw?.toString() ?: ""
        try {
            Templates.validate(s)
        } catch (e: ExpressionException) {
            fail(path, e.message ?: "invalid template")
        }
        return s
    }

    /** Every called flow must exist, and flows must not call themselves directly or indirectly. */
    private fun checkCalls(program: Program) {
        fun calls(nodes: List<Node>): List<String> = nodes.flatMap { n ->
            when (n) {
                is Node.Call -> listOf(n.flow)
                is Node.Sequence -> calls(n.nodes)
                is Node.If -> calls(n.then) + calls(n.otherwise)
                is Node.FirstThatWorks -> calls(n.alternatives)
                is Node.Try -> calls(n.body) + calls(n.onError)
                else -> emptyList()
            }
        }
        val graph = program.flows.mapValues { calls(it.value.body) }
        (calls(program.body) + graph.values.flatten()).firstOrNull { it !in program.flows }?.let {
            fail("call", "unknown flow '$it'; defined flows: ${program.flows.keys}")
        }
        val visiting = mutableSetOf<String>()
        val done = mutableSetOf<String>()
        fun visit(flow: String, chain: List<String>) {
            if (flow in done) return
            if (!visiting.add(flow)) fail("flows", "recursive flow calls are not allowed: ${(chain + flow).joinToString(" -> ")}")
            graph.getValue(flow).forEach { visit(it, chain + flow) }
            visiting.remove(flow)
            done.add(flow)
        }
        program.flows.keys.forEach { visit(it, emptyList()) }
    }

    private fun fail(path: String, message: String): Nothing =
        throw YamlParseException("$path: $message", line = 0, column = 0)

    companion object {
        val TOP_LEVEL_KEYS = setOf("name", "description", "params", "variables", "flows", "steps")
        val CONDITION_KEYS = setOf("exists", "not_exists", "equals", "contains", "is_set", "not", "all", "any")

        internal fun selectorStrings(selector: Selector): List<String> = when (selector) {
            is Selector.ByText -> listOf(selector.text)
            is Selector.ByResourceId -> listOf(selector.resourceId)
            is Selector.ByContentDescription -> listOf(selector.description)
            is Selector.ByClassName -> listOf(selector.className)
            is Selector.Composite -> selector.fallbackOrder.flatMap { selectorStrings(it) }
        }

        internal fun mapSelector(selector: Selector, f: (String) -> String): Selector = when (selector) {
            is Selector.ByText -> Selector.ByText(f(selector.text))
            is Selector.ByResourceId -> Selector.ByResourceId(f(selector.resourceId))
            is Selector.ByContentDescription -> Selector.ByContentDescription(f(selector.description))
            is Selector.ByClassName -> selector.copy(className = f(selector.className))
            is Selector.Composite -> Selector.Composite(selector.fallbackOrder.map { mapSelector(it, f) })
        }
    }
}

/** Renders every template in a step's parameters and selector */
internal fun Step.rendered(scope: Scope): Step = copy(
    parameters = parameters.mapValues { (_, v) -> (v as? String)?.let { Templates.render(it, scope) } ?: v },
    selector = selector?.let { DslParser.mapSelector(it) { s -> Templates.render(s, scope) } },
    target = target?.map { s -> Templates.render(s, scope) }
)
