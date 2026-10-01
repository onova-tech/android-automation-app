package com.proj.automation.dsl

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TemplatesTest {

    private val scope = Scope(
        mapOf(
            "text" to "on my way",
            "to" to mapOf("phone" to "5511999998888", "name" to "Maria"),
            "balance" to "1234.56"
        )
    )

    @Test
    fun `renders variables and nested fields`() {
        assertEquals("Hi Maria: on my way", Templates.render("Hi \${to.name}: \${text}", scope))
    }

    @Test
    fun `applies filters in order`() {
        assertEquals("on%20my%20way", Templates.render("\${text|urlencode}", scope))
        assertEquals("MARIA", Templates.render("\${ to.name | upper }", scope))
        assertEquals("*****56", Templates.render("\${balance|mask}", scope))
    }

    @Test
    fun `escaped placeholder renders literally`() {
        assertEquals("cost \${x}", Templates.render("cost $\${x}", scope))
    }

    @Test
    fun `undefined variable fails instead of rendering empty`() {
        assertThrows<ExpressionException> { Templates.render("\${missing}", scope) }
        assertThrows<ExpressionException> { Templates.render("\${to.missing}", scope) }
        // a map is not a printable value
        assertThrows<ExpressionException> { Templates.render("\${to}", scope) }
    }

    @Test
    fun `validate rejects unknown filters and bad references`() {
        assertThrows<ExpressionException> { Templates.validate("\${text|shell}") }
        assertThrows<ExpressionException> { Templates.validate("\${1abc}") }
        assertThrows<ExpressionException> { Templates.validate("\${a b}") }
        Templates.validate("plain text, \${ok.path|trim}")
    }

    @Test
    fun `rendered values are not rendered again`() {
        val s = Scope(mapOf("payload" to "\${secret}", "secret" to "x"))
        assertEquals("\${secret}", Templates.render("\${payload}", s))
    }
}
