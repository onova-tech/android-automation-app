package com.proj.automation.security

import java.security.MessageDigest

/** One audit record. [hash] covers every other field and the previous record's hash. */
data class AuditEntry(
    val seq: Long,
    val time: Long,
    val channel: String,
    /** Masked sender, e.g. "*******8888" */
    val sender: String,
    val event: String,
    val detail: String,
    val prevHash: String,
    val hash: String
)

/**
 * Hash-chained audit log (specs/003-command-channel, FR-009). Changing, removing or
 * reordering a record breaks the chain, which [verify] detects. Records must never contain
 * secrets or one-time codes: callers log commands with the `#index-code` suffix removed.
 *
 * Storage is the caller's: entries are plain data to persist (e.g. SQLite on the phone).
 */
class AuditLog(entries: List<AuditEntry> = emptyList()) {

    private val list = entries.toMutableList()
    val entries: List<AuditEntry> get() = list.toList()

    fun append(time: Long, channel: String, sender: String, event: String, detail: String): AuditEntry {
        val prev = list.lastOrNull()?.hash ?: GENESIS
        val seq = (list.lastOrNull()?.seq ?: 0) + 1
        val masked = maskSender(sender)
        val entry = AuditEntry(seq, time, channel, masked, event, detail.take(MAX_DETAIL), prev,
            hash(seq, time, channel, masked, event, detail.take(MAX_DETAIL), prev))
        list += entry
        return entry
    }

    companion object {
        val GENESIS = "0".repeat(64)
        const val MAX_DETAIL = 500

        /** Index of the first broken record, or null when the whole chain is intact */
        fun verify(entries: List<AuditEntry>): Int? {
            var prev = GENESIS
            entries.forEachIndexed { i, e ->
                if (e.prevHash != prev || e.seq != (i + 1).toLong() ||
                    e.hash != hash(e.seq, e.time, e.channel, e.sender, e.event, e.detail, e.prevHash)
                ) return i
                prev = e.hash
            }
            return null
        }

        fun maskSender(sender: String): String =
            if (sender.length <= 4) "*".repeat(sender.length) else "*".repeat(sender.length - 4) + sender.takeLast(4)

        private fun hash(vararg parts: Any): String {
            val md = MessageDigest.getInstance("SHA-256")
            parts.forEach { md.update(it.toString().toByteArray()); md.update(0) }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
