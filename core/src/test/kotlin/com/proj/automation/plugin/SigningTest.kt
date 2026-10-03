package com.proj.automation.plugin

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class SigningTest {

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private val dev = keyPair()
    private val other = keyPair()
    private val pluginDir = File("../plugins/whatsapp")
    private val libs = File("../plugins/libraries")

    private fun fingerprint(k: KeyPair) = PackageSignature.fingerprint(k.public.encoded)

    @Test
    fun `signing does not change the package hash and verifies`() {
        val unsigned = PackageBuilder.build(pluginDir, libs)
        val signed = PackageBuilder.build(pluginDir, libs, dev)
        assertEquals(unsigned.plugin.packageHash, signed.plugin.packageHash)
        assertEquals(SignatureStatus.Unsigned, unsigned.plugin.signature)
        val valid = signed.plugin.signature as SignatureStatus.Valid
        assertEquals(fingerprint(dev), valid.fingerprint)
        assertTrue(signed.plugin.installSummary().contains("Signed by key: ${fingerprint(dev)}"))
        assertTrue(unsigned.plugin.installSummary().contains("Signature: NONE"))
    }

    @Test
    fun `an already built package can be signed and re-signed`() {
        val bytes = PackageBuilder.build(pluginDir, libs).bytes
        val signed = PackageSignature.signPackage(bytes, dev.private, dev.public)
        assertEquals(fingerprint(dev), (PluginLoader.load(signed).signature as SignatureStatus.Valid).fingerprint)
        val resigned = PackageSignature.signPackage(signed, other.private, other.public)
        assertEquals(fingerprint(other), (PluginLoader.load(resigned).signature as SignatureStatus.Valid).fingerprint)
    }

    @Test
    fun `fingerprints are stable, readable and differ per key`() {
        val f = fingerprint(dev)
        assertEquals(f, fingerprint(dev))
        assertNotEquals(f, fingerprint(other))
        assertTrue(f.matches(Regex("([0-9A-F]{4} ){15}[0-9A-F]{4}")), f)
    }

    @Test
    fun `a signature for another package is rejected`() {
        val hash = PackageBuilder.build(pluginDir, libs).plugin.packageHash
        val sigForOther = PackageSignature.sign("0".repeat(64), dev.private, dev.public)
        val e = assertThrows<PluginPackageException> { PackageSignature.verify(sigForOther, hash) }
        assertTrue(e.message!!.contains("does not match"))
    }

    @Test
    fun `a swapped public key is rejected`() {
        val hash = PackageBuilder.build(pluginDir, libs).plugin.packageHash
        val sig = PackageSignature.sign(hash, dev.private, dev.public)
        val swapped = sig.lines().joinToString("\n") {
            if (it.startsWith("public-key ")) "public-key " + java.util.Base64.getEncoder().encodeToString(other.public.encoded) else it
        }
        assertThrows<PluginPackageException> { PackageSignature.verify(swapped, hash) }
    }

    @Test
    fun `malformed signature files are rejected, never treated as unsigned`() {
        val hash = "a".repeat(64)
        for (bad in listOf("", "agp-sig 2", "agp-sig 1\nalgorithm RSA", "agp-sig 1\nalgorithm ECDSA-P256-SHA256\npublic-key !!!\nsignature x")) {
            assertThrows<PluginPackageException>(bad) { PackageSignature.verify(bad, hash) }
        }
        assertEquals(SignatureStatus.Unsigned, PackageSignature.verify(null, hash))
    }

    @Test
    fun `changing a file after signing breaks the package`() {
        val signed = PackageBuilder.build(pluginDir, libs, dev).bytes
        val files = PackageReader.read(signed).entries.toMutableMap()
        files["skills/send.yaml"] = files.getValue("skills/send.yaml") + "\n# tampered".toByteArray()
        // the lock no longer matches (and re-locking would change the hash the signature covers)
        assertThrows<PluginPackageException> { PluginLoader.load(PackageZip.write(files)) }
        files[PackageLock.FILE] = PackageLock.forFiles(files, mapOf("android-common" to "0.1.0")).render().toByteArray()
        val e = assertThrows<PluginPackageException> { PluginLoader.load(PackageZip.write(files)) }
        assertTrue(e.message!!.contains("signature does not match"), e.message)
    }

    // ——— Install policy ———

    private val messaging = PackageBuilder.build(pluginDir, libs)

    private fun financial(key: KeyPair?): Plugin {
        val files = mapOf(
            "plugin.yaml" to """
                schema: 1
                plugin: { id: bank, name: Bank, version: 0.0.1, category: financial, scope: read_only }
                app: { package: com.example.bank }
                capabilities: { ui_automation: [com.example.bank] }
            """.trimIndent(),
            "commands.yaml" to "commands:\n  - { verb: BALANCE, skill: balance }",
            "skills/balance.yaml" to "skill: balance\nsteps:\n  - return: x"
        ).mapValues { it.value.toByteArray() }.toMutableMap()
        files[PackageLock.FILE] = PackageLock.forFiles(files, emptyMap()).render().toByteArray()
        val bytes = PackageZip.write(files)
        return PluginLoader.load(key?.let { PackageSignature.signPackage(bytes, it.private, it.public) } ?: bytes)
    }

    @Test
    fun `unsigned and unknown signers install with warnings, trusted ones without`() {
        val unsigned = InstallPolicy.evaluate(messaging.plugin, emptyMap(), null) as InstallDecision.Allowed
        assertTrue(unsigned.warnings.single().contains("could not be verified"))

        val signed = PackageBuilder.build(pluginDir, libs, dev).plugin
        val unknown = InstallPolicy.evaluate(signed, emptyMap(), null) as InstallDecision.Allowed
        assertTrue(unknown.warnings.single().contains(fingerprint(dev)))

        val trusted = InstallPolicy.evaluate(signed, mapOf(fingerprint(dev) to "Ana"), null) as InstallDecision.Allowed
        assertTrue(trusted.warnings.isEmpty())
        assertEquals("Ana", trusted.verifiedAs)
    }

    @Test
    fun `financial plugins need a trusted signer`() {
        assertTrue(InstallPolicy.evaluate(financial(null), emptyMap(), null) is InstallDecision.Blocked)
        assertTrue(InstallPolicy.evaluate(financial(dev), emptyMap(), null) is InstallDecision.Blocked)
        assertTrue(InstallPolicy.evaluate(financial(dev), mapOf(fingerprint(dev) to "Ana"), null) is InstallDecision.Allowed)
    }

    @Test
    fun `updates keep the same signer`() {
        val byDev = PackageBuilder.build(pluginDir, libs, dev).plugin
        val byOther = PackageBuilder.build(pluginDir, libs, other).plugin
        val wasDev = InstalledSigner(fingerprint(dev))
        assertTrue(InstallPolicy.evaluate(byDev, emptyMap(), wasDev) is InstallDecision.Allowed)
        assertTrue(InstallPolicy.evaluate(byOther, emptyMap(), wasDev) is InstallDecision.Blocked)
        assertTrue(InstallPolicy.evaluate(messaging.plugin, emptyMap(), wasDev) is InstallDecision.Blocked)
        // an unsigned installed plugin may move to a signed one
        assertTrue(InstallPolicy.evaluate(byDev, emptyMap(), InstalledSigner(null)) is InstallDecision.Allowed)
    }
}
