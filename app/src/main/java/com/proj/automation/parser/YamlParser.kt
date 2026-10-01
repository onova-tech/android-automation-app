package com.proj.automation.parser

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.StringReader

/**
 * Parses YAML workflow strings into typed Workflow objects using SnakeYAML 2.x.
 *
 * Handles polymorphic action deserialization, selector parsing, and error reporting
 * with line/column information for malformed input.
 */
class YamlParser {

    private val yaml: Yaml

    constructor() {
        this.yaml = createSafeLoader()
    }

    /**
     * Parse a YAML string into a typed Workflow object.
     *
     * @throws YamlParseException if the YAML is malformed or has invalid actions
     * @throws ValidationException if required parameters are missing
     */
    fun parse(yamlString: String): Workflow {
        return try {
            val reader = StringReader(yamlString)
            val raw = yaml.load<Map<String, Any?>>(reader)

            if (raw == null) {
                throw YamlParseException("Empty YAML document", line = 0, column = 0)
            }

            val name = raw["name"] as? String
            val description = raw["description"] as? String
            val variables = (raw["variables"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                ?.mapValues { it.value.toString() } ?: emptyMap()
            val rawSteps: List<Any?> = raw["steps"] as? List<Any?> ?: emptyList()

            val steps = rawSteps.mapIndexed { index, stepRaw ->
                parseStep(stepRaw, index + 1)
            }

            Workflow(
                name = name,
                description = description,
                steps = steps,
                variables = variables
            )
        } catch (e: YamlParseException) {
            throw e
        } catch (e: Exception) {
            throw YamlParseException(
                message = "YAML parse error: ${e.message}",
                line = 0,
                column = 0
            )
        }
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

        val selector = parseSelector(rawParams["selector"] as? Map<*, *>)

        return Step(
            action = actionType,
            parameters = params,
            selector = selector,
            retries = (rawParams["retries"] as? Number)?.toInt() ?: 1,
            retryDelayMs = (rawParams["retry_delay"] as? Number)?.toLong()
                ?: (rawParams["retryDelayMs"] as? Number)?.toLong() ?: 1000,
            timeoutMs = (rawParams["timeout"] as? Number)?.toLong()
                ?: (rawParams["timeoutMs"] as? Number)?.toLong() ?: 30000,
            onFailure = parseOnFailurePolicy(rawParams["on_failure"] as? String)
        )
    }

    internal fun parseSelector(raw: Map<*, *>?): Selector? {
        if (raw == null) return null
        return when {
            "text" in raw -> Selector.ByText(raw["text"].toString())
            "resource_id" in raw -> Selector.ByResourceId(raw["resource_id"].toString())
            "content_description" in raw -> Selector.ByContentDescription(
                raw["content_description"].toString()
            )
            "class_name" in raw -> Selector.ByClassName(
                raw["class_name"].toString(),
                (raw["index"] as? Number)?.toInt()
            )
            "fallback" in raw -> {
                val fallbackList: List<Any?> = raw["fallback"] as? List<Any?> ?: emptyList()
                val selectors = fallbackList.mapNotNull {
                    @Suppress("UNCHECKED_CAST")
                    parseSelector(it as? Map<*, *>)
                }
                if (selectors.isNotEmpty()) Selector.Composite(selectors) else null
            }
            else -> null
        }
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
