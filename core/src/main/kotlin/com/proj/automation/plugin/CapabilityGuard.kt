package com.proj.automation.plugin

import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/** Checked by the interpreter before every action. Returns null to allow, or the reason to deny. */
fun interface ActionGuard {
    fun check(step: Step, foregroundPackage: String?): String?

    /** Whether the screen of [foregroundPackage] may be seen at all; unseen screens read as empty */
    fun canSee(foregroundPackage: String?): Boolean = true
}

/**
 * Enforces a plugin's approved capabilities at run time (docs/vision/plugins.md section 8):
 *
 * - the plugin is **blind** to apps it was not approved for: actions and conditions see an empty
 *   screen there, so it can neither act on nor read them (including through `exists`/`screen_is`);
 * - launching an app or opening a link outside the approved lists ends the run (`E_CAPABILITY`).
 */
class CapabilityGuard(private val capabilities: Capabilities) : ActionGuard {

    override fun check(step: Step, foregroundPackage: String?): String? = when (step.action) {
        ActionType.LAUNCH_APP -> {
            val pkg = step.parameters["package"] as? String
            if (pkg in capabilities.uiAutomation) null else "launch_app '$pkg' is not an approved app"
        }
        ActionType.OPEN_URL -> {
            val url = step.parameters["url"] as? String ?: ""
            if (capabilities.deeplinks.any { urlMatches(it, url) }) null else "open_url to a link not in capabilities.deeplinks"
        }
        // other actions only see the approved apps' screens (see canSee)
        else -> null
    }

    override fun canSee(foregroundPackage: String?): Boolean =
        foregroundPackage != null &&
            (foregroundPackage in capabilities.uiAutomation || foregroundPackage in capabilities.readScreen)

    companion object {
        private val SCHEME_HOST = Regex("^[a-z][a-z0-9+.-]*://[^/*?#]+")

        /**
         * Deeplink patterns use `*` as a wildcard. The scheme and host must be written out
         * literally, so a pattern for wa.me can never match another host.
         */
        fun urlMatches(pattern: String, url: String): Boolean {
            val fixed = SCHEME_HOST.find(pattern)?.value ?: return false
            if (!url.startsWith(fixed) || (url.length > fixed.length && url[fixed.length] !in "/?#")) return false
            val regex = pattern.split('*').joinToString(".*") { Regex.escape(it) }
            return Regex(regex).matches(url)
        }

    }
}
