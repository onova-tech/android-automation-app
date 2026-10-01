# Android Unit Testing Pattern

## Purpose

Guide the setup and execution of unit tests for the Android automation runtime using JUnit 5, MockK, and Kotlin coroutines. Covers mocking `AccessibilityNodeInfo` for selector tests, testing parsers without Android dependencies, and coroutine test support.

## When This Pattern Applies

Use this pattern when:
- Writing unit tests for the YAML parser (pure JVM, no Android dependencies)
- Writing unit tests for the selector engine (with mocked `AccessibilityNodeInfo`)
- Writing unit tests for the execution engine (with mocked action handlers)
- Setting up coroutine test infrastructure (`runTest`)
- Testing action handlers in isolation without the Android framework

## Test Dependencies

```kotlin
// app/build.gradle.kts — testImplementation block
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.1")
}
```

## JUnit Platform Configuration

Android's default test runner uses JUnit 4. To use JUnit 5, configure the test options:

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        testOptions {
            unitTests {
                isIncludeAndroidResources = true
                all { test ->
                    test.useJUnitPlatform()
                }
            }
        }
    }
}
```

## Test Directory Structure

```
app/src/test/java/com/proj/automation/
├── parser/
│   ├── YamlParserTest.kt          — Parser unit tests
│   ├── WorkflowAstTest.kt          — Data model validation
│   └── exceptions/
│       └── ParseExceptionTest.kt   — Error handling tests
├── selector/
│   ├── SelectorEngineTest.kt       — Engine with mocked nodes
│   └── strategies/
│       ├── ByTextStrategyTest.kt
│       ├── ByResourceIdStrategyTest.kt
│       ├── ByContentDescriptionStrategyTest.kt
│       └── ByClassNameStrategyTest.kt
├── engine/
│   ├── ExecutionEngineTest.kt      — Step loop orchestration
│   ├── ActionDispatcherTest.kt     — Handler routing
│   ├── ErrorHandlerTest.kt         — Retry/timeout logic
│   └── CancellationTokenTest.kt  — Cancellation support
└── models/
    └── ExecutionResultTest.kt      — Result data class tests
```

## MockK Setup for AccessibilityNodeInfo

`AccessibilityNodeInfo` is an Android framework class. Mock it with MockK to test the selector engine without an Android device:

```kotlin
package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SelectorEngineTest {

    private val engine = SelectorEngine()
    private val rootNode: AccessibilityNodeInfo = mockk()

    @BeforeEach
    fun setup() {
        // Mock root node with typical child structure
        val child1: AccessibilityNodeInfo = mockk(relaxed = true)
        val child2: AccessibilityNodeInfo = mockk(relaxed = true)

        every { rootNode.childCount } returns 2
        every { rootNode.getChild(0) } returns child1
        every { rootNode.getChild(1) } returns child2
    }
}
```

## Testing the YAML Parser

Tests for the parser run on pure JVM — no Android dependencies needed:

```kotlin
package com.proj.automation.parser

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class YamlParserTest {

    private val parser = YamlParser()

    @Test
    fun `parses valid workflow with all fields`() {
        val yaml = """
            name: Test Workflow
            description: A test
            variables:
              pkg: com.test.app
            steps:
              - launch_app:
                  package: com.test.app
              - wait:
                  seconds: 1
        """.trimIndent()

        val workflow = parser.parse(yaml)

        assertEquals("Test Workflow", workflow.name)
        assertEquals("A test", workflow.description)
        assertEquals("com.test.app", workflow.variables["pkg"])
        assertEquals(2, workflow.steps.size)
        assertEquals(ActionType.LAUNCH_APP, workflow.steps[0].action)
        assertEquals("com.test.app", workflow.steps[0].parameters["package"])
        assertEquals(ActionType.WAIT, workflow.steps[1].action)
    }

    @Test
    fun `parses click step with retries and selector`() {
        val yaml = """
            steps:
              - click:
                  selector:
                    text: "Submit"
                  retries: 3
                  retry_delay: 500
        """.trimIndent()

        val workflow = parser.parse(yaml)
        val step = workflow.steps[0]

        assertEquals(ActionType.CLICK, step.action)
        assertEquals(3, step.retries)
        assertEquals(500L, step.retryDelayMs)
        assertTrue(step.selector is Selector.ByText)
        assertEquals("Submit", (step.selector as Selector.ByText).text)
    }

    @Test
    fun `applies default values for missing optional fields`() {
        val yaml = """
            steps:
              - back:
        """.trimIndent()

        val workflow = parser.parse(yaml)
        val step = workflow.steps[0]

        assertEquals(ActionType.BACK, step.action)
        assertEquals(1, step.retries)
        assertEquals(1000L, step.retryDelayMs)
        assertEquals(30000L, step.timeoutMs)
        assertEquals(OnFailurePolicy.ABORT, step.onFailure)
    }

    @Test
    fun `throws YamlParseException on malformed YAML`() {
        val yaml = """
            steps:
              - click:
                  selector:
                    text: "OK"
              - invalid_yaml: [unclosed
        """.trimIndent()

        assertThrows<YamlParseException> {
            parser.parse(yaml)
        }
    }

    @Test
    fun `throws on unknown action type`() {
        val yaml = """
            steps:
              - fake_action:
                  param: value
        """.trimIndent()

        val exception = assertThrows<YamlParseException> {
            parser.parse(yaml)
        }
        assertTrue(exception.message!!.contains("Unknown action"))
    }

    @Test
    fun `handles empty steps list`() {
        val yaml = "name: Empty\nsteps: []"
        val workflow = parser.parse(yaml)
        assertTrue(workflow.steps.isEmpty())
    }

    @Test
    fun `parses all supported action types`() {
        val actions = listOf(
            "launch_app", "wait", "wait_for", "click", "type",
            "back", "home", "scroll", "log"
        )

        for (action in actions) {
            val yaml = "steps:\n  - $action:\n      key: value"
            val workflow = parser.parse(yaml)
            assertEquals(action, workflow.steps[0].action.yamlValue)
        }
    }

    @Test
    fun `parses composite selector fallback`() {
        val yaml = """
            steps:
              - click:
                  selector:
                    fallback:
                      - text: "Submit"
                      - resource_id: "submit_btn"
                      - content_description: "submit"
        """.trimIndent()

        val workflow = parser.parse(yaml)
        val selector = workflow.steps[0].selector
        assertTrue(selector is Selector.Composite)
        assertEquals(3, (selector as Selector.Composite).fallbackOrder.size)
    }

    @Test
    fun `parses on_failure policy types`() {
        val cases = mapOf(
            "abort" to OnFailurePolicy.ABORT,
            "continue" to OnFailurePolicy.CONTINUE,
            "retry(3, 500)" to OnFailurePolicy.RETRY(3, 500),
            null to OnFailurePolicy.ABORT // default
        )

        for ((raw, expected) in cases) {
            val yaml = if (raw != null) {
                "steps:\n  - click:\n      selector:\n        text: \"X\"\n      on_failure: $raw"
            } else {
                "steps:\n  - click:\n      selector:\n        text: \"X\""
            }
            val workflow = parser.parse(yaml)
            assertEquals(expected, workflow.steps[0].onFailure, "Failed for on_failure=$raw")
        }
    }
}
```

## Testing the Selector Engine

Use MockK to mock `AccessibilityNodeInfo` trees:

```kotlin
package com.proj.automation.selector

