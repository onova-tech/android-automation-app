package com.proj.automation.channel

sealed class PolicyDecision {
    object Allow : PolicyDecision()
    /** Run only after a second, separately authenticated confirmation */
    object NeedsConfirmation : PolicyDecision()
    data class Deny(val reason: String) : PolicyDecision()
}

/**
 * Decides from a command's risk level (0–5) and the channel's trust profile
 * (docs/vision/sms-security.md section 5, channels.md section 4). Policy is local
 * configuration: nothing in a message or a plugin can change it.
 */
object Policy {

    fun decide(risk: Int, trust: TrustProfile, authenticated: Boolean): PolicyDecision = when {
        risk !in 0..5 -> PolicyDecision.Deny("Invalid risk level")
        risk > 0 && !authenticated -> PolicyDecision.Deny("Not authenticated")
        risk > trust.maxRisk -> PolicyDecision.Deny("Not allowed on this channel")
        // irreversible-class commands need a second step whenever the owner is not at the phone
        risk >= 5 && trust != TrustProfile.PHYSICAL -> PolicyDecision.NeedsConfirmation
        else -> PolicyDecision.Allow
    }
}
