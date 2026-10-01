package com.proj.automation.channel

import com.proj.automation.dsl.RunResult
import com.proj.automation.dsl.RunStatus
import com.proj.automation.engine.ErrorCode
import com.proj.automation.plugin.PackageBuilder
import com.proj.automation.plugin.PackageLock
import com.proj.automation.plugin.PluginLoader
import com.proj.automation.security.AuditLog
import com.proj.automation.security.AuthResult
import com.proj.automation.security.AuthState
import com.proj.automation.security.CodeSheet
import com.proj.automation.security.CodeVerifier
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ChannelTest {

    private val key = ByteArray(32) { it.toByte() }
    private val sheet = CodeSheet(key, sheetId = 7)

    // ——— Code sheet ———

    @Test
    fun `codes are 8 digits, stable, and differ by index, sheet and key`() {
        val c1 = sheet.code(1)
        assertTrue(c1.matches(Regex("\\d{8}")))
        assertEquals(c1, CodeSheet(key, 7).code(1))
        assertNotEquals(c1, sheet.code(2))
        assertNotEquals(c1, CodeSheet(key, 8).code(1))
        assertNotEquals(c1, CodeSheet(ByteArray(32) { 9 }, 7).code(1))
        assertEquals(100, sheet.printable().size)
        assertEquals(100, (1..100).map { sheet.code(it) }.toSet().size)
    }

    @Test
    fun `a code works once and reuse is dropped without counting as a failure`() {
        val v = CodeVerifier(sheet)
        val (r1, s1) = v.verify(AuthState(7), 3, sheet.code(3), now = 0)
        assertEquals(AuthResult.Accepted(3, 99), r1)
        val (r2, s2) = v.verify(s1, 3, sheet.code(3), now = 1)
        assertTrue(r2 is AuthResult.Rejected)
        assertEquals(0, s2.totalFailures)
    }

    @Test
    fun `failures lock out, escalate, and end in a hard lock`() {
        val v = CodeVerifier(sheet, maxConsecutive = 3, baseLockMs = 1000, failureBudget = 7)
        var s = AuthState(7)
        repeat(3) { s = v.verify(s, 1, "00000000", now = 0).second }
        assertEquals(1000, s.lockedUntil)
        // even the right code is refused while locked
        assertTrue(v.verify(s, 1, sheet.code(1), now = 500).first is AuthResult.Locked)
        // after the lock, the right code works
        assertTrue(v.verify(s, 1, sheet.code(1), now = 1001).first is AuthResult.Accepted)
        // a second lockout lasts twice as long
        repeat(3) { s = v.verify(s, 2, "11111111", now = 2000).second }
        assertEquals(2000 + 2000, s.lockedUntil)
        repeat(1) { s = v.verify(s, 2, "11111111", now = 5000).second }
        assertTrue(s.hardLocked)
        assertTrue(v.verify(s, 9, sheet.code(9), now = 99_999).first is AuthResult.Locked)
    }

    @Test
    fun `codes from another sheet are refused`() {
        assertTrue(CodeVerifier(sheet).verify(AuthState(sheetId = 1), 1, sheet.code(1), 0).first is AuthResult.Rejected)
    }

    // ——— Grammar and arguments ———

    @Test
    fun `parses plugin commands, globals and the auth suffix`() {
        val p = CommandParser.parse("wa enviar Maria: chego já ☺ #12-12345678") as ParsedCommand.Plugin
        assertEquals("WA", p.keyword)
        assertEquals("ENVIAR", p.verb)
        assertEquals("Maria: chego já ☺", p.args) // arguments kept as typed
        assertEquals(AuthCode(12, "12345678"), p.auth)

        assertEquals(ParsedCommand.Global(GlobalVerb.OK, "7391", AuthCode(2, "87654321")), CommandParser.parse("ok 7391 # 2 - 87654321"))
        assertEquals(ParsedCommand.Global(GlobalVerb.STOP, null, null), CommandParser.parse("  Stop "))
        assertTrue(CommandParser.parse("WA") is ParsedCommand.Invalid)
        assertTrue(CommandParser.parse("#1-12345678") is ParsedCommand.Invalid)
        assertEquals("STATUS", CommandParser.keyword("státus").let { if (it == "STATUS") it else "x" })
    }

    @Test
    fun `argument templates`() {
        assertEquals(mapOf("phone" to "5511999", "text" to "on my way: 10 min"),
            ArgsTemplate("<phone>: <text>").match("5511999 :  on my way: 10 min"))
        assertEquals(mapOf("phone" to "5511999", "text" to "hello there"), ArgsTemplate("<phone> <text>").match("5511999 hello there"))
        assertEquals(mapOf("n" to "5"), ArgsTemplate("[<n>]").match("5"))
        assertEquals(emptyMap<String, String>(), ArgsTemplate("[<n>]").match(""))
        assertEquals(mapOf("amount" to "50", "beneficiary" to "maria"), ArgsTemplate("<amount> <beneficiary>").match("50 maria"))
        assertNull(ArgsTemplate("<phone>: <text>").match("no colon here"))
        assertNull(ArgsTemplate("<phone>").match(""))
        assertEquals(emptyMap<String, String>(), ArgsTemplate(null).match(""))
        assertNull(ArgsTemplate(null).match("unexpected"))
    }

    @Test
    fun `phone numbers match across formats`() {
        val allowed = setOf("+55 11 99999-8888")
        assertEquals("+55 11 99999-8888", PhoneNumbers.matchAllowed("+5511999998888", allowed))
        assertEquals("+55 11 99999-8888", PhoneNumbers.matchAllowed("11999998888", allowed))
        assertNull(PhoneNumbers.matchAllowed("+5511999998887", allowed))
        assertNull(PhoneNumbers.matchAllowed("8888", allowed))
    }

    // ——— Policy and replies ———

    @Test
    fun `policy combines risk and channel trust`() {
        assertEquals(PolicyDecision.Allow, Policy.decide(0, TrustProfile.UNAUTHENTICATED, authenticated = false))
        assertTrue(Policy.decide(1, TrustProfile.WEAK_REMOTE, authenticated = false) is PolicyDecision.Deny)
        assertEquals(PolicyDecision.Allow, Policy.decide(4, TrustProfile.WEAK_REMOTE, authenticated = true))
        assertEquals(PolicyDecision.NeedsConfirmation, Policy.decide(5, TrustProfile.WEAK_REMOTE, authenticated = true))
        assertEquals(PolicyDecision.Allow, Policy.decide(5, TrustProfile.PHYSICAL, authenticated = true))
        assertTrue(Policy.decide(1, TrustProfile.UNAUTHENTICATED, authenticated = true) is PolicyDecision.Deny)
    }

    @Test
    fun `replies are plain and paged`() {
        assertEquals("Saldo R$ 1.234,56 ? ok", Replies.plain("Saldo  R$ 1.234,56 ☺ ok"))
        assertEquals("Acao concluida", Replies.plain("Ação concluída"))
        val long = (1..60).joinToString(" ") { "word$it" }
        val pages = Replies.paginate(long, 160)
        assertTrue(pages.size > 1)
        assertTrue(pages.all { it.length <= 160 }, pages.toString())
        assertTrue(pages.first().endsWith("(1/${pages.size}) MORE"))
        assertTrue(pages.last().endsWith("(${pages.size}/${pages.size})"))
        assertEquals(long, pages.joinToString(" ") { it.replace(Regex(" \\(\\d+/\\d+\\)( MORE)?$"), "") })
    }

    // ——— Audit ———

    @Test
    fun `audit chain detects tampering and masks senders`() {
        val log = AuditLog()
        log.append(1, "sms", "+5511999998888", "run", "WA SEND x")
        log.append(2, "sms", "+5511999998888", "result", "SUCCEEDED")
        assertEquals("**********8888", log.entries[0].sender)
        assertNull(AuditLog.verify(log.entries))
        val tampered = log.entries.toMutableList().apply { this[0] = this[0].copy(detail = "WA SEND y") }
        assertEquals(0, AuditLog.verify(tampered))
        assertEquals(0, AuditLog.verify(log.entries.drop(1)))
    }

    // ——— Router, end to end ———

    private val owner = "+5511999998888"
    private val sms = ChannelConfig("sms", TrustProfile.WEAK_REMOTE, ChannelCapabilities.PLAIN_SMS, setOf(owner))
    private val whatsapp = PackageBuilder.build(File("../plugins/whatsapp"), File("../plugins/libraries")).plugin
    private val audit = AuditLog()
    private val state = RouterState(AuthState(7))
    private val router = CommandRouter(mapOf("WA" to whatsapp, "BANK" to transferPlugin()), CodeVerifier(sheet), audit, SecureRandom())

    private fun send(body: String, at: Long = 1000, from: String = owner) =
        router.handle(Envelope("sms", from, body, at), sms, state)

    private fun text(o: Outcome) = (o as Outcome.Reply).messages.joinToString("|")

    @Test
    fun `unknown senders get nothing and change nothing`() {
        assertEquals(Outcome.Ignore, send("WA SEND 1: hi #1-${sheet.code(1)}", from = "+1555"))
        assertTrue(state.auth.used.isEmpty())
        assertTrue(audit.entries.isEmpty())
    }

    @Test
    fun `a valid command runs once with parsed arguments`() {
        val run = send("wa send 5511999: chego em 10 #1-${sheet.code(1)}") as Outcome.Run
        assertEquals("send", run.request.skill)
        assertEquals(mapOf("phone" to "5511999", "text" to "chego em 10"), run.request.args)
        assertEquals(1, run.request.commandId)
        // the same SMS delivered again does not run twice
        assertEquals("Not accepted", text(send("wa send 5511999: chego em 10 #1-${sheet.code(1)}")))
    }

    @Test
    fun `missing codes and bad arguments are refused without burning codes`() {
        assertEquals("Not accepted", text(send("WA SEND 5511999: hi")))
        assertTrue(text(send("WA SEND hi #2-${sheet.code(2)}")).startsWith("Usage: WA SEND <phone>: <text>"))
        assertFalse(2 in state.auth.used)
        assertTrue(text(send("WA FLY now #2-${sheet.code(2)}")).startsWith("Unknown command"))
    }

    @Test
    fun `risk 5 needs a second, separately coded confirmation`() {
        val confirm = text(send("BANK TRANSFER 50 maria #3-${sheet.code(3)}"))
        val word = Regex("OK (\\d{4})").find(confirm)!!.groupValues[1]
        assertTrue(confirm.startsWith("CONFIRM: BANK TRANSFER 50 maria"))
        assertEquals("Not accepted", text(send("OK 0000 #4-${sheet.code(4)}".replace("0000", if (word == "0000") "1111" else "0000"))))
        assertEquals("Not accepted", text(send("OK $word")))
        val run = send("OK $word #5-${sheet.code(5)}") as Outcome.Run
        assertEquals(mapOf("amount" to "50", "beneficiary" to "maria"), run.request.args)
        assertEquals("Nothing to confirm", text(send("OK $word #6-${sheet.code(6)}")))
    }

    @Test
    fun `confirmations expire`() {
        val confirm = text(send("BANK TRANSFER 50 maria #3-${sheet.code(3)}", at = 0))
        val word = Regex("OK (\\d{4})").find(confirm)!!.groupValues[1]
        assertTrue(text(send("OK $word #4-${sheet.code(4)}", at = 6 * 60 * 1000L)).startsWith("Expired"))
    }

    @Test
    fun `STOP needs no code and silences everything after it`() {
        assertTrue(text(send("stop")).startsWith("STOPPED"))
        assertEquals(Outcome.Ignore, send("WA SEND 1: hi #7-${sheet.code(7)}"))
        assertFalse(7 in state.auth.used)
    }

    @Test
    fun `results become short replies with paging and a low-code warning`() {
        val run = (send("WA SEND 1: hi #8-${sheet.code(8)}") as Outcome.Run).request
        val env = Envelope("sms", owner, "", 2000)
        assertEquals("ERROR: Could not find it on screen", text(router.completed(env, sms, state, run, RunResult(RunStatus.FAILED, ErrorCode.E_NOT_FOUND))))

        val lowRun = run.copy(codesLeft = 3)
        val long = (1..40).joinToString(" ") { "msg$it" }
        val first = text(router.completed(env, sms, state, lowRun, RunResult(RunStatus.SUCCEEDED, returnValue = long)))
        assertTrue(first.endsWith("MORE"))
        var page = text(send("MORE"))
        while (page.endsWith("MORE")) page = text(send("MORE"))
        assertTrue(page.contains("(3 codes left)"), page)
        assertEquals("No more", text(send("MORE")))
        assertEquals(first, text(send("RESEND")))
    }

    @Test
    fun `the on-device console needs no code and burns none`() {
        val local = ChannelConfig("local", TrustProfile.PHYSICAL, ChannelCapabilities.LOCAL_UI, setOf("owner"))
        val run = router.handle(Envelope("local", "owner", "WA SEND 1: hi", 1), local, state) as Outcome.Run
        assertEquals(0, run.request.commandId)
        assertTrue(state.auth.used.isEmpty())
        // risk 5 runs directly when the owner is at the phone
        assertTrue(router.handle(Envelope("local", "owner", "BANK TRANSFER 5 x", 2), local, state) is Outcome.Run)
    }

    @Test
    fun `the audit log never holds codes or reply contents`() {
        send("WA SEND 5511999: secret plans #9-${sheet.code(9)}")
        val all = audit.entries.joinToString("\n") { it.event + " " + it.detail }
        assertFalse(all.contains(sheet.code(9)), all)
        assertTrue(all.contains("accepted #9"))
        assertNull(AuditLog.verify(audit.entries))
    }

    /** A minimal financial plugin whose command needs confirmation (risk 5) */
    private fun transferPlugin() = PluginLoader.load(zipWithLock(mapOf(
        "plugin.yaml" to """
            schema: 1
            plugin: { id: bank, name: Bank, version: 0.0.1, category: financial }
            app: { package: com.example.bank }
            capabilities: { ui_automation: [com.example.bank] }
        """.trimIndent(),
        "commands.yaml" to "commands:\n  - { verb: TRANSFER, skill: transfer, args: \"<amount> <beneficiary>\" }",
        "skills/transfer.yaml" to "skill: transfer\nparams: { amount: {}, beneficiary: {} }\nsteps:\n  - return: done"
    )))

    private fun zipWithLock(files: Map<String, String>): ByteArray {
        val bytes = files.mapValues { it.value.toByteArray() }.toMutableMap()
        bytes[PackageLock.FILE] = PackageLock.forFiles(bytes, emptyMap()).render().toByteArray()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> bytes.forEach { (p, b) -> z.putNextEntry(ZipEntry(p)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }
}
