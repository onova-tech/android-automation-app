package com.proj.automation.channel

import com.proj.automation.dsl.RunResult
import com.proj.automation.plugin.Plugin
import com.proj.automation.security.AuditLog
import com.proj.automation.security.AuthResult
import com.proj.automation.security.AuthState
import com.proj.automation.security.CodeVerifier
import java.security.SecureRandom

/** A channel as configured on the phone (in admin mode). */
data class ChannelConfig(
    val id: String,
    val trust: TrustProfile,
    val capabilities: ChannelCapabilities,
    /** Senders allowed to talk to the agent; everyone else is dropped silently */
    val allowedSenders: Set<String>
)

/** A command that passed authentication and policy, ready for the engine. */
data class RunRequest(
    val plugin: Plugin,
    val skill: String,
    val args: Map<String, String>,
    /** Burned code index: the command's idempotency id */
    val commandId: Int,
    /** "WA SEND ..." without the code, for confirmations and the audit log */
    val summary: String,
    val codesLeft: Int
)

sealed class Outcome {
    /** Messages to send now (the rest of a long reply is held for MORE) */
    data class Reply(val messages: List<String>) : Outcome()
    data class Run(val request: RunRequest) : Outcome()
    /** Nothing is sent back */
    object Ignore : Outcome()
}

data class PendingConfirmation(val word: String, val expiresAt: Long, val request: RunRequest)

/** Mutable conversation state; the app persists [auth] after every call. */
class RouterState(var auth: AuthState) {
    var stopped = false
    val pending = mutableMapOf<String, PendingConfirmation>()
    val morePages = mutableMapOf<String, ArrayDeque<String>>()
    val lastReply = mutableMapOf<String, List<String>>()
}

/**
 * Channel-neutral command handling (docs/vision/sms-security.md, channels.md):
 * sender allowlist → grammar → arguments → one-time code → policy (risk × channel trust)
 * → two-step confirmation for risk 5 → run → short, paged reply. Every decision is audited
 * without codes or reply contents.
 */
