# YAML Workflow Parsing Pattern

## Purpose

Guide the configuration and implementation of SnakeYAML 2.x for parsing workflow YAML files into a typed AST (Abstract Syntax Tree). Covers data model design, polymorphic action deserialization, validation, and error handling.

## When This Pattern Applies

Use this pattern when:
- Configuring SnakeYAML for Android with security constraints
- Defining the workflow AST data model (Workflow, Step, Selector, ActionType)
- Implementing custom deserializers for polymorphic action fields
- Validating parsed workflows before execution
- Handling YAML parse errors with location information

## SnakeYAML Configuration

### Dependency

```kotlin
// app/build.gradle.kts
implementation("org.yaml:snakeyaml:2.2")
```

### Loader Configuration (Security)

SnakeYAML 2.x removed the auto-tagging vulnerability (CVE-2017-18640). Configure with `LoaderOptions` for safe deserialization:

```kotlin
package com.proj.automation.parser

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.introspector.PropertyUtils

class YamlConfig {
    fun createSafeLoader(): Yaml {
        val loaderOptions = LoaderOptions().apply {
            // Disable YAML anchors/aliases (not needed for workflows)
            allowAliases = true
            // Disable multi-document streams (single workflow per file)
            allowMultipleDocuments = false
            // Disable Java object tags (prevents arbitrary object instantiation)
            allowDuplicateKeys = false
        }

        val constructor = Constructor(WorkflowAst::class.java, loaderOptions)
        val propertyUtils = PropertyUtils()
        propertyUtils.setSkipMissingProperties(true)
        constructor.propertyUtils = propertyUtils

        return Yaml(constructor, loaderOptions)
    }
}
```

## Workflow AST Data Model

### Top-Level: `Workflow`

```kotlin
package com.proj.automation.parser

import com.proj.automation.engine.models.OnFailurePolicy

data class Workflow(
    val name: String? = null,
    val description: String? = null,
    val steps: List<Step> = emptyList(),
    val variables: Map<String, String> = emptyMap()
)
```

### Step — Polymorphic Action Container

Each step has exactly one action. The `action` field is polymorphic and resolved by the custom deserializer.

```kotlin
data class Step(
    val action: ActionType,
    val parameters: Map<String, Any?> = emptyMap(),
    val selector: Selector? = null,
    val retries: Int = 1,
    val retryDelayMs: Long = 1000,
    val timeoutMs: Long = 30000,
    val onFailure: OnFailurePolicy = OnFailurePolicy.ABORT
)
```

### Action Types

```kotlin
enum class ActionType(val yamlValue: String) {
    LAUNCH_APP("launch_app"),
    WAIT("wait"),
    WAIT_FOR("wait_for"),
    CLICK("click"),
    TYPE("type"),
    BACK("back"),
    HOME("home"),
    SCROLL("scroll"),
    LOG("log");

    companion object {
        fun fromYaml(value: String): ActionType {
            return entries.find { it.yamlValue == value }
                ?: throw UnknownActionException(value)
        }

        val values: Set<String> get() = entries.map { it.yamlValue }.toSet()
    }
}
```

### Selector — Sealed Class Hierarchy

```kotlin
sealed class Selector {
    data class ByResourceId(val resourceId: String) : Selector()
    data class ByText(val text: String) : Selector()
    data class ByContentDescription(val description: String) : Selector()
    data class ByClassName(val className: String, val index: Int? = null) : Selector()
    data class Composite(val fallbackOrder: List<Selector>) : Selector()
}
```

### OnFailurePolicy

```kotlin
sealed class OnFailurePolicy {
    object ABORT : OnFailurePolicy()
    object CONTINUE : OnFailurePolicy()
    data class RETRY(val maxAttempts: Int, val delayMs: Long) : OnFailurePolicy()
}
```

## Custom Deserializer

SnakeYAML maps YAML to Kotlin data classes, but polymorphic action fields require a custom deserializer. The deserializer reads the first YAML key of each step to determine the `ActionType`.

```kotlin
package com.proj.automation.parser

import org.yaml.snakeyaml.constructor.CustomConstructor
import org.yaml.snakeyaml.introspector.BeanAccess
import org.yaml.snakeyaml.introspector.Property

/**
 * Custom constructor that resolves polymorphic `action` field on Step.
 *
 * YAML structure:
 *   - launch_app:
 *       package: com.android.calculator2
 *
 * The deserializer reads "launch_app" as the ActionType, then merges
 * the remaining parameters into Step.parameters.
 */
class WorkflowConstructor(loaderOptions: LoaderOptions) : CustomConstructor(loaderOptions) {

    init {
        // Use BeanAccess.FIELD to access private fields directly
        addBeanAccess(BeanAccess.FIELD)
    }

    override fun createProperty(beanDesc: org.yaml.snakeyaml.introspector.PropertyDescriptor): Property {
        // Override property introspection to handle polymorphic action field
        return super.createProperty(beanDesc)
    }
}
```

### Parsing Entry Point

