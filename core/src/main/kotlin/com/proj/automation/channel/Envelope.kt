package com.proj.automation.channel

/**
 * How much a channel can be trusted (docs/vision/channels.md section 4). Set by the adapter,
 * never by the message.
 */
enum class TrustProfile(val maxRisk: Int) {
    /** On-device admin UI behind the device lock */
    PHYSICAL(5),
    /** Encrypted, authenticated remote channel (e.g. encrypted SMS, profile B) */
    STRONG_REMOTE(5),
    /** Plain remote channel with per-command codes (e.g. plain SMS, profile A) */
    WEAK_REMOTE(5),
    /** Anything without valid authentication */
    UNAUTHENTICATED(0)
}

/** What a channel can carry; the core adapts replies to it instead of assuming SMS. */
data class ChannelCapabilities(
    /** Characters per reply message (160 for GSM-7 SMS) */
    val maxReplyChars: Int,
    /** False when a third party (e.g. the carrier) can read the content */
    val confidential: Boolean,
    /** True when the sender identity cannot be spoofed */
    val senderAuthenticated: Boolean,
    /** Messages per reply before the rest is held for `MORE` */
    val maxReplyParts: Int = 1,
    /** Replies must be plain ASCII (no accents), e.g. to fit GSM-7 SMS */
    val plainText: Boolean = false
) {
    companion object {
        val PLAIN_SMS = ChannelCapabilities(maxReplyChars = 160, confidential = false, senderAuthenticated = false, plainText = true)
        val LOCAL_UI = ChannelCapabilities(maxReplyChars = 10_000, confidential = true, senderAuthenticated = true)
    }
}

/** A channel-neutral incoming command. */
data class Envelope(
    /** Adapter id, e.g. "sms" */
    val channel: String,
    /** Channel-specific sender identity, e.g. a phone number */
    val sender: String,
    val body: String,
    val receivedAt: Long
)
