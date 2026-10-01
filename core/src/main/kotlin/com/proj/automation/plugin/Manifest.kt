package com.proj.automation.plugin

enum class Category { MESSAGING, FINANCIAL, UTILITY }

data class Capabilities(
    /** Apps the plugin may operate (click, type, scroll, launch) */
    val uiAutomation: Set<String> = emptySet(),
    /** Apps the plugin may only read */
    val readScreen: Set<String> = emptySet(),
    val deeplinks: List<String> = emptyList(),
    val notificationsRead: Set<String> = emptySet(),
    val notificationsReply: Set<String> = emptySet(),
    val contactsLookup: Boolean = false,
    val smsReply: Boolean = false,
    val screenshot: Boolean = false,
    val deviceCredentialPrompt: Boolean = false,
    val secrets: Set<String> = emptySet()
) {
    /** Human-readable list for the install summary */
    fun describe(): List<String> = buildList {
        if (uiAutomation.isNotEmpty()) add("operate apps: ${uiAutomation.sorted().joinToString()}")
        if (readScreen.isNotEmpty()) add("read screens of: ${readScreen.sorted().joinToString()}")
        if (deeplinks.isNotEmpty()) add("open links: ${deeplinks.joinToString()}")
        if (notificationsRead.isNotEmpty()) add("read notifications of: ${notificationsRead.sorted().joinToString()}")
        if (notificationsReply.isNotEmpty()) add("reply to notifications of: ${notificationsReply.sorted().joinToString()}")
        if (contactsLookup) add("look up contacts")
        if (smsReply) add("reply by SMS")
        if (screenshot) add("capture the screen (screenshots/OCR)")
        if (deviceCredentialPrompt) add("type the DEVICE PIN into the system credential prompt")
        if (secrets.isNotEmpty()) add("use stored secrets: ${secrets.sorted().joinToString()}")
    }
}

data class SecretSlot(val name: String, val prompt: String)