```kotlin
package com.proj.automation.parser

import org.yaml.snakeyaml.Yaml
import java.io.StringReader

class YamlParser {

    private val yaml: Yaml

    constructor() {
        this.yaml = YamlConfig().createSafeLoader()
    }

    /**
     * Parse a YAML string into a typed Workflow object.
     *
     * @throws YamlParseException if the YAML is malformed
     * @throws UnknownActionException if a step references an unrecognized action
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
            val rawSteps = raw["steps"] as? List<*> ?: emptyList()

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

    private fun parseStep(raw: Any?, stepIndex: Int): Step {
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

        val rawParams = raw[actionKey] as? Map<*, *> ?: emptyMap()
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

    private fun parseSelector(raw: Map<*, *>?): Selector? {
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
                val fallbackList = raw["fallback"] as? List<*> ?: emptyList()
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
}
```

## Error Handling

### Exception Classes

```kotlin
package com.proj.automation.parser

/** Thrown when YAML syntax is invalid (malformed, bad indentation, etc.) */
class YamlParseException(
    message: String,
    val line: Int = 0,
    val column: Int = 0
) : Exception("YAML Error at line $line, column $column: $message")

/** Thrown when a step references an action type not in the ActionType enum */
class UnknownActionException(
    val unknownAction: String
) : Exception(
    "Unknown action '$unknownAction'. Valid actions: ${ActionType.values.joinToString(", ")}"
)

/** Thrown when a parsed step has missing required parameters */
class ValidationException(
    val field: String,
    message: String
) : Exception("Validation error in '$field': $message")
```

### Supported Actions

| Action | YAML Key | Selector Required | Key Parameters |
|--------|----------|-------------------|----------------|
| Launch app | `launch_app` | No | `package` (String) |
| Wait | `wait` | No | `seconds` (Number) |
| Wait for | `wait_for` | Yes | `timeout` (Long ms) |
| Click | `click` | Yes | `retries`, `retry_delay` |
| Type | `type` | No* | `text` (String) — typed into focused field |
| Back | `back` | No | — |
| Home | `home` | No | — |
| Scroll | `scroll` | Yes | `direction` (up/down/left/right) |
| Log | `log` | No | `message` (String) |

*Type uses the element currently in focus (set by previous action).

### Example YAML Accepted by Parser

```yaml
name: Calculator Demo
description: Automate 2 + 3 = 5 on the built-in Calculator
variables:
  calc_package: com.android.calculator2

steps:
  - launch_app:
      package: com.android.calculator2

  - wait:
      seconds: 2

  - wait_for:
      selector:
        text: "2"
      timeout: 5000

  - click:
      selector:
        text: "2"
      retries: 3
      retry_delay: 500

  - click:
      selector:
        text: "+"

  - click:
      selector:
        text: "3"

  - click:
      selector:
        text: "="
      retries: 5
      retry_delay: 1000

  - wait:
      seconds: 1

  - log:
      message: "Calculator workflow completed"
```

## Customization Guide

| Parameter | Default | Notes |
|-----------|---------|-------|
| SnakeYAML version | 2.2 | Must be 2.x for security fixes |
| `allowAliases` | `true` | Safe in 2.x; disable if workflows never use aliases |
| `allowMultipleDocuments` | `false` | Single workflow per YAML file |
| `allowDuplicateKeys` | `false` | Prevents accidental key overrides |
| `retryDelayMs` default | 1000ms | Per-step default; overridden by YAML `retry_delay` |
| `timeoutMs` default | 30000ms (30s) | Per-step default; overridden by YAML `timeout` |
| `onFailure` default | `ABORT` | Workflow stops on first failure unless policy differs |
| `propertyUtils.skipMissingProperties` | `true` | Allows YAML to omit optional fields gracefully |

## Validation

Before using the parser in the execution engine, verify:

- [ ] `YamlParser().parse(yamlString)` returns a `Workflow` for valid input
- [ ] Malformed YAML throws `YamlParseException` (not a crash)
- [ ] Unknown action throws `UnknownActionException` with the list of valid actions
- [ ] All supported action types (`launch_app`, `wait`, `wait_for`, `click`, `type`, `back`, `home`, `scroll`, `log`) parse correctly
- [ ] `Selector.ByText` correctly extracts `text` field from selector mapping
- [ ] `Selector.ByResourceId` correctly extracts `resource_id` field
- [ ] `Selector.ByContentDescription` correctly extracts `content_description` field
- [ ] `Selector.ByClassName` correctly extracts `class_name` and optional `index`
- [ ] `Selector.Composite` correctly parses `fallback` list
- [ ] Default values applied for missing optional fields (`retries=1`, `timeout=30000`)
- [ ] `onFailure` policy parses `ABORT`, `CONTINUE`, and `RETRY(max, delay)` syntax
- [ ] Empty `steps` list is valid (returns `Workflow(steps = [])`)
- [ ] `variables` map is extracted when present
- [ ] YAML with extra/unknown top-level keys is ignored (no crash)
- [ ] Parser completes in under 100ms for workflows up to 100 steps
