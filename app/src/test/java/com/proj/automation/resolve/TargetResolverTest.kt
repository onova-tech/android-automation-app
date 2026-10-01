package com.proj.automation.resolve

import com.proj.automation.ui.Bounds
import com.proj.automation.ui.UiNode
import com.proj.automation.ui.UiSnapshots
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TargetResolverTest {

    private val resolver = TargetResolver()

    private fun fixture(name: String): UiNode =
        UiSnapshots.fromUiAutomatorXml(javaClass.getResource("/fixtures/$name")!!.readText())

    private val chat = fixture("chat_synthetic.xml")
    private val bank = fixture("bank_home_synthetic.xml")

    private fun found(r: Resolution): Resolution.Found {
        assertTrue(r is Resolution.Found, "expected Found, got $r")
        return r as Resolution.Found
    }

    @Test
    fun `parses uiautomator xml into a snapshot`() {
        assertEquals(Bounds(0, 0, 1080, 2340), chat.bounds)
        val entry = chat.walk().first { it.className == "android.widget.EditText" }
        assertTrue(entry.editable)
        assertTrue(entry.focused)
        assertEquals("Mensagem", entry.label)
        assertEquals("Conta\nSaldo disponível\nR$ 1.234,56", bank.walk().first { it.label?.startsWith("Conta") == true }.label)
    }

    @Test
    fun `a unique exact hint wins with full confidence`() {
        val r = found(resolver.resolve(Target(hints = Hints(contentDescription = "Enviar")), chat))
        assertEquals("hint:content_description", r.stage)
        assertEquals(1.0, r.confidence)
        assertEquals("com.example.chat:id/send_v2", r.node.resourceId)
    }

    @Test
    fun `hints are tried in order and stale ones are skipped`() {
        val target = Target(hints = Hints(resourceId = "com.example.chat:id/send", text = "Mensagem"))
        val r = found(resolver.resolve(target, chat))
        assertEquals("hint:text", r.stage)
    }

    @Test
    fun `self-heals when the id changed, using intent, role and region`() {
        // The old id no longer exists; ranking finds the send button anyway
        val target = Target(
            intent = "botão que envia a mensagem",
            role = Role.BUTTON,
            hints = Hints(resourceId = "com.example.chat:id/send"),
            region = "bottom-right"
        )
        val r = found(resolver.resolve(target, chat))
        assertEquals("ranked", r.stage)
        assertEquals("com.example.chat:id/send_v2", r.node.resourceId)
    }

    @Test
    fun `role keeps a text field from being chosen as a button`() {
        val r = found(resolver.resolve(Target(intent = "campo da mensagem", role = Role.EDIT_TEXT), chat))
        assertEquals("android.widget.EditText", r.node.className)
    }

    @Test
    fun `finds the balance card in a flutter-style tree`() {
        val r = found(resolver.resolve(Target(intent = "saldo disponível da conta", role = Role.TEXT), bank))
        assertTrue(r.node.label!!.startsWith("Conta"))
    }

    @Test
    fun `refuses to guess when nothing is close enough`() {
        val r = resolver.resolve(Target(intent = "transferir via pix", role = Role.BUTTON), bank)
        assertTrue(r is Resolution.Ambiguous, "got $r")
    }

    @Test
    fun `refuses to guess between two equally good candidates`() {
        val screen = UiNode(
            bounds = Bounds(0, 0, 1000, 1000),
            children = listOf(
                UiNode(text = "OK", className = "android.widget.Button", clickable = true, bounds = Bounds(0, 0, 100, 100)),
                UiNode(text = "OK", className = "android.widget.Button", clickable = true, bounds = Bounds(0, 200, 100, 300))
            )
        )
        val r = resolver.resolve(Target(intent = "ok", role = Role.BUTTON), screen)
        assertTrue(r is Resolution.Ambiguous, "got $r")
        // the same pair matched by a hint is still ambiguous, not "first one wins"
        assertTrue(resolver.resolve(Target(intent = "ok", hints = Hints(text = "OK")), screen) is Resolution.Ambiguous)
    }

    @Test
    fun `hints without an intent fail when nothing matches`() {
        assertTrue(resolver.resolve(Target(hints = Hints(text = "Nope")), chat) is Resolution.NotFound)
        assertTrue(resolver.resolve(Target(intent = "x"), null) is Resolution.NotFound)
    }

    @Test
    fun `reranker can override the heuristic order`() {
        val reranking = TargetResolver { _, ranked -> ranked.map { (n, _) -> n to if (n.label == "Emoji") 0.99 else 0.1 } }
        val r = found(reranking.resolve(Target(intent = "botão que envia a mensagem", role = Role.BUTTON), chat))
        assertEquals("Emoji", r.node.label)
    }

    @Test
    fun `word matching tolerates simple inflections and accents`() {
        assertTrue(TargetResolver.sameWord("send", "sends"))
        assertTrue(TargetResolver.sameWord("enviar", "envia"))
        assertFalse(TargetResolver.sameWord("mensagem", "message"))
        assertEquals("saldo disponivel", TargetResolver.normalize("Saldo disponível!"))
        assertEquals(listOf("saldo"), TargetResolver.tokens("R$ 1.234 de saldo"))
    }

    @Test
    fun `target parsing validates its fields`() {
        val t = Target.parse(mapOf("intent" to "send", "role" to "button", "hints" to mapOf("text" to "Send"), "region" to "bottom-right"))
        assertEquals(Role.BUTTON, t.role)
        assertEquals("Send", t.hints.text)
        assertThrows<IllegalArgumentException> { Target.parse(mapOf("role" to "button")) }
        assertThrows<IllegalArgumentException> { Target.parse(mapOf("intent" to "x", "role" to "slider")) }
        assertThrows<IllegalArgumentException> { Target.parse(mapOf("intent" to "x", "region" to "upside")) }
        assertThrows<IllegalArgumentException> { Target.parse(mapOf("intent" to "x", "hints" to mapOf("xpath" to "//a"))) }
        assertThrows<IllegalArgumentException> { Target.parse(mapOf("intent" to "x", "min_confidence" to 2)) }
    }
}
