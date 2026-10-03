package com.proj.automation.plugin

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class ClassificationTest {

    private val dev: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val devFp = PackageSignature.fingerprint(dev.public.encoded)
    private val trusted = mapOf(devFp to "Ana")

    /** A plugin that operates [pkg] and declares [category]; optional secret, PIN or interrupt rule */
    private fun plugin(
        pkg: String,
        category: String = "utility",
        secret: Boolean = false,
        pin: Boolean = false,
        interrupts: Boolean = false,
        readOnly: Boolean = false,
        signed: Boolean = true
    ): Plugin {
        val scope = if (readOnly) ", scope: read_only" else ""
        val caps = buildList {
            add("ui_automation: [$pkg]")
            if (secret) add("secrets: [password]")
            if (pin) add("device_credential_prompt: true")
        }.joinToString(", ")
        val files = mutableMapOf(
            "plugin.yaml" to """
                schema: 1
                plugin: { id: demo, name: Demo, version: 1.0.0, category: $category$scope }
                app: { package: $pkg }
                capabilities: { $caps }
            """.trimIndent() + (if (secret) "\nsecrets:\n  - { name: password, prompt: Password }" else ""),
            "commands.yaml" to "commands:\n  - { verb: GO, skill: go }",
            "skills/go.yaml" to "skill: go\nsteps:\n  - return: ok"
        )
        if (interrupts) files["interrupts.yaml"] = "rules:\n  - { name: r, when: { exists: { text: X } }, do: [ { back: {} } ] }"
        val bytes = files.mapValues { it.value.toByteArray() }.toMutableMap()
        bytes[PackageLock.FILE] = PackageLock.forFiles(bytes, emptyMap()).render().toByteArray()
        val zip = PackageZip.write(bytes)
        return PluginLoader.load(if (signed) PackageSignature.signPackage(zip, dev.private, dev.public) else zip)
    }

    private val bank = "com.nu.production"

    @Test
    fun `rule 1 - a plugin operating a listed app is financial even if it says utility`() {
        val liar = plugin(bank)
        val c = PluginClassifier.classify(liar, PluginClassifier.KNOWN_FINANCIAL_APPS)
        assertTrue(c.financial)
        assertTrue(c.financialReasons.single().contains(bank))
        assertTrue(InstallPolicy.evaluate(liar, emptyMap(), null) is InstallDecision.Blocked)
        assertTrue(InstallPolicy.evaluate(liar, trusted, null) is InstallDecision.Allowed)
    }

    @Test
    fun `rule 1 - the owner's list decides, not the plugin`() {
        val p = plugin("com.example.smallbank")
        assertFalse(PluginClassifier.classify(p, PluginClassifier.KNOWN_FINANCIAL_APPS).financial)
        assertTrue(PluginClassifier.classify(p, setOf("com.example.smallbank")).financial)
        assertTrue(InstallPolicy.evaluate(p, emptyMap(), null, setOf("com.example.smallbank")) is InstallDecision.Blocked)
    }

    @Test
    fun `a financial classification raises every command's risk`() {
        val liar = plugin(bank)
        assertEquals(1, liar.commands.getValue("GO").risk) // what it declared
        val effective = PluginClassifier.effective(liar, PluginClassifier.classify(liar, PluginClassifier.KNOWN_FINANCIAL_APPS))
        assertEquals(5, effective.commands.getValue("GO").risk) // caught by the list: its read-only claims are not trusted

        val honest = plugin(bank, category = "financial", readOnly = true)
        val honestEffective = PluginClassifier.effective(honest, PluginClassifier.classify(honest, PluginClassifier.KNOWN_FINANCIAL_APPS))
        assertEquals(4, honestEffective.commands.getValue("GO").risk)
    }

    @Test
    fun `financial plugins cannot have interrupt rules, however they are classified`() {
        val p = plugin(bank, interrupts = true)
        val d = InstallPolicy.evaluate(p, trusted, null) as InstallDecision.Blocked
        assertTrue(d.reason.contains("interrupt rules"))
    }

    @Test
    fun `rule 2 - secrets or the device PIN need a trusted signer, without becoming financial`() {
        for (p in listOf(plugin("com.example.chat", secret = true), plugin("com.example.chat", pin = true))) {
            val c = PluginClassifier.classify(p, PluginClassifier.KNOWN_FINANCIAL_APPS)
            assertFalse(c.financial)
            assertTrue(c.needsTrustedSigner)
            assertTrue(InstallPolicy.evaluate(p, emptyMap(), null) is InstallDecision.Blocked)
            val ok = InstallPolicy.evaluate(p, trusted, null) as InstallDecision.Allowed
            assertFalse(ok.classification.financial)
        }
        // a non-financial plugin with secrets may still have interrupt rules
        assertTrue(InstallPolicy.evaluate(plugin("com.example.chat", secret = true, interrupts = true), trusted, null) is InstallDecision.Allowed)
    }

    @Test
    fun `ordinary plugins are unaffected`() {
        val p = plugin("com.example.chat", signed = false)
        val c = PluginClassifier.classify(p, PluginClassifier.KNOWN_FINANCIAL_APPS)
        assertFalse(c.needsTrustedSigner)
        assertTrue(InstallPolicy.evaluate(p, emptyMap(), null) is InstallDecision.Allowed)
        assertSame(p, PluginClassifier.effective(p, c))
    }
}
