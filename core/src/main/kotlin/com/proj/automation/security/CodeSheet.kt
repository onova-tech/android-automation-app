package com.proj.automation.security

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Printed one-time-code sheet (docs/vision/sms-security.md section 4). Code `i` is derived from
 * a secret key that never leaves the agent phone: `truncate(HMAC-SHA256(key, sheetId ‖ i))`,
 * 8 digits. The phone stores only the key and the used indices, never the codes.
 *
 * On Android the key lives in the Keystore and [mac] is backed by it; tests pass a plain key.
 */
class CodeSheet(private val mac: (ByteArray) -> ByteArray, val sheetId: Int, val size: Int = DEFAULT_SIZE) {

    constructor(key: ByteArray, sheetId: Int, size: Int = DEFAULT_SIZE) : this(hmacSha256(key), sheetId, size)

    init {
        require(size in 1..MAX_SIZE) { "sheet size must be 1..$MAX_SIZE" }
    }

    /** Code for 1-based [index] */
    fun code(index: Int): String {
        require(index in 1..size) { "index out of range" }
        val digest = mac(ByteBuffer.allocate(8).putInt(sheetId).putInt(index).array())
        // RFC 4226-style dynamic truncation
        val offset = digest.last().toInt() and 0x0f
        val bin = ((digest[offset].toInt() and 0x7f) shl 24) or
            ((digest[offset + 1].toInt() and 0xff) shl 16) or
            ((digest[offset + 2].toInt() and 0xff) shl 8) or
            (digest[offset + 3].toInt() and 0xff)
        return (bin % 100_000_000).toString().padStart(CODE_DIGITS, '0')
    }

    /** All codes, for showing the sheet once in admin mode */
    fun printable(): List<String> = (1..size).map { "%3d  %s".format(it, code(it)) }

    companion object {
        const val DEFAULT_SIZE = 100
        const val MAX_SIZE = 999
        const val CODE_DIGITS = 8

        fun hmacSha256(key: ByteArray): (ByteArray) -> ByteArray {
            require(key.size >= 16) { "key too short" }
            val spec = SecretKeySpec(key, "HmacSHA256")
            return { data -> Mac.getInstance("HmacSHA256").apply { init(spec) }.doFinal(data) }
        }
    }
}

/** Persistent authentication state; the caller stores it after every verification. */
data class AuthState(
    val sheetId: Int,
    val used: Set<Int> = emptySet(),
    val consecutiveFailures: Int = 0,
    val totalFailures: Int = 0,
    val lockouts: Int = 0,
    val lockedUntil: Long = 0,
    /** Set when the failure budget is spent; only admin mode on the device clears it */
    val hardLocked: Boolean = false
)

sealed class AuthResult {
    /** [index] is now burned; it doubles as the command's idempotency id */
    data class Accepted(val index: Int, val remaining: Int) : AuthResult()
    data class Rejected(val reason: String) : AuthResult()
    data class Locked(val until: Long?, val reason: String) : AuthResult()
}

/**
 * Verifies `#<index>-<code>` against the sheet. A valid index is burned before the command runs,
 * so it can never run twice. Failures lead to escalating lockouts and, after a total budget,
 * to a hard lock (docs/vision/sms-security.md section 4.1).
 *
 * The caller must count failures only for allow-listed senders, so strangers cannot lock the agent.
 */
class CodeVerifier(
    private val sheet: CodeSheet,
    private val maxConsecutive: Int = 5,
    private val baseLockMs: Long = 15 * 60 * 1000L,
    private val failureBudget: Int = 20
) {

    fun verify(state: AuthState, index: Int, code: String, now: Long): Pair<AuthResult, AuthState> {
        if (state.sheetId != sheet.sheetId) return AuthResult.Rejected("Code sheet replaced") to state
        if (state.hardLocked) return AuthResult.Locked(null, "Too many failures; unlock on the phone") to state
        if (now < state.lockedUntil) return AuthResult.Locked(state.lockedUntil, "Temporarily locked") to state

        val valid = index in 1..sheet.size &&
            MessageDigest.isEqual(sheet.code(index).toByteArray(), code.toByteArray())
        if (!valid) return AuthResult.Rejected("Invalid code") to failed(state, now)
        // A burned code is a duplicate delivery or a replay: harmless, so it is dropped without
        // counting as a failure (a carrier re-delivering an SMS must not lock the owner out)
        if (index in state.used) return AuthResult.Rejected("Code already used") to state

        val next = state.copy(used = state.used + index, consecutiveFailures = 0)
        return AuthResult.Accepted(index, sheet.size - next.used.size) to next
    }

    private fun failed(state: AuthState, now: Long): AuthState {
        val consecutive = state.consecutiveFailures + 1
        val total = state.totalFailures + 1
        if (total >= failureBudget) return state.copy(consecutiveFailures = consecutive, totalFailures = total, hardLocked = true)
        if (consecutive < maxConsecutive) return state.copy(consecutiveFailures = consecutive, totalFailures = total)
        val lockMs = baseLockMs shl minOf(state.lockouts, 10) // 15 min, 30 min, 1 h, ...
        return state.copy(consecutiveFailures = 0, totalFailures = total, lockouts = state.lockouts + 1, lockedUntil = now + lockMs)
    }
}
