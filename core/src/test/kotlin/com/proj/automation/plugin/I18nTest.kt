package com.proj.automation.plugin

import com.proj.automation.dsl.DslParser
import com.proj.automation.dsl.Interpreter
import com.proj.automation.dsl.Program
import com.proj.automation.dsl.RunStatus
import com.proj.automation.engine.ActionContext
import com.proj.automation.engine.ActionDispatcher
import com.proj.automation.engine.CancellationToken
import com.proj.automation.engine.ErrorHandler
import com.proj.automation.engine.buildHandlerRegistry
import com.proj.automation.parser.YamlParseException
import com.proj.automation.replay.ScreenDevice
import com.proj.automation.service.EventBus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class I18nTest {

    private val strings = mapOf("pt" to mapOf("hello" to "Olá"), "en" to mapOf("hello" to "Hello"))

    @Test
    fun `texts follow the device language with fallbacks`() {
        val p = Program(strings = strings, defaultLanguage = "pt")
        assertEquals("Hello", p.stringsFor("en")["hello"])
        assertEquals("Hello", p.stringsFor("en-US")["hello"])
        assertEquals("Olá", p.stringsFor("PT-br")["hello"])
        assertEquals("Olá", p.stringsFor("fr")["hello"])
        assertEquals("Olá", p.stringsFor(null)["hello"])
        assertTrue(Program().stringsFor("en").isEmpty())
    }

    @Test
    fun `skills read texts as t, in flows too`() = runTest {
        val program = DslParser().parse(
            """
            flows:
              greet: { steps: [ { return: "${'$'}{t.hello}!" } ] }
            steps:
              - call: { flow: greet, into: g }
              - return: "${'$'}{g} ${'$'}{t.hello}"
            """.trimIndent()
        ).copy(strings = strings, defaultLanguage = "pt")
        suspend fun run(lang: String?) = Interpreter(ActionDispatcher(buildHandlerRegistry()), ErrorHandler(), EventBus())
            .run(program, emptyMap(), ActionContext(ScreenDevice(null, language = lang), EventBus(), CancellationToken()))
        assertEquals("Hello! Hello", run("en").returnValue)
        assertEquals("Olá! Olá", run("de").returnValue)
        assertEquals(RunStatus.SUCCEEDED, run(null).status)
    }

    @Test
    fun `t cannot be assigned`() {
        val parser = DslParser()
        assertThrows<YamlParseException> { parser.parse("steps:\n  - set: { t: x }") }
        assertThrows<YamlParseException> { parser.parse("params: { t: {} }\nsteps: []") }
        assertThrows<YamlParseException> { parser.parse("steps:\n  - read_text: { target: { hints: { text: a } }, into: t }") }
        assertThrows<YamlParseException> { parser.parse("flows:\n  f: { steps: [ { return: x } ] }\nsteps:\n  - call: { flow: f, into: t }") }
    }

    // ——— Package checks ———

    private val manifest = """
        schema: 1
        plugin: { id: demo, name: Demo, version: 1.0.0, category: utility, default_language: pt }
        app: { package: com.example.app }
        capabilities: { ui_automation: [com.example.app] }
    """.trimIndent()

    private val base = mapOf(
        "plugin.yaml" to manifest,
        "commands.yaml" to "commands:\n  - { verb: HI, skill: hi }",
        "skills/hi.yaml" to "skill: hi\nsteps:\n  - return: \"\${t.hello}\"",
        "i18n/pt.yaml" to "hello: Olá",
        "i18n/en.yaml" to "hello: Hello"
    )

    private fun load(files: Map<String, String>): Plugin {
        val bytes = files.mapValues { it.value.toByteArray() }.toMutableMap()
        bytes[PackageLock.FILE] = PackageLock.forFiles(bytes, emptyMap()).render().toByteArray()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> bytes.forEach { (p, b) -> z.putNextEntry(ZipEntry(p)); z.write(b); z.closeEntry() } }
        return PluginLoader.load(out.toByteArray())
    }

    private fun fails(files: Map<String, String>, contains: String) {
        val e = assertThrows<PluginPackageException> { load(files) }
        assertTrue(e.message!!.contains(contains), "expected '$contains' in: ${e.message}")
    }

    @Test
    fun `a translated plugin loads and lists its languages`() {
        val plugin = load(base)
        assertEquals(listOf("en", "pt"), plugin.languages)
        assertEquals("Hello", plugin.skills.getValue("hi").program.stringsFor("en")["hello"])
        assertTrue(plugin.installSummary().contains("Languages: en, pt (default pt)"))
    }

    @Test
    fun `translation problems are reported`() {
        fails(base + ("i18n/en.yaml" to "other: x"), "differs from i18n/pt.yaml")
        fails(base + ("skills/hi.yaml" to "skill: hi\nsteps:\n  - return: \"\${t.bye}\""), "not defined in i18n")
        fails(base + ("plugin.yaml" to manifest.replace(", default_language: pt", "")), "default_language' is required")
        fails(base + ("plugin.yaml" to manifest.replace("default_language: pt", "default_language: fr")), "has no i18n/fr.yaml")
        fails(base - "i18n/pt.yaml" - "i18n/en.yaml" + ("plugin.yaml" to manifest), "has no i18n/ texts")
        fails(base + ("i18n/en.yaml" to "hello: [1, 2]"), "must be a text")
        assertThrows<PluginPackageException> { load(base + ("i18n/english.yaml" to "hello: x")) }
    }
}
