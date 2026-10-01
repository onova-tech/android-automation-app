package com.proj.automation.plugin

import com.proj.automation.dsl.Node
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginPackageTest {

    // ——— Helpers ———

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            files.forEach { (path, bytes) -> z.putNextEntry(ZipEntry(path)); z.write(bytes); z.closeEntry() }
        }
        return out.toByteArray()
    }

    /** A package with a correct PACKAGE.lock unless [lock] is false */
    private fun pkg(files: Map<String, String>, libraries: Map<String, String> = emptyMap(), lock: Boolean = true): ByteArray {
        val bytes = files.mapValues { it.value.toByteArray() }.toMutableMap()
        if (lock) bytes[PackageLock.FILE] = PackageLock.forFiles(bytes, libraries).render().toByteArray()
        return zip(bytes)
    }

    private val manifest = """
        schema: 1
        plugin: { id: demo, name: Demo, version: 1.0.0, category: utility }
        app: { package: com.example.app }
        capabilities: { ui_automation: [com.example.app] }
    """.trimIndent()

    private val minimal = mapOf(
        "plugin.yaml" to manifest,
        "commands.yaml" to "commands:\n  - { verb: PING, skill: ping }",
        "skills/ping.yaml" to "skill: ping\nsteps:\n  - return: pong"
    )

    private fun fails(files: Map<String, String>, contains: String, libraries: Map<String, String> = emptyMap()) {
        val e = assertThrows<PluginPackageException> { PluginLoader.load(pkg(files, libraries)) }
        assertTrue(e.message!!.contains(contains), "expected '$contains' in: ${e.message}")
    }

    // ——— The real example plugin ———

    @Test
    fun `builds and loads the bundled whatsapp plugin`() {
        val result = PackageBuilder.build(File("../plugins/whatsapp"), File("../plugins/libraries"))
        val plugin = result.plugin

        assertEquals("whatsapp", plugin.manifest.id)
        assertEquals(setOf("SEND", "READ"), plugin.commands.keys)
        assertEquals(3, plugin.commands.getValue("SEND").risk) // declared above the messaging floor
        assertEquals(2, plugin.commands.getValue("READ").risk) // messaging floor
        assertTrue("android-common.dismiss_permission" in plugin.skills.getValue("send").program.flows)
        assertTrue("android-common.permission_allow" in plugin.targets)

        val summary = plugin.installSummary()
        assertTrue(summary.contains("operate apps: com.android.permissioncontroller, com.whatsapp"), summary)
        assertTrue(summary.contains("open links: https://wa.me/*"), summary)
        assertTrue(summary.contains("Package hash: ${plugin.packageHash}"), summary)
    }

    @Test
    fun `builds are reproducible`() {
        val a = PackageBuilder.build(File("../plugins/whatsapp"), File("../plugins/libraries"))
        val b = PackageBuilder.build(File("../plugins/whatsapp"), File("../plugins/libraries"))
        assertArrayEquals(a.bytes, b.bytes)
        assertEquals(a.plugin.packageHash, b.plugin.packageHash)
    }

    @Test
    fun `named targets in skills resolve to full target definitions`() {
        val plugin = PackageBuilder.build(File("../plugins/whatsapp"), File("../plugins/libraries")).plugin
        val click = plugin.skills.getValue("send").program.body.filterIsInstance<Node.Action>()
            .first { it.step.action == ActionType.CLICK }
        assertEquals("botão que envia a mensagem", click.step.target!!.intent)
    }

    // ——— Reading the zip safely ———

    @Test
    fun `a minimal package loads`() {
        val plugin = PluginLoader.load(pkg(minimal))
        assertEquals(1, plugin.commands.getValue("PING").risk)
    }

    @Test
    fun `rejects unsafe or unexpected entries`() {
        for (bad in listOf("../evil.yaml", "/abs.yaml", "skills/../../x.yaml", ".hidden", "run.sh", "skills/x.kt", "lib/a/b/c/d.yaml")) {
            val e = assertThrows<PluginPackageException>("$bad should be rejected") {
                PackageReader.read(zip(mapOf(bad to "x".toByteArray())))
            }
            assertTrue(e.message!!.contains("not allowed") || e.message!!.contains("Invalid"), "$bad: ${e.message}")
        }
    }

    @Test
    fun `rejects reserved files with a clear message`() {
        val e = assertThrows<PluginPackageException> { PackageReader.read(zip(mapOf("interrupts.yaml" to "x".toByteArray()))) }
        assertTrue(e.message!!.contains("reserved"))
    }

    @Test
    fun `rejects case-colliding duplicates`() {
        assertThrows<PluginPackageException> {
            PackageReader.read(zip(mapOf("skills/a.yaml" to "x".toByteArray(), "skills/A.yaml" to "y".toByteArray())))
        }
    }

    @Test
    fun `size limits apply to decompressed bytes`() {
        val bomb = zip(mapOf("skills/a.yaml" to ByteArray(2_000_000)))
        assertTrue(bomb.size < 20_000, "zeros compress well: ${bomb.size}")
        val e = assertThrows<PluginPackageException> { PackageReader.read(bomb) }
        assertTrue(e.message!!.contains("larger than"))
        val many = zip((1..20).associate { "skills/s$it.yaml" to "x".toByteArray() })
        assertThrows<PluginPackageException> { PackageReader.read(many, PackageLimits(maxEntries = 10)) }
    }

    // ——— PACKAGE.lock ———

    @Test
    fun `lock must exist and match every file`() {
        assertThrows<PluginPackageException> { PluginLoader.load(pkg(minimal, lock = false)) }

        val bytes = minimal.mapValues { it.value.toByteArray() }.toMutableMap()
        bytes[PackageLock.FILE] = PackageLock.forFiles(bytes, emptyMap()).render().toByteArray()
        bytes["skills/ping.yaml"] = "skill: ping\nsteps:\n  - return: tampered".toByteArray()
        val e = assertThrows<PluginPackageException> { PluginLoader.load(zip(bytes)) }
        assertTrue(e.message!!.contains("does not match its hash"))

        val extra = bytes.toMutableMap().apply { put("README.md", "added later".toByteArray()) }
        extra["skills/ping.yaml"] = minimal.getValue("skills/ping.yaml").toByteArray()
        assertTrue(assertThrows<PluginPackageException> { PluginLoader.load(zip(extra)) }.message!!.contains("not listed"))
    }

    // ——— Manifest and content checks ———

    @Test
    fun `manifest problems are reported`() {
        fun withManifest(m: String) = minimal + ("plugin.yaml" to m)
        fails(withManifest(manifest.replace("schema: 1", "schema: 2")), "unsupported schema")
        fails(withManifest(manifest.replace("id: demo", "id: Demo!")), "plugin.id")
        fails(withManifest(manifest.replace("ui_automation: [com.example.app]", "ui_automation: [com.other]")), "app.package")
        fails(withManifest(manifest + "\nsecrets:\n  - { name: pin, prompt: PIN }"), "capabilities.secrets")
        fails(withManifest(manifest.replace("app: { package: com.example.app }", "app: { package: com.example.app, candidates: tree_only }")
            .replace("ui_automation: [com.example.app]", "ui_automation: [com.example.app], screenshot: true")), "tree_only")
        fails(withManifest(manifest + "\nevil: true"), "unknown keys")
    }

    @Test
    fun `financial plugins get the risk floor`() {
        val financial = manifest.replace("category: utility", "category: financial, scope: read_only")
        assertEquals(4, PluginLoader.load(pkg(minimal + ("plugin.yaml" to financial))).commands.getValue("PING").risk)
        val full = manifest.replace("category: utility", "category: financial")
        assertEquals(5, PluginLoader.load(pkg(minimal + ("plugin.yaml" to full))).commands.getValue("PING").risk)
    }

    @Test
    fun `content problems are reported`() {
        fails(minimal + ("commands.yaml" to "commands:\n  - { verb: STOP, skill: ping }"), "reserved")
        fails(minimal + ("commands.yaml" to "commands:\n  - { verb: PING, skill: nope }"), "unknown skill")
        fails(minimal + ("skills/ping.yaml" to "skill: ping\nsteps:\n  - click: { target: nope }"), "unknown target")
        fails(minimal + ("skills/ping.yaml" to "skill: ping\nsteps:\n  - launch_app: { package: com.bank }"), "not in capabilities.ui_automation")
        fails(minimal + ("skills/ping.yaml" to "skill: ping\nsteps:\n  - open_url: { url: \"https://x.com\" }"), "deeplinks")
        fails(minimal + ("skills/ping.yaml" to "skill: ping\nsteps:\n  - assert: { screen_is: home }"), "unknown screen")
        fails(minimal + ("targets/t.yaml" to "bad: { role: button }"), "target 'bad'")
    }

    @Test
    fun `libraries are checked and cannot reach into the plugin`() {
        val withLib = minimal + mapOf(
            "plugin.yaml" to manifest + "\nlibraries: { common: 1.0.0 }",
            "lib/common/library.yaml" to "library: common\nversion: 1.0.0",
            "lib/common/flows/f.yaml" to "flow: f\nsteps:\n  - log: { message: hi }"
        )
        assertNotNull(PluginLoader.load(pkg(withLib, mapOf("common" to "1.0.0"))))
        fails(withLib, "Libraries do not match") // lock without the library
        fails(withLib + ("lib/common/library.yaml" to "library: common\nversion: 2.0.0"), "must declare", mapOf("common" to "1.0.0"))
        // a library flow cannot call a plugin flow
        fails(
            withLib + mapOf(
                "lib/common/flows/f.yaml" to "flow: f\nsteps:\n  - call: { flow: mine }",
                "flows/mine.yaml" to "flow: mine\nsteps:\n  - log: { message: x }"
            ),
            "unknown flow", mapOf("common" to "1.0.0")
        )
    }

    // ——— Runtime capability guard ———

    @Test
    fun `deeplink patterns cannot be stretched to other hosts`() {
        val p = "https://wa.me/*"
        assertTrue(CapabilityGuard.urlMatches(p, "https://wa.me/5511999?text=oi"))
        assertTrue(CapabilityGuard.urlMatches(p, "https://wa.me/"))
        assertFalse(CapabilityGuard.urlMatches(p, "https://wa.me.evil.com/x"))
        assertFalse(CapabilityGuard.urlMatches(p, "https://evil.com/?u=https://wa.me/"))
        assertFalse(CapabilityGuard.urlMatches(p, "http://wa.me/x"))
        assertFalse(CapabilityGuard.urlMatches("*", "https://anything"))
    }

    @Test
    fun `guard limits what a plugin can see, launch and open`() {
        val guard = CapabilityGuard(Capabilities(uiAutomation = setOf("com.a"), readScreen = setOf("com.b"), deeplinks = listOf("https://a.com/*")))
        assertTrue(guard.canSee("com.a"))
        assertTrue(guard.canSee("com.b"))
        assertFalse(guard.canSee("com.c"))
        assertFalse(guard.canSee(null))
        assertNull(guard.check(Step(ActionType.LAUNCH_APP, parameters = mapOf("package" to "com.a")), null))
        assertNotNull(guard.check(Step(ActionType.LAUNCH_APP, parameters = mapOf("package" to "com.c")), null))
        assertNull(guard.check(Step(ActionType.OPEN_URL, parameters = mapOf("url" to "https://a.com/x")), null))
        assertNotNull(guard.check(Step(ActionType.OPEN_URL, parameters = mapOf("url" to "https://b.com/x")), null))
        assertNull(guard.check(Step(ActionType.CLICK), "com.c")) // allowed to try, but it sees an empty screen
    }

    @Test
    fun `a plugin is blind to apps it was not approved for`() {
        val device = com.proj.automation.replay.ScreenDevice(
            com.proj.automation.ui.UiNode(packageName = "com.other", text = "secret balance")
        )
        val guard = CapabilityGuard(Capabilities(uiAutomation = setOf("com.a")))
        val ctx = com.proj.automation.engine.ActionContext(
            device, com.proj.automation.service.EventBus(), com.proj.automation.engine.CancellationToken(), canSee = guard::canSee
        )
        assertNull(ctx.snapshot())
    }
}
