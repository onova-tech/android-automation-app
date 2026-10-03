package com.proj.automation.dsl

import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Selector
import com.proj.automation.parser.YamlParseException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DslParserTest {

    private val parser = DslParser()

    @Test
    fun `parses a v1 workflow unchanged`() {
        val program = parser.parse(
            """
            name: Calculator
            steps:
              - launch_app: { package: com.google.android.calculator }
              - click: { selector: { text: "2" }, retries: 3 }
            """.trimIndent()
        )
        assertEquals("Calculator", program.name)
        assertEquals(2, program.body.size)
        val click = program.body[1] as Node.Action
        assertEquals(ActionType.CLICK, click.step.action)
        assertEquals(Selector.ByText("2"), click.step.selector)
        assertEquals(3, click.step.retries)
    }

    @Test
    fun `parses control flow, params and flows`() {
        val program = parser.parse(
            """
            params:
              to: { required: true }
              text: { default: "hello" }
            flows:
              open_chat:
                params: { who: {} }
                steps:
                  - open_chat_marker: {}
            steps:
              - set: { greeting: "hi ${'$'}{to}" }
              - if:
                  equals: ["${'$'}{to}", "maria"]
                  then:
                    - log: { message: "maria" }
                  else:
                    - log: { message: "other" }
              - first_that_works:
                  - click: { selector: { text: "A" } }
                  - click: { selector: { text: "B" } }
              - try:
                  do:
                    - call: { flow: open_chat, with: { who: "${'$'}{to}" }, into: chat }
                  on_error:
                    - log: { message: "failed ${'$'}{error.code}" }
              - assert: { is_set: greeting, message: "no greeting" }
              - return: "done ${'$'}{greeting}"
            """.trimIndent().replace("open_chat_marker", "back")
        )
        assertEquals(setOf("to", "text"), program.params.keys)
        assertTrue(program.params.getValue("to").required)
        assertEquals("hello", program.params.getValue("text").default)
        assertFalse(program.params.getValue("text").required)
        assertEquals(setOf("open_chat"), program.flows.keys)

        val kinds = program.body.map { it::class.simpleName }
        assertEquals(listOf("SetVars", "If", "FirstThatWorks", "Try", "Assert", "Return"), kinds)
        val iff = program.body[1] as Node.If
        assertEquals(Condition.Equals("\${to}", "maria"), iff.condition)
        assertEquals(1, iff.then.size)
        assertEquals(1, iff.otherwise.size)
        val call = (program.body[3] as Node.Try).body.single() as Node.Call
        assertEquals("open_chat", call.flow)
        assertEquals("chat", call.into)
    }

    @Test
    fun `expect is parsed as a post-condition and removed from parameters`() {
        val program = parser.parse(
            """
            steps:
              - click:
                  selector: { content_description: "Enviar" }
                  expect: { exists: { text: "sent" } }
            """.trimIndent()
        )
        val action = program.body.single() as Node.Action
        assertEquals(Condition.Exists(Selector.ByText("sent")), action.expect)
        assertFalse("expect" in action.step.parameters)
    }

    @Test
    fun `parses nested conditions`() {
        val program = parser.parse(
            """
            steps:
              - assert:
                  all:
                    - not: { not_exists: { text: "Saldo" } }
                    - any:
                        - contains: ["abc", "b"]
                        - is_set: x
            """.trimIndent()
        )
        val condition = (program.body.single() as Node.Assert).condition as Condition.All
        assertTrue(condition.conditions[0] is Condition.Not)
        assertTrue(condition.conditions[1] is Condition.AnyOf)
    }

    @Test
    fun `rejects unknown flows and recursive calls`() {
        assertThrows<YamlParseException> {
            parser.parse("steps:\n  - call: { flow: nope }")
        }
        val e = assertThrows<YamlParseException> {
            parser.parse(
                """
                flows:
                  a: { steps: [ { call: { flow: b } } ] }
                  b: { steps: [ { call: { flow: a } } ] }
                steps:
                  - call: { flow: a }
                """.trimIndent()
            )
        }
        assertTrue(e.message!!.contains("recursive"))
    }

    @Test
    fun `rejects bad templates at parse time`() {
        assertThrows<YamlParseException> { parser.parse("steps:\n  - log: { message: \"\${x|evil}\" }") }
        assertThrows<YamlParseException> { parser.parse("steps:\n  - click: { selector: { text: \"\${}\" } }") }
    }

    @Test
    fun `bundled examples parse`() {
        java.io.File("../examples").listFiles { f -> f.extension == "yaml" }!!.forEach {
            assertNotNull(parser.parse(it.readText()), it.name)
        }
    }

    @Test
    fun `rejects malformed steps and unknown top-level keys`() {
        assertThrows<YamlParseException> { parser.parse("stepz: []") }
        assertThrows<YamlParseException> { parser.parse("steps:\n  - if: { then: [] }") }
        assertThrows<YamlParseException> { parser.parse("steps:\n  - assert: { equals: [\"only one\"] }") }
        assertThrows<YamlParseException> { parser.parse("steps:\n  - fly: {}") }
        assertThrows<YamlParseException> { parser.parse("steps:\n  - try: { on_error: [] }") }
    }
}
