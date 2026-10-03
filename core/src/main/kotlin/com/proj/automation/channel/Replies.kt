package com.proj.automation.channel

import com.proj.automation.dsl.RunResult
import com.proj.automation.dsl.RunStatus
import com.proj.automation.engine.ErrorCode
import java.text.Normalizer

/**
 * Turns results into short replies and splits them for small channels. SMS replies are plain
 * text without accents (GSM-7 fits 160 characters; one accent drops a message to 70) and are
 * paged with `MORE` (specs/003-command-channel, FR-008).
 */
object Replies {

    /** User-facing text per error code (specs/001-execution-engine/contracts/workflow-language.md) */
    fun forError(code: ErrorCode?): String = when (code) {
        ErrorCode.E_NOT_FOUND -> "Could not find it on screen"
        ErrorCode.E_TIMEOUT -> "Took too long"
        ErrorCode.E_VERIFY_FAILED -> "Done but NOT confirmed - check before repeating"
        ErrorCode.E_LOW_CONFIDENCE -> "Screen changed; did not act"
        ErrorCode.E_CAPABILITY -> "Plugin not permitted to do that"
        ErrorCode.E_EXPR -> "Plugin error"
        ErrorCode.E_BUDGET -> "Took too long"
        ErrorCode.E_DEVICE -> "Phone unavailable for automation"
        ErrorCode.E_CANCELLED -> "Stopped"
        ErrorCode.E_ACTION_FAILED, null -> "Failed"
    }

    fun forResult(result: RunResult): String = when (result.status) {
        RunStatus.SUCCEEDED -> result.returnValue?.takeIf { it.isNotBlank() } ?: "OK"
        RunStatus.CANCELLED -> "Stopped"
        RunStatus.FAILED -> "ERROR: " + forError(result.errorCode)
    }

    /** Plain text a basic phone can show in one GSM-7 segment per 160 characters */
    fun plain(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .map { c -> if (c == '\n' || c.code in 0x20..0x7e) c else '?' }
            .joinToString("")
            .replace(Regex("[ \\t]+"), " ")
            .trim()

    /**
     * Splits [text] into pages of at most [maxChars], breaking at spaces or line ends when
     * possible. Every page but the last ends with " (i/n) MORE".
     */
    fun paginate(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val suffixRoom = 14 // " (99/99) MORE"
        val room = maxChars - suffixRoom
        require(room > 10) { "maxChars too small" }
        val pages = mutableListOf<String>()
        var rest = text
        while (rest.length > room) {
            var cut = rest.lastIndexOfAny(charArrayOf(' ', '\n'), room)
            if (cut < room / 2) cut = room
            pages += rest.substring(0, cut).trimEnd()
            rest = rest.substring(cut).trimStart()
        }
        if (rest.isNotEmpty()) pages += rest
        val n = pages.size
        return pages.mapIndexed { i, p -> if (i < n - 1) "$p (${i + 1}/$n) MORE" else if (n > 1) "$p (${n}/$n)" else p }
    }
}
