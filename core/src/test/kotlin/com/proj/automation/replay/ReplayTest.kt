package com.proj.automation.replay

import com.proj.automation.dsl.RunStatus
import com.proj.automation.engine.ErrorCode
import com.proj.automation.plugin.PackageBuilder
import com.proj.automation.plugin.PluginPackageException
import com.proj.automation.resolve.Hints
import com.proj.automation.resolve.Resolution
import com.proj.automation.resolve.Role
import com.proj.automation.resolve.Target
import com.proj.automation.resolve.TargetResolver
import com.proj.automation.ui.UiNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class ReplayTest {

    private val dir = File("src/test/resources/whatsapp")
    private val plugin = PackageBuilder.build(dir, File("src/test/resources/libraries")).plugin
    private fun fixture(name: String) = dir.resolve("fixtures/$name").takeIf { it.isFile }?.readText()

    @Test
    fun `every bundled whatsapp replay test passes`() {
        val files = dir.resolve("tests").listFiles { f -> f.extension == "yaml" }!!.sortedBy { it.name }
        assertTrue(files.size >= 4)
        for (f in files) {
            val outcome = Replay.run(plugin, Replay.parseCase(f.readText(), f.name), ::fixture)
            assertTrue(outcome.passed, "${f.name}: ${outcome.failures} ${outcome.interactions}")
        }
    }

    @Test
    fun `failures are reported, not thrown`() {
        val case = Replay.parseCase(
            """
            test: wrong expectation
            skill: send
            args: { phone: "1", text: "oi" }
            expect: { status: succeeded, interactions: [] }
            """.trimIndent(), "inline"
        )
        val outcome = Replay.run(plugin, case, ::fixture)
        assertFalse(outcome.passed)
        assertEquals(RunStatus.FAILED, outcome.result!!.status)
        assertEquals(ErrorCode.E_TIMEOUT, outcome.result!!.errorCode)
        assertTrue(outcome.failures.any { it.startsWith("status") })
        assertTrue(outcome.failures.any { it.startsWith("interactions") })
    }

    @Test
    fun `test files are validated`() {
        assertThrows<PluginPackageException> { Replay.parseCase("test: x\nskill: s", "a.yaml") } // no expect
        assertThrows<PluginPackageException> { Replay.parseCase("skill: s\nexpect: { status: maybe }", "b.yaml") }
        assertThrows<PluginPackageException> { Replay.parseCase("skill: s\nexpect: { status: failed }\ntransitions: [ { on: click } ]", "c.yaml") }
        assertThrows<PluginPackageException> { Replay.parseCase("skill: s\nexpect: { status: failed }\nextra: 1", "d.yaml") }
        val missingFixture = Replay.parseCase("skill: send\nargs: { phone: '1', text: x }\nstart: nope.xml\nexpect: { status: failed }", "e.yaml")
        assertThrows<PluginPackageException> { Replay.run(plugin, missingFixture, ::fixture) }
    }

    @Test
    fun `screen device only acts on what it shows and follows transitions`() {
        val a = UiNode(text = "A", clickable = true)
        val b = UiNode(text = "B", clickable = true)
        val device = ScreenDevice(UiNode(children = listOf(a)), listOf(Transition("click", "A", UiNode(children = listOf(b)))))
        assertFalse(device.click(b)) // not on screen
        assertTrue(device.click(a))
        assertSame(b, device.snapshot()!!.children.single())
        assertEquals(listOf("click:A"), device.interactions)
    }

    @Test
    fun `exact hints only count for elements of the expected role`() {
        val screen = UiNode(children = listOf(
            UiNode(text = "oi", className = "android.widget.TextView"),
            UiNode(text = "Mensagem", className = "android.widget.EditText", editable = true, clickable = true)
        ))
        val r = TargetResolver().resolve(Target(role = Role.EDIT_TEXT, hints = Hints(text = "oi")), screen)
        assertTrue(r is Resolution.NotFound, "got $r")
    }
}
