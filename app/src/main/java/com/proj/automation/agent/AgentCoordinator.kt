package com.proj.automation.agent

import android.content.Context
import com.proj.automation.accessibility.AndroidDevicePort
import com.proj.automation.channel.ChannelCapabilities
import com.proj.automation.channel.ChannelConfig
import com.proj.automation.channel.CommandRouter
import com.proj.automation.channel.Envelope
import com.proj.automation.channel.Outcome
import com.proj.automation.channel.RouterState
import com.proj.automation.channel.TrustProfile
import com.proj.automation.dsl.RunResult
import com.proj.automation.engine.ActionDispatcher
import com.proj.automation.engine.ErrorHandler
import com.proj.automation.engine.ExecutionEngine
import com.proj.automation.engine.buildHandlerRegistry
import com.proj.automation.plugin.InstallDecision
import com.proj.automation.plugin.InstallPolicy
import com.proj.automation.plugin.Plugin
import com.proj.automation.security.AuditEntry
import com.proj.automation.security.AuditLog
import com.proj.automation.security.CodeSheet
import com.proj.automation.security.CodeVerifier
import com.proj.automation.security.KeystoreKeys
import com.proj.automation.service.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Wires channels to the command router and the engine. One command runs at a time (the screen
 * is a single shared resource); state is persisted after every command.
 */
object AgentCoordinator {

    /** For channel adapters that receive on the main thread (e.g. the SMS receiver) */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    const val LOCAL_SENDER = "owner"

    private val mutex = Mutex()
    private lateinit var store: AgentStore
    private lateinit var engine: ExecutionEngine

    @Volatile var plugins: List<InstalledPlugin> = emptyList(); private set
    @Volatile var loadProblems: List<String> = emptyList(); private set
    @Volatile var settings = AgentSettings(); private set

    private lateinit var audit: AuditLog
    private lateinit var router: CommandRouter
    private lateinit var state: RouterState

    fun init(context: Context) {
        val app = context.applicationContext
        store = AgentStore(app)
        engine = ExecutionEngine(
            ActionDispatcher(buildHandlerRegistry()), ErrorHandler(), EventBus.default,
            device = { AndroidDevicePort.current(app) }
        )
        reload()
    }

    val stopped: Boolean get() = ::state.isInitialized && state.stopped

    fun smsChannel() = ChannelConfig("sms", TrustProfile.WEAK_REMOTE, ChannelCapabilities.PLAIN_SMS, settings.allowedSenders)
    fun localChannel() = ChannelConfig("local", TrustProfile.PHYSICAL, ChannelCapabilities.LOCAL_UI, setOf(LOCAL_SENDER))

    /** Handles one incoming command and returns the messages to send back. */
    suspend fun handle(env: Envelope, channel: ChannelConfig): List<String> = mutex.withLock {
        val before = audit.entries.size
        val replies = when (val outcome = router.handle(env, channel, state)) {
            Outcome.Ignore -> emptyList()
            is Outcome.Reply -> outcome.messages
            is Outcome.Run -> {
                val r = outcome.request
                val result = run(r.plugin, r.skill, r.args)
                router.completed(env, channel, state, r, result).messages
            }
        }
        persist(before)
        replies
    }

    /** Fails with E_DEVICE when the accessibility service is off (no device port) */
    private suspend fun run(plugin: Plugin, skill: String, args: Map<String, String>): RunResult =
        engine.runSkill(plugin, skill, args)

    fun cancelRunning() = engine.cancel()

    // ——— Admin operations (call only from the on-device admin UI) ———

    suspend fun installPlugin(keyword: String, bytes: ByteArray, plugin: Plugin) = mutex.withLock {
        // checked again here, so no caller can skip the policy
        val decision = InstallPolicy.evaluate(plugin, store.trustedKeys(), store.installedSigner(plugin.manifest.id))
        if (decision is InstallDecision.Blocked) throw com.proj.automation.plugin.PluginPackageException(decision.reason)
        store.installPlugin(keyword, bytes, plugin)
        reload()
    }

    /** What the install policy says about a loaded package (signature, signer continuity, financial rule) */
    fun installDecision(plugin: Plugin, alsoTrusted: Map<String, String> = emptyMap()): InstallDecision =
        InstallPolicy.evaluate(plugin, store.trustedKeys() + alsoTrusted, store.installedSigner(plugin.manifest.id))

    fun trustedKeys(): Map<String, String> = store.trustedKeys()

    suspend fun trustKey(fingerprint: String, name: String) = mutex.withLock {
        store.saveTrustedKeys(store.trustedKeys() + (fingerprint to name))
    }

    suspend fun untrustKey(fingerprint: String) = mutex.withLock {
        store.saveTrustedKeys(store.trustedKeys() - fingerprint)
    }

    suspend fun removePlugin(keyword: String) = mutex.withLock {
        store.removePlugin(keyword)
        reload()
    }

    suspend fun setAllowedSenders(senders: Set<String>) = mutex.withLock {
        store.saveSettings(settings.copy(allowedSenders = senders))
        reload()
    }

    /** Replaces the code sheet; returns the new codes, to be shown once and never stored. */
    suspend fun newCodeSheet(size: Int = CodeSheet.DEFAULT_SIZE): List<String> = mutex.withLock {
        val old = settings.sheetId
        val next = old + 1
        KeystoreKeys.newSheetKey(old, next)
        store.saveSettings(settings.copy(sheetId = next, sheetSize = size))
        store.saveAuthState(com.proj.automation.security.AuthState(next))
        reload()
        CodeSheet(KeystoreKeys.codeSheetMac(next), next, size).printable()
    }

    /** Re-enables remote commands after STOP and clears a hard lock. */
    suspend fun reenable() = mutex.withLock {
        state.stopped = false
        state.auth = state.auth.copy(hardLocked = false, consecutiveFailures = 0, lockedUntil = 0)
        store.saveAuthState(state.auth)
        settings = settings.copy(stopped = false)
        store.saveSettings(settings)
    }

    fun auditEntries(): List<AuditEntry> = if (::audit.isInitialized) audit.entries else emptyList()

    // ——— Internals ———

    private fun reload() {
        settings = store.settings()
        val (loaded, problems) = store.loadPlugins()
        plugins = loaded
        loadProblems = problems
        val sheet = CodeSheet(KeystoreKeys.codeSheetMac(settings.sheetId), settings.sheetId, settings.sheetSize)
        audit = AuditLog(store.auditEntries())
        router = CommandRouter(loaded.associate { it.keyword to it.plugin }, CodeVerifier(sheet), audit)
        state = RouterState(store.authState(settings.sheetId)).also { it.stopped = settings.stopped }
    }

    private fun persist(auditBefore: Int) {
        store.saveAuthState(state.auth)
        if (state.stopped != settings.stopped) {
            settings = settings.copy(stopped = state.stopped)
            store.saveSettings(settings)
        }
        store.appendAudit(audit.entries.drop(auditBefore))
    }
}
