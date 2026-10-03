package com.proj.automation.plugin

/**
 * How the base app classifies a plugin, whatever the plugin says about itself (ADR-009 §9).
 * A plugin can make itself *more* sensitive by declaring `category: financial`, never less.
 */
data class Classification(
    /** Why the plugin is treated as financial; empty when it is not */
    val financialReasons: List<String>,
    /** Why the plugin needs a trusted signer even if it is not financial (secrets, device PIN) */
    val secretReasons: List<String>
) {
    val financial: Boolean get() = financialReasons.isNotEmpty()
    val needsTrustedSigner: Boolean get() = financial || secretReasons.isNotEmpty()
    val reasons: List<String> get() = financialReasons + secretReasons
}

object PluginClassifier {

    /**
     * Apps treated as financial out of the box. Only package names confirmed on a real phone
     * are listed; the owner adds the rest in admin mode.
     */
    val KNOWN_FINANCIAL_APPS: Set<String> = setOf(
        "com.nu.production" // Nubank, confirmed on the owner's phone (Spike 4)
    )

    /**
     * Rule 1: financial if the plugin says so, or if any app it may operate or read is in
     * [financialApps]. Rule 2: secrets or the device PIN require a trusted signer.
     */
    fun classify(plugin: Plugin, financialApps: Set<String>): Classification {
        val m = plugin.manifest
        val financial = buildList {
            if (m.category == Category.FINANCIAL) add("declares itself financial")
            (m.capabilities.uiAutomation + m.capabilities.readScreen).filter { it in financialApps }.sorted()
                .forEach { add("operates $it, a financial app") }
        }
        val secrets = buildList {
            if (m.secrets.isNotEmpty()) add("stores secrets (${m.secrets.joinToString { it.name }})")
            if (m.capabilities.deviceCredentialPrompt) add("types the device PIN")
        }
        return Classification(financial, secrets)
    }

    /**
     * The plugin as it must run: a plugin classified financial gets the financial risk floor on
     * every command. Only a plugin that *declared* itself financial and read-only gets level 4;
     * one caught by the app list gets 5, because its own claims are not trusted.
     */
    fun effective(plugin: Plugin, c: Classification): Plugin {
        if (!c.financial) return plugin
        val m = plugin.manifest
        val floor = if (m.category == Category.FINANCIAL && m.readOnly) 4 else 5
        return plugin.copy(commands = plugin.commands.mapValues { (_, cmd) -> cmd.copy(risk = maxOf(cmd.risk, floor)) })
    }
}
