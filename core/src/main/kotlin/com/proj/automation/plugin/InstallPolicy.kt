package com.proj.automation.plugin

/** What the phone already knows about an installed plugin id */
data class InstalledSigner(
    /** Fingerprint of the key that signed the installed version, or null if it was unsigned */
    val fingerprint: String?
)

sealed class InstallDecision {
    /**
     * Installation may proceed after the owner reads [warnings] (empty when the signer is trusted).
     * [verifiedAs] is the trusted developer's name, if any.
     */
    data class Allowed(val warnings: List<String>, val verifiedAs: String?, val classification: Classification) : InstallDecision()
    data class Blocked(val reason: String) : InstallDecision()
}

/**
 * Install/update rules for signatures (specs/006-package-signing). Invalid signatures never get here: loading
 * the package already rejected them.
 */
object InstallPolicy {

    /**
     * @param trusted trusted keys, fingerprint → developer name (admin mode only)
     * @param previous the signer of the currently installed version with the same plugin id, if any
     * @param financialApps apps the base app treats as financial (rule 1)
     */
    fun evaluate(
        plugin: Plugin,
        trusted: Map<String, String>,
        previous: InstalledSigner?,
        financialApps: Set<String> = PluginClassifier.KNOWN_FINANCIAL_APPS
    ): InstallDecision {
        val classification = PluginClassifier.classify(plugin, financialApps)
        val sig = plugin.signature
        val fingerprint = (sig as? SignatureStatus.Valid)?.fingerprint
        val trustedName = fingerprint?.let { trusted[it] }

        // Signer continuity, as on Android: an installed signed plugin only accepts the same key
        if (previous?.fingerprint != null && previous.fingerprint != fingerprint) {
            return InstallDecision.Blocked(
                if (fingerprint == null) "The installed version is signed; this update is not. Updates must be signed by the same key."
                else "This update is signed by a different key than the installed version. " +
                    "To change the developer, uninstall the plugin first (its secrets will be deleted)."
            )
        }
        if (classification.financial && plugin.interruptRules.isNotEmpty()) {
            return InstallDecision.Blocked(
                "Treated as financial (${classification.financialReasons.joinToString()}), and financial plugins cannot have interrupt rules."
            )
        }
        if (classification.needsTrustedSigner && trustedName == null) {
            val why = classification.reasons.joinToString()
            return InstallDecision.Blocked(
                if (fingerprint == null) "This plugin must be signed by a trusted developer ($why); it is unsigned."
                else "This plugin must be signed by a trusted developer ($why). Trust key $fingerprint first if you know who it belongs to."
            )
        }
        val warnings = when {
            sig is SignatureStatus.Unsigned -> listOf("The identity of this package could not be verified: it is not signed.")
            trustedName == null -> listOf("Signed by an unknown developer. Key fingerprint: $fingerprint")
            else -> emptyList()
        }
        return InstallDecision.Allowed(warnings, trustedName, classification)
    }
}