class CommandRouter(
    /** Installed plugins by command keyword, e.g. "WA" → whatsapp */
    private val plugins: Map<String, Plugin>,
    private val verifier: CodeVerifier,
    private val audit: AuditLog,
    private val random: SecureRandom = SecureRandom(),
    private val confirmTtlMs: Long = 5 * 60 * 1000L,
    private val lowCodesWarning: Int = 15
) {

    fun handle(env: Envelope, channel: ChannelConfig, state: RouterState): Outcome {
        // Unknown senders: dropped before parsing, not counted as failures, not even logged (flood-proof)
        if (env.sender !in channel.allowedSenders) return Outcome.Ignore
        fun log(event: String, detail: String = "") = audit.append(env.receivedAt, env.channel, env.sender, event, detail)
        fun reply(text: String) = reply(env.sender, text, channel, state)

        val parsed = CommandParser.parse(env.body)
        if (parsed is ParsedCommand.Global && parsed.verb == GlobalVerb.STOP) {
            state.stopped = true
            state.pending.clear()
            log("stop")
            return reply("STOPPED. Re-enable on the phone.")
        }
        if (state.stopped) {
            log("ignored", "agent stopped")
            return Outcome.Ignore
        }

        return when (parsed) {
            is ParsedCommand.Invalid -> { log("invalid", parsed.reason); reply("Not understood. Send HELP") }
            is ParsedCommand.Global -> global(parsed, env, channel, state, ::log, ::reply)
            is ParsedCommand.Plugin -> pluginCommand(parsed, env, channel, state, ::log, ::reply)
        }
    }

    /** Formats a finished run for the channel; call after executing an [Outcome.Run]. */
    fun completed(env: Envelope, channel: ChannelConfig, state: RouterState, request: RunRequest, result: RunResult): Outcome.Reply {
        audit.append(
            env.receivedAt, env.channel, env.sender, "result",
            "${request.summary} -> ${result.status}${result.errorCode?.let { " $it" } ?: ""}"
        )
        val warning = if (request.codesLeft < lowCodesWarning) " (${request.codesLeft} codes left)" else ""
        return reply(env.sender, Replies.forResult(result) + warning, channel, state)
    }

    // ——— Global verbs ———

    private fun global(
        cmd: ParsedCommand.Global, env: Envelope, channel: ChannelConfig, state: RouterState,
        log: (String, String) -> Any, reply: (String) -> Outcome.Reply
    ): Outcome = when (cmd.verb) {
        GlobalVerb.HELP -> {
            log("help", "")
            val lines = plugins.entries.sortedBy { it.key }.flatMap { (kw, p) ->
                p.commands.values.sortedBy { it.verb }.map { c -> "$kw ${c.verb}${c.args?.let { " $it" } ?: ""}" }
            }
            reply((lines + "STATUS, MORE, RESEND, CANCEL, STOP. Add #n-code").joinToString("\n"))
        }
        GlobalVerb.MORE -> {
            val next = state.morePages[env.sender]?.removeFirstOrNull()
            Outcome.Reply(listOf(next ?: navigation("No more", channel)))
        }
        GlobalVerb.RESEND -> {
            val last = state.lastReply[env.sender]
            if (last == null) Outcome.Reply(listOf(navigation("Nothing to resend", channel))) else {
                state.morePages[env.sender] = ArrayDeque(last.drop(channel.capabilities.maxReplyParts))
                Outcome.Reply(last.take(channel.capabilities.maxReplyParts))
            }
        }
        GlobalVerb.CANCEL -> {
            val had = state.pending.remove(env.sender) != null
            log("cancel", if (had) "pending confirmation dropped" else "nothing pending")
            reply(if (had) "Cancelled" else "Nothing to cancel")
        }
        GlobalVerb.STATUS -> when (val a = authenticate(cmd.auth, env, state, log)) {
            is AuthResult.Accepted -> reply("OK. ${a.remaining} codes left. Plugins: ${plugins.keys.sorted().joinToString()}")
            else -> reply("Not accepted")
        }
        GlobalVerb.OK -> {
            val pending = state.pending[env.sender]
            when {
                pending == null -> reply("Nothing to confirm")
                env.receivedAt > pending.expiresAt -> { state.pending.remove(env.sender); log("confirm", "expired"); reply("Expired. Send the command again") }
                cmd.argument?.trim() != pending.word -> { log("confirm", "wrong word"); reply("Not accepted") }
                else -> when (val a = authenticate(cmd.auth, env, state, log)) {
                    is AuthResult.Accepted -> {
                        state.pending.remove(env.sender)
                        log("confirmed", pending.request.summary)
                        Outcome.Run(pending.request.copy(codesLeft = a.remaining))
                    }
                    else -> reply("Not accepted")
                }
            }
        }
        GlobalVerb.STOP -> error("handled before")
    }

    // ——— Plugin commands ———

    private fun pluginCommand(
        cmd: ParsedCommand.Plugin, env: Envelope, channel: ChannelConfig, state: RouterState,
        log: (String, String) -> Any, reply: (String) -> Outcome.Reply
    ): Outcome {
        val plugin = plugins[cmd.keyword]
        val command = plugin?.commands?.get(cmd.verb)
        if (plugin == null || command == null) {
            log("invalid", "unknown command ${cmd.keyword} ${cmd.verb}")
            return reply("Unknown command. Send HELP")
        }
        val summary = "${cmd.keyword} ${cmd.verb}${if (cmd.args.isNotEmpty()) " ${cmd.args}" else ""}"
        val template = ArgsTemplate(command.args)
        // Arguments are checked before the code, so a typo never burns a code
        val args = template.match(cmd.args) ?: run {
            log("invalid", "bad arguments for ${cmd.keyword} ${cmd.verb}")
            return reply("Usage: ${cmd.keyword} ${cmd.verb}${command.args?.let { " $it" } ?: ""} #n-code")
        }
        val auth = authenticate(cmd.auth, env, state, log)
        val accepted = auth as? AuthResult.Accepted
        val request = RunRequest(plugin, command.skill, args, accepted?.index ?: 0, summary, accepted?.remaining ?: 0)

        return when (val decision = Policy.decide(command.risk, channel.trust, authenticated = accepted != null)) {
            is PolicyDecision.Deny -> {
                log("denied", "$summary: ${decision.reason}")
                reply(if (accepted == null) "Not accepted" else "Not allowed")
            }
            PolicyDecision.NeedsConfirmation -> {
                val word = "%04d".format(random.nextInt(10_000))
                state.pending[env.sender] = PendingConfirmation(word, env.receivedAt + confirmTtlMs, request)
                log("confirm-requested", summary)
                reply("CONFIRM: $summary. Reply OK $word #n-code within ${confirmTtlMs / 60_000} min")
            }
            PolicyDecision.Allow -> {
                log("run", summary)
                Outcome.Run(request)
            }
        }
    }

    // ——— Helpers ———

    private fun authenticate(code: AuthCode?, env: Envelope, state: RouterState, log: (String, String) -> Any): AuthResult {
        if (code == null) {
            log("auth", "missing code")
            return AuthResult.Rejected("Missing code")
        }
        val (result, next) = verifier.verify(state.auth, code.index, code.code, env.receivedAt)
        state.auth = next
        log("auth", when (result) {
            is AuthResult.Accepted -> "accepted #${result.index}"
            is AuthResult.Rejected -> "rejected: ${result.reason}"
            is AuthResult.Locked -> "locked: ${result.reason}"
        })
        return result
    }

    /** Short navigation notices; unlike [reply] they do not replace the last reply kept for RESEND */
    private fun navigation(text: String, channel: ChannelConfig): String =
        if (channel.capabilities.plainText) Replies.plain(text) else text

    private fun reply(sender: String, text: String, channel: ChannelConfig, state: RouterState): Outcome.Reply {
        val caps = channel.capabilities
        val body = if (caps.plainText) Replies.plain(text) else text
        val pages = Replies.paginate(body, caps.maxReplyChars)
        state.lastReply[sender] = pages
        state.morePages[sender] = ArrayDeque(pages.drop(caps.maxReplyParts))
        return Outcome.Reply(pages.take(caps.maxReplyParts))
    }
}
