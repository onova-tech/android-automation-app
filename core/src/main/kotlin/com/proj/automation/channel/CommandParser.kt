package com.proj.automation.channel

import java.text.Normalizer

/** A parsed command line, before authentication and policy. */
sealed class ParsedCommand {
    /** The `#<index>-<code>` suffix, if present (it is removed from everything else) */
    abstract val auth: AuthCode?

    data class Global(val verb: GlobalVerb, val argument: String?, override val auth: AuthCode?) : ParsedCommand()
    data class Plugin(val keyword: String, val verb: String, val args: String, override val auth: AuthCode?) : ParsedCommand()
    data class Invalid(val reason: String, override val auth: AuthCode?) : ParsedCommand()
}

data class AuthCode(val index: Int, val code: String)

/** Verbs the base app handles on every channel (specs/003-command-channel/contracts/command-grammar.md) */
enum class GlobalVerb {
    HELP, STOP, STATUS, MORE, RESEND, CANCEL,
    /** `OK <word>` confirms a pending two-step command */
    OK
}

/**
 * Channel-neutral command grammar, designed for a T9 keypad:
 * `<PLUGIN> <VERB> <arguments> #<index>-<code>`. Keywords are case- and accent-insensitive;
 * arguments are kept exactly as typed.
 */
object CommandParser {

    private val AUTH = Regex("""\s*#\s*(\d{1,3})\s*-\s*(\d{8})\s*$""")
    private val WORD = Regex("""^\s*(\S+)(?:\s+(\S+))?(?:\s+(.*))?$""", RegexOption.DOT_MATCHES_ALL)
    const val MAX_LENGTH = 1000

    fun parse(body: String): ParsedCommand {
        if (body.length > MAX_LENGTH) return ParsedCommand.Invalid("Too long", null)
        val authMatch = AUTH.find(body)
        val auth = authMatch?.let { AuthCode(it.groupValues[1].toInt(), it.groupValues[2]) }
        val text = (if (authMatch != null) body.substring(0, authMatch.range.first) else body).trim()
        if (text.isEmpty()) return ParsedCommand.Invalid("Empty command", auth)

        val m = WORD.find(text) ?: return ParsedCommand.Invalid("Empty command", auth)
        val first = keyword(m.groupValues[1])
        GlobalVerb.entries.find { it.name == first }?.let { verb ->
            val rest = listOf(m.groupValues[2], m.groupValues[3]).filter { it.isNotEmpty() }.joinToString(" ").trim()
            return ParsedCommand.Global(verb, rest.ifEmpty { null }, auth)
        }
        val verb = m.groupValues[2].takeIf { it.isNotEmpty() }
            ?: return ParsedCommand.Invalid("Missing verb after '$first'", auth)
        return ParsedCommand.Plugin(first, keyword(verb), m.groupValues[3].trim(), auth)
    }

    /** Upper case without accents, so "wa enviar" and "WA ENVIAR" are the same keyword */
    fun keyword(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").uppercase()
}

/**
 * A command's argument pattern from `commands.yaml`, e.g. `<phone>: <text>` or `[<n>]`.
 * `<name>` is a required argument, `[<name>]` an optional one (only at the end); any other text
 * is a literal separator, matched with flexible spaces. The last argument takes the rest of the line.
 */
class ArgsTemplate(template: String?) {

    val names: List<String>
    private val regex: Regex
    private val optional: Set<String>

    init {
        val tokens = TOKEN.findAll(template.orEmpty()).toList()
        val names = mutableListOf<String>()
        val optional = mutableSetOf<String>()
        val pattern = StringBuilder("^\\s*")
        var last = 0
        tokens.forEachIndexed { i, t ->
            val sep = separator(template!!.substring(last, t.range.first), between = i > 0)
            val isOptional = t.value.startsWith("[")
            val name = t.groupValues[1].ifEmpty { t.groupValues[2] }
            require(name !in names) { "argument '$name' appears twice" }
            require(!isOptional || i == tokens.lastIndex) { "only the last argument can be optional" }
            names += name
            if (isOptional) optional += name
            val group = if (i == tokens.lastIndex) "(.+)" else "(.+?)"
            // an optional argument takes its separator with it, so "STATEMENT" and "STATEMENT 5" both match
            pattern.append(if (isOptional) "(?:$sep$group)?" else sep + group)
            last = t.range.last + 1
        }
        pattern.append(separator(template.orEmpty().substring(last), between = false)).append("\\s*$")
        this.names = names
        this.optional = optional
        this.regex = Regex(pattern.toString(), RegexOption.DOT_MATCHES_ALL)
    }

    /** @return the arguments by name, or null when the text does not fit the pattern */
    fun match(text: String): Map<String, String>? {
        val m = regex.matchEntire(text) ?: return null
        val out = names.mapIndexedNotNull { i, name ->
            m.groups[i + 1]?.value?.trim()?.takeIf { it.isNotEmpty() }?.let { name to it }
        }.toMap()
        return if (names.all { it in out || it in optional }) out else null
    }

    /** Literal text between arguments; whitespace alone between two arguments needs at least one space */
    private fun separator(s: String, between: Boolean): String {
        val words = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return if (between && s.isNotEmpty()) "\\s+" else "\\s*"
        return "\\s*" + words.joinToString("\\s*") { Regex.escape(it) } + "\\s*"
    }

    companion object {
        private val TOKEN = Regex("""<([a-z][a-z0-9_]*)>|\[<([a-z][a-z0-9_]*)>]""")
    }
}