import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ByTextStrategyTest {

    private val strategy = ByTextStrategy()

    @Test
    fun `finds node by matching text (case-insensitive)`() {
        val node: AccessibilityNodeInfo = mockk()
        every { node.text } returns "Hello World"

        val selector = Selector.ByText("hello world")
        val results = strategy.find(mockRootWithNode(node), selector)

        assertEquals(1, results.size)
        assertEquals(node, results[0])
    }

    @Test
    fun `returns empty list when no node matches`() {
        val node: AccessibilityNodeInfo = mockk()
        every { node.text } returns "Different Text"

        val selector = Selector.ByText("Hello World")
        val results = strategy.find(mockRootWithNode(node), selector)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `returns empty list for null root`() {
        val selector = Selector.ByText("Hello")
        val results = strategy.find(null, selector)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `matches compares text case-insensitively`() {
        val node: AccessibilityNodeInfo = mockk()
        every { node.text } returns "Button"

        assertTrue(strategy.matches(node, Selector.ByText("button")))
        assertTrue(strategy.matches(node, Selector.ByText("BUTTON")))
        assertTrue(strategy.matches(node, Selector.ByText("Button")))
        assertFalse(strategy.matches(node, Selector.ByText("Other")))
    }

    private fun mockRootWithNode(vararg nodes: AccessibilityNodeInfo): AccessibilityNodeInfo {
        val root: AccessibilityNodeInfo = mockk()
        every { root.childCount } returns nodes.size
        nodes.forEachIndexed { i, node ->
            every { root.getChild(i) } returns node
        }
        return root
    }
}
```

## Testing the Execution Engine

```kotlin
package com.proj.automation.engine

import com.proj.automation.engine.models.ExecutionResult
import com.proj.automation.engine.models.OnFailurePolicy
import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import com.proj.automation.parser.Workflow
import com.proj.automation.selector.SelectorEngine
import com.proj.automation.service.AutomationBridge
import com.proj.automation.service.EventBus
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ExecutionEngineTest {

    private lateinit var engine: ExecutionEngine
    private val dispatcher: ActionDispatcher = mockk()
    private val errorHandler: ErrorHandler = mockk(relaxed = true)
    private val eventBus: EventBus = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        engine = ExecutionEngine(dispatcher, errorHandler, eventBus)
    }

    @Test
    fun `executes all steps in order`() = runTest {
        val steps = listOf(
            Step(action = ActionType.BACK),
            Step(action = ActionType.WAIT),
            Step(action = ActionType.LOG, parameters = mapOf("message" to "done"))
        )
        val workflow = Workflow(name = "Test", steps = steps)

        coEvery { dispatcher.dispatch(any(), any()) } returnsMany listOf(
            StepResult(0, ActionType.BACK, true, 10),
            StepResult(1, ActionType.WAIT, true, 1000),
            StepResult(2, ActionType.LOG, true, 1)
        )

        val result = engine.execute(workflow)

        assertTrue(result.completedSuccessfully)
        assertEquals(3, result.steps.size)
        assertEquals(3, result.stepCount)
    }

    @Test
    fun `stops on failure with ABORT policy`() = runTest {
        val steps = listOf(
            Step(action = ActionType.CLICK, onFailure = OnFailurePolicy.ABORT),
            Step(action = ActionType.BACK)
        )
        val workflow = Workflow(name = "Test", steps = steps)

        coEvery { dispatcher.dispatch(any(), any()) } returnsMany listOf(
            StepResult(0, ActionType.CLICK, false, 100, errorMessage = "Not found"),
            StepResult(1, ActionType.BACK, true, 10)
        )

        val result = engine.execute(workflow)

        assertFalse(result.completedSuccessfully)
        assertEquals(1, result.steps.size) // Only first step executed
    }

    @Test
    fun `continues on failure with CONTINUE policy`() = runTest {
        val steps = listOf(
            Step(action = ActionType.CLICK, onFailure = OnFailurePolicy.CONTINUE),
            Step(action = ActionType.BACK)
        )
        val workflow = Workflow(name = "Test", steps = steps)

        coEvery { errorHandler.executeWithPolicy(any(), any(), any()) } answers {
            firstParameter<Step>().onFailure
        }

        val result = engine.execute(workflow)
        assertEquals(2, result.steps.size)
    }

    @Test
    fun `empty workflow returns immediately`() = runTest {
        val workflow = Workflow(name = "Empty", steps = emptyList())
        val result = engine.execute(workflow)
        assertTrue(result.completedSuccessfully)
        assertEquals(0, result.steps.size)
    }
}
```

## Testing ErrorHandler

```kotlin
package com.proj.automation.engine

import com.proj.automation.engine.models.StepResult
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ErrorHandlerTest {

    private val errorHandler = ErrorHandler()
    private val context: ActionContext = mockk(relaxed = true)

    @Test
    fun `returns success on first attempt`() = runTest {
        val step = Step(action = ActionType.LOG)
        val action = suspend { StepResult(0, ActionType.LOG, true, 10) }

        val result = errorHandler.executeWithPolicy(step, action, context)

        assertTrue(result.success)
    }

    @Test
    fun `retries on failure up to max attempts`() = runTest {
        var attempts = 0
        val step = Step(
            action = ActionType.CLICK,
            retries = 3,
            retryDelayMs = 10,
            onFailure = OnFailurePolicy.ABORT
        )

        coEvery { context.throwIfCancelled() } just Runs

        val action = suspend {
            attempts++
            if (attempts < 3) {
                StepResult(0, ActionType.CLICK, false, 10, "Not found")
            } else {
                StepResult(0, ActionType.CLICK, true, 10)
            }
        }

        val result = errorHandler.executeWithPolicy(step, action, context)

        assertTrue(result.success)
        assertEquals(3, attempts)
    }

    @Test
    fun `returns failure after all retries exhausted`() = runTest {
        val step = Step(
            action = ActionType.CLICK,
            retries = 2,
            retryDelayMs = 10,
            onFailure = OnFailurePolicy.ABORT
        )

        val action = suspend {
            StepResult(0, ActionType.CLICK, false, 10, "Always fails")
        }

        val result = errorHandler.executeWithPolicy(step, action, context)

        assertFalse(result.success)
    }
}
```

## Customization Guide

| Parameter | Default | Notes |
|-----------|---------|-------|
| Test framework | JUnit 5 + JUnit Platform | Requires `useJUnitPlatform()` in gradle |
| Mocking library | MockK 1.13.9 | Preferred over Mockito for Kotlin |
| Coroutine testing | `kotlinx-coroutines-test` `runTest` | Standard for suspending function tests |
| `mockk(relaxed = true)` | — | Auto-returns defaults; use sparingly |
| `coEvery` / `coAnswers` | — | Coroutine-aware MockK stubbing |
| Test runner | `JUnitPlatform` | Configure in `android { testOptions }` |

## Validation

After writing tests, verify:

- [ ] `./gradlew :app:testDebugUnitTest` runs all tests green
- [ ] Parser tests cover: valid YAML, malformed YAML, unknown actions, default values, all action types, composite selectors, on_failure policies
- [ ] Selector tests cover: each strategy with matching node, non-matching node, null root, case-insensitive matching
- [ ] Engine tests cover: sequential execution, ABORT on failure, CONTINUE on failure, empty workflow
- [ ] ErrorHandler tests cover: immediate success, retry success, all-retries-failed
- [ ] CancellationToken tests cover: cancel stops execution, isCancelled flag
- [ ] No test requires an Android device or emulator (pure JVM)
- [ ] Coroutine tests use `runTest` and complete within reasonable time
- [ ] MockK is used for `AccessibilityNodeInfo` — no real Android objects in unit tests
