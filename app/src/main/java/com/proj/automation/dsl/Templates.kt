package com.proj.automation.dsl

import java.net.URLEncoder

class ExpressionException(message: String) : RuntimeException(message)

/**
 * `${path | filter | filter}` interpolation. Paths are dotted variable names; filters are a
 * fixed set of pure functions. There is no arbitrary evaluation. `$${` renders a literal `${`.
 */
object Templates {

    private val PLACEHOLDER = Regex("""\$\$\{|\$\{([^}]*)}""")
    private val PATH = Regex("""[A-Za-z_][A-Za-z0-9_]*(\.([A-Za-z_][A-Za-z0-9_]*|\d+))*""")

    val FILTERS: Map<String, (String) -> String> = mapOf(
        "urlencode" to { s -> URLEncoder.encode(s, "UTF-8").replace("+", "%20") },
        "upper" to { s -> s.uppercase() },
        "lower" to { s -> s.lowercase() },
        "trim" to { s -> s.trim() },
        // Keeps the last 2 characters; enough for a person to recognize a value in an SMS
        "mask" to { s -> if (s.length <= 2) "*".repeat(s.length) else "*".repeat(s.length - 2) + s.takeLast(2) }
    )

    /** Checks syntax, paths and filter names without variables. Throws [ExpressionException]. */
    fun validate(template: String) {
        PLACEHOLDER.findAll(template).forEach { m -> if (m.value != "$\${") parse(m.groupValues[1]) }
    }

    /** Variable paths referenced by a template (first segment only) */
    fun referencedRoots(template: String): Set<String> =
        PLACEHOLDER.findAll(template)
            .filter { it.value != "$\${" }
            .map { parse(it.groupValues[1]).first.substringBefore('.') }
            .toSet()

    fun render(template: String, scope: Scope): String =
        PLACEHOLDER.replace(template) { m ->
            if (m.value == "$\${") return@replace "\${"
            val (path, filters) = parse(m.groupValues[1])
            val value = scope.lookup(path)
                ?: throw ExpressionException("Undefined variable '$path'")
            filters.fold(value) { acc, f -> FILTERS.getValue(f)(acc) }
        }

    private fun parse(expr: String): Pair<String, List<String>> {
        val parts = expr.split('|').map { it.trim() }
        val path = parts.first()
        if (!PATH.matches(path)) throw ExpressionException("Invalid variable reference '\${$expr}'")
        val filters = parts.drop(1)
        filters.firstOrNull { it !in FILTERS }?.let {
            throw ExpressionException("Unknown filter '$it' in '\${$expr}'. Known: ${FILTERS.keys.joinToString()}")
        }
        return path to filters
    }
}

/**
 * Variable scope. Values are strings or nested maps (for dotted access such as `contact.phone`).
 * Flow calls get a fresh scope, so flows cannot read or change their caller's variables.
 */
class Scope(initial: Map<String, Any?> = emptyMap()) {
    private val vars = initial.toMutableMap()

    operator fun set(name: String, value: Any?) {
        vars[name] = value
    }

    fun isSet(path: String): Boolean = lookup(path) != null

    /**
     * Resolves a dotted path. Lists support `.size` and numeric indexes (`items.0`) and render
     * one element per line.
     */
    fun lookup(path: String): String? {
        var current: Any? = vars
        for (segment in path.split('.')) {
            current = when (val c = current) {
                is Map<*, *> -> c[segment]
                is List<*> -> if (segment == "size") c.size else segment.toIntOrNull()?.let { c.getOrNull(it) }
                else -> null
            } ?: return null
        }
        return when (val c = current) {
            null, is Map<*, *> -> null
            is List<*> -> c.joinToString("\n")
            else -> c.toString()
        }
    }
}
