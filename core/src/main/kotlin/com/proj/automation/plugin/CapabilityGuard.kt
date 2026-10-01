package com.proj.automation.plugin

import com.proj.automation.parser.ActionType
import com.proj.automation.parser.Step

/** Checked by the interpreter before every action. Returns null to allow, or the reason to deny. */
fun interface ActionGuard {
    fun check(step: Step, foregroundPackage: String?): String?
}

/**
 * Enforces a plugin's approved capabilities at run time (docs/vision/plugins.md section 8):
 * an action may only touch apps the owner approved, whatever the plugin's steps say.
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
        in UI_ACTIONS -> when {
            foregroundPackage == null -> "no app on screen"
            foregroundPackage in capabilities.uiAutomation -> null
            else -> "'${step.action.yamlValue}' on '$foregroundPackage', which this plugin may not operate"
        }
        in READ_ACTIONS -> when {
            foregroundPackage == null -> "no app on screen"
            foregroundPackage in capabilities.uiAutomation || foregroundPackage in capabilities.readScreen -> null
            else -> "'${step.action.yamlValue}' on '$foregroundPackage', which this plugin may not read"
        }
        // back, home, wait and log act on no app's content
        else -> null
    }

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

        val UI_ACTIONS = setOf(ActionType.CLICK, ActionType.TYPE, ActionType.SCROLL, ActionType.SCROLL_UNTIL)
        val READ_ACTIONS = setOf(ActionType.READ_TEXT, ActionType.READ_LIST, ActionType.WAIT_FOR)
    }
}
