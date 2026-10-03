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
                """
                steps:
                  - click:
                      selector:
                        text: "X"
                      on_failure: $raw
                """.trimIndent()
            } else {
                """
                steps:
                  - click:
                      selector:
                        text: "X"
                """.trimIndent()
            }
            val workflow = parser.parse(yaml)
            assertEquals(expected, workflow.steps[0].onFailure, "Failed for on_failure=$raw")
        }
    }

    @Test
    fun `parses resource_id selector`() {
        val yaml = """
            steps:
              - click:
                  selector:
                    resource_id: "com.app:id/submit"
        """.trimIndent()

        val workflow = parser.parse(yaml)
        val selector = workflow.steps[0].selector
        assertTrue(selector is Selector.ByResourceId)
        assertEquals("com.app:id/submit", (selector as Selector.ByResourceId).resourceId)
    }

    @Test
    fun `parses content_description selector`() {
        val yaml = """
            steps:
              - click:
                  selector:
                    content_description: "submit_button"
        """.trimIndent()

        val workflow = parser.parse(yaml)
        val selector = workflow.steps[0].selector
        assertTrue(selector is Selector.ByContentDescription)
        assertEquals("submit_button", (selector as Selector.ByContentDescription).description)
    }

    @Test
    fun `parses class_name selector with index`() {
        val yaml = """
            steps:
              - click:
                  selector:
                    class_name: "android.widget.Button"
                    index: 2
        """.trimIndent()

        val workflow = parser.parse(yaml)
        val selector = workflow.steps[0].selector
        assertTrue(selector is Selector.ByClassName)
        val classNameSelector = selector as Selector.ByClassName
        assertEquals("android.widget.Button", classNameSelector.className)
        assertEquals(2, classNameSelector.index)
    }

    @Test
    fun `handles empty YAML document`() {
        assertThrows<YamlParseException> {
            parser.parse("")
        }
    }

    @Test
    fun `parses workflow with wait_for step`() {
        val yaml = """
            steps:
              - wait_for:
                  selector:
                    text: "Ready"
                  timeout: 5000
        """.trimIndent()

        val workflow = parser.parse(yaml)
        val step = workflow.steps[0]
        assertEquals(ActionType.WAIT_FOR, step.action)
        assertEquals(5000L, step.timeoutMs)
        assertTrue(step.selector is Selector.ByText)
    }

    @Test
    fun `parses workflow with variables`() {
        val yaml = """
            name: Var Test
            variables:
              calc_pkg: com.android.calculator2
              delay: "2"
            steps:
              - launch_app:
                  package: com.android.calculator2
        """.trimIndent()

        val workflow = parser.parse(yaml)
        assertEquals("Var Test", workflow.name)
        assertEquals("com.android.calculator2", workflow.variables["calc_pkg"])
        assertEquals("2", workflow.variables["delay"])
    }
}
