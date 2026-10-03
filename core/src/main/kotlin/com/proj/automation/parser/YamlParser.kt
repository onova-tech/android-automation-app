package com.proj.automation.parser

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.StringReader

/**
 * Safe YAML loading (SnakeYAML 2.x with alias, recursion and duplicate-key limits) and parsing of
 * single action steps. Programs, skills and flows are parsed by [com.proj.automation.dsl.DslParser].
 */
class YamlParser {

    private val yaml: Yaml

    constructor() {
        this.yaml = createSafeLoader()
    }

    /** Loads a YAML document as a map, wrapping errors in [YamlParseException] */
    internal fun loadDocument(yamlString: String): Map<String, Any?> {
        val raw = try {
            yaml.load<Any?>(StringReader(yamlString))
        } catch (e: Exception) {
            throw YamlParseException("YAML parse error: ${e.message}", line = 0, column = 0)
        }
        @Suppress("UNCHECKED_CAST")
        return when (raw) {
            null -> throw YamlParseException("Empty YAML document", line = 0, column = 0)
            is Map<*, *> -> raw as Map<String, Any?>
            else -> throw YamlParseException("Top level must be a mapping", line = 0, column = 0)
        }
    }

    internal fun parseStep(raw: Any?, stepIndex: Int): Step {
        if (raw !is Map<*, *>) {
            throw YamlParseException(
                message = "Step at index $stepIndex must be a mapping (key: value)",
                line = stepIndex,
                column = 0
            )
        }

        if (raw.size != 1) {
            throw YamlParseException(
                message = "Step at index $stepIndex must have exactly one action key, " +
                    "found ${raw.size}: ${raw.keys}",
                line = stepIndex,
                column = 0
            )
        }

        val actionKey = raw.keys.first() as? String
            ?: throw YamlParseException(
                message = "Step at index $stepIndex has a non-string key",
                line = stepIndex,
                column = 0
            )

        val actionType = try {
            ActionType.fromYaml(actionKey)
        } catch (e: UnknownActionException) {
            throw YamlParseException(
                message = e.message + " (step $stepIndex)",
                line = stepIndex,
                column = 0
            )
        }

        val rawParams: Map<String, Any?> = raw[actionKey] as? Map<String, Any?> ?: emptyMap()
        val params = rawParams.mapKeys { it.key.toString() }
            .mapValues { it.value?.toString() ?: "" }

        if ("selector" in rawParams) {
            throw YamlParseException("Step $stepIndex: 'selector' was replaced by 'target'", line = stepIndex, column = 0)
        }
        val target = (rawParams["target"] as? Map<*, *>)?.let {
            try {
                com.proj.automation.resolve.Target.parse(it)
            } catch (e: IllegalArgumentException) {
                throw YamlParseException("Step $stepIndex target: ${e.message}", line = stepIndex, column = 0)
            }
        }

        return Step(
            action = actionType,
            parameters = params,
            target = target,
            retries = (rawParams["retries"] as? Number)?.toInt() ?: 1,
            retryDelayMs = (rawParams["retry_delay"] as? Number)?.toLong()
                ?: (rawParams["retryDelayMs"] as? Number)?.toLong() ?: 1000,
            timeoutMs = (rawParams["timeout"] as? Number)?.toLong()
                ?: (rawParams["timeoutMs"] as? Number)?.toLong() ?: 30000,
            onFailure = parseOnFailurePolicy(rawParams["on_failure"] as? String)
        )
    }

    private fun parseOnFailurePolicy(raw: String?): OnFailurePolicy {
        return when (raw?.lowercase()) {
            "abort", null -> OnFailurePolicy.ABORT
            "continue" -> OnFailurePolicy.CONTINUE
            else -> {
                // Try parsing RETRY: "retry(3, 500)"
                val retryMatch = Regex("""retry\(\s*(\d+)\s*,\s*(\d+)\s*\)""").find(raw)
                if (retryMatch != null) {
                    OnFailurePolicy.RETRY(
                        maxAttempts = retryMatch.groupValues[1].toInt(),
                        delayMs = retryMatch.groupValues[2].toLong()
                    )
                } else {
                    OnFailurePolicy.ABORT // Default fallback
                }
            }
        }
    }

    companion object {
        /**
         * Create a SnakeYAML loader with security constraints.
         * Disables multi-document streams and duplicate keys.
         */
        private fun createSafeLoader(): Yaml {
            val loaderOptions = LoaderOptions().apply {
                // SnakeYAML 2.x: aliasing is always enabled; bound the limit to avoid a
                // denial-of-service via unbounded alias expansion.
                maxAliasesForCollections = 50
                // Disallow recursive (self-referencing) aliases.
                allowRecursiveKeys = false
                // Disallow duplicate mapping keys (prevents accidental overrides).
                setAllowDuplicateKeys(false)
            }

            val constructor = org.yaml.snakeyaml.constructor.Constructor(
                Map::class.java,
                loaderOptions
            )
            val propertyUtils = org.yaml.snakeyaml.introspector.PropertyUtils()
            propertyUtils.setSkipMissingProperties(true)
            constructor.propertyUtils = propertyUtils

            // SnakeYAML 2.x: the Constructor is already bound to loaderOptions, so pass
            // it directly (no Yaml(Constructor, LoaderOptions) overload exists).
            return Yaml(constructor)
        }
    }
}