data class Manifest(
    val schema: Int,
    val id: String,
    val name: String,
    val version: String,
    val category: Category,
    val readOnly: Boolean,
    val appPackage: String,
    val testedVersions: List<String>,
    val treeOnly: Boolean,
    val capabilities: Capabilities,
    val secrets: List<SecretSlot>,
    /** Libraries the plugin vendors, id → exact version */
    val libraries: Map<String, String>,
    val requiresBase: String?,
    /** Language used when the device's language has no texts in `i18n/` */
    val defaultLanguage: String? = null
) {
    companion object {
        const val SCHEMA = 1
        private val ID = Regex("[a-z][a-z0-9-]{1,39}")
        val LANGUAGE = Regex("[a-z]{2}(-[a-z]{2})?")
        private val SEMVER = Regex("""\d+\.\d+\.\d+""")
        private val PACKAGE = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")

        fun parse(doc: Map<String, Any?>): Manifest {
            fun fail(msg: String): Nothing = throw PluginPackageException("plugin.yaml: $msg")
            fun map(v: Any?, where: String): Map<*, *> = v as? Map<*, *> ?: fail("'$where' must be a mapping")
            fun strings(v: Any?, where: String): List<String> =
                (v ?: emptyList<String>()).let { it as? List<*> ?: fail("'$where' must be a list") }.map { it.toString() }
            fun bool(v: Any?, where: String): Boolean = v?.let { it as? Boolean ?: fail("'$where' must be true or false") } ?: false
            fun unknown(m: Map<*, *>, allowed: Set<String>, where: String) {
                val extra = m.keys.map { it.toString() } - allowed
                if (extra.isNotEmpty()) fail("unknown keys $extra in '$where'; allowed: $allowed")
            }

            unknown(doc, setOf("schema", "plugin", "app", "capabilities", "secrets", "libraries", "requires_base"), "top level")
            val schema = (doc["schema"] as? Number)?.toInt() ?: fail("missing 'schema'")
            if (schema != SCHEMA) fail("unsupported schema $schema; this app supports $SCHEMA")

            val plugin = map(doc["plugin"], "plugin")
            unknown(plugin, setOf("id", "name", "version", "category", "scope", "default_language"), "plugin")
            val id = plugin["id"]?.toString()?.takeIf { ID.matches(it) } ?: fail("'plugin.id' must match ${ID.pattern}")
            val version = plugin["version"]?.toString()?.takeIf { SEMVER.matches(it) } ?: fail("'plugin.version' must be x.y.z")
            val category = plugin["category"]?.toString()?.let { c ->
                Category.entries.find { it.name.equals(c, ignoreCase = true) } ?: fail("unknown category '$c'")
            } ?: fail("missing 'plugin.category'")
            val defaultLanguage = plugin["default_language"]?.toString()?.lowercase()?.also {
                if (!LANGUAGE.matches(it)) fail("'plugin.default_language' must be a language code like pt or pt-br")
            }
            val scope = plugin["scope"]?.toString()
            if (scope != null && scope != "read_only") fail("'plugin.scope' can only be read_only")

            val app = map(doc["app"], "app")
            unknown(app, setOf("package", "tested_versions", "candidates"), "app")
            val pkg = app["package"]?.toString()?.takeIf { PACKAGE.matches(it) } ?: fail("'app.package' must be an Android package name")
            val candidates = app["candidates"]?.toString() ?: "any"
            if (candidates !in setOf("any", "tree_only")) fail("'app.candidates' must be any or tree_only")

            val caps = doc["capabilities"]?.let { map(it, "capabilities") } ?: emptyMap<String, Any?>()
            unknown(
                caps,
                setOf("ui_automation", "read_screen", "deeplinks", "notifications", "contacts_lookup", "sms_reply",
                    "screenshot", "device_credential_prompt", "secrets"),
                "capabilities"
            )
            val notifications = caps["notifications"]?.let { map(it, "capabilities.notifications") } ?: emptyMap<String, Any?>()
            unknown(notifications, setOf("read", "reply"), "capabilities.notifications")
            val capabilities = Capabilities(
                uiAutomation = strings(caps["ui_automation"], "capabilities.ui_automation").toSet(),
                readScreen = strings(caps["read_screen"], "capabilities.read_screen").toSet(),
                deeplinks = strings(caps["deeplinks"], "capabilities.deeplinks"),
                notificationsRead = strings(notifications["read"], "capabilities.notifications.read").toSet(),
                notificationsReply = strings(notifications["reply"], "capabilities.notifications.reply").toSet(),
                contactsLookup = bool(caps["contacts_lookup"], "capabilities.contacts_lookup"),
                smsReply = bool(caps["sms_reply"], "capabilities.sms_reply"),
                screenshot = bool(caps["screenshot"], "capabilities.screenshot"),
                deviceCredentialPrompt = bool(caps["device_credential_prompt"], "capabilities.device_credential_prompt"),
                secrets = strings(caps["secrets"], "capabilities.secrets").toSet()
            )
            if (candidates == "tree_only" && capabilities.screenshot) fail("a tree_only app cannot request 'screenshot'")
            val operable = capabilities.uiAutomation + capabilities.readScreen
            if (operable.isNotEmpty() && pkg !in operable) fail("'app.package' must be listed in ui_automation or read_screen")

            val rawSecrets = when (val v = doc["secrets"]) {
                null -> emptyList<Any?>()
                is List<*> -> v
                else -> fail("'secrets' must be a list")
            }
            val secrets = rawSecrets.mapIndexed { i, raw ->
                val slot = map(raw, "secrets[$i]")
                unknown(slot, setOf("name", "prompt", "use"), "secrets[$i]")
                val use = slot["use"]?.toString() ?: "type_secret_only"
                if (use != "type_secret_only") fail("secrets[$i].use can only be type_secret_only")
                SecretSlot(
                    name = slot["name"]?.toString()?.takeIf { ID.matches(it.replace('_', '-')) } ?: fail("secrets[$i].name is invalid"),
                    prompt = slot["prompt"]?.toString() ?: fail("secrets[$i].prompt is required")
                )
            }
            if (secrets.map { it.name }.toSet() != capabilities.secrets) {
                fail("'capabilities.secrets' must list exactly the declared secrets ${secrets.map { it.name }}")
            }

            val libraries = (doc["libraries"]?.let { map(it, "libraries") } ?: emptyMap<String, Any?>())
                .entries.associate { (k, v) ->
                    val lib = k.toString().takeIf { ID.matches(it) } ?: fail("invalid library id '$k'")
                    lib to (v?.toString()?.takeIf { SEMVER.matches(it) } ?: fail("library '$lib' needs an exact x.y.z version"))
                }

            return Manifest(
                schema = schema, id = id, name = plugin["name"]?.toString() ?: id, version = version,
                category = category, readOnly = scope == "read_only",
                appPackage = pkg, testedVersions = strings(app["tested_versions"], "app.tested_versions"),
                treeOnly = candidates == "tree_only", capabilities = capabilities, secrets = secrets,
                libraries = libraries, requiresBase = doc["requires_base"]?.toString(),
                defaultLanguage = defaultLanguage
            )
        }
    }
}
