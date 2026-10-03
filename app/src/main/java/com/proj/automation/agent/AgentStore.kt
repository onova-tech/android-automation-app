package com.proj.automation.agent

import android.content.Context
import com.proj.automation.plugin.Plugin
import com.proj.automation.plugin.PluginLoader
import com.proj.automation.plugin.InstalledSigner
import com.proj.automation.plugin.PluginClassifier
import com.proj.automation.plugin.PluginPackageException
import com.proj.automation.plugin.SignatureStatus
import com.proj.automation.security.AuditEntry
import com.proj.automation.security.AuthState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** An approved plugin: the command keyword the owner chose and the package hash they approved. */
data class InstalledPlugin(val keyword: String, val plugin: Plugin)

/** Settings changed only in admin mode on the phone. */
data class AgentSettings(
    val allowedSenders: Set<String> = emptySet(),
    val sheetId: Int = 0,
    val sheetSize: Int = 100,
    /** Set by the STOP command; only the on-device admin UI clears it */
    val stopped: Boolean = false,
    /** Apps treated as financial whatever a plugin declares (ADR-009 §9) */
    val financialApps: Set<String> = PluginClassifier.KNOWN_FINANCIAL_APPS
)

/**
 * Local persistence in the app's private storage (encrypted by the device until first unlock).
 * Approved packages are re-verified on every load: if the bytes on disk no longer match the
 * approved hash, the plugin is dropped instead of loaded.
 */
class AgentStore(context: Context) {

    private val dir = File(context.filesDir, "agent").apply { mkdirs() }
    private val pluginsDir = File(dir, "plugins").apply { mkdirs() }
    private val pluginsIndex = File(dir, "plugins.json")
    private val settingsFile = File(dir, "settings.json")
    private val authFile = File(dir, "auth.json")
    private val auditFile = File(dir, "audit.jsonl")
    private val trustedFile = File(dir, "trusted_keys.json")

    // ——— Plugins ———

    fun loadPlugins(): Pair<List<InstalledPlugin>, List<String>> {
        val index = readJson(pluginsIndex) ?: return emptyList<InstalledPlugin>() to emptyList()
        val loaded = mutableListOf<InstalledPlugin>()
        val problems = mutableListOf<String>()
        for (keyword in index.keys()) {
            val entry = index.getJSONObject(keyword)
            val file = File(pluginsDir, "$keyword.agp")
            try {
                val plugin = PluginLoader.load(file.readBytes())
                val signer = (plugin.signature as? SignatureStatus.Valid)?.fingerprint
                if (plugin.packageHash != entry.getString("hash")) {
                    problems += "$keyword: package changed on disk since it was approved; not loaded"
                } else if (signer != entry.optString("signer").ifEmpty { null }) {
                    // the signature is outside the package hash, so it is checked on its own
                    problems += "$keyword: signature changed on disk since it was approved; not loaded"
                } else {
                    loaded += InstalledPlugin(keyword, plugin)
                }
            } catch (e: PluginPackageException) {
                problems += "$keyword: ${e.message}"
            } catch (e: java.io.IOException) {
                problems += "$keyword: ${e.message}"
            }
        }
        return loaded to problems
    }

    fun installPlugin(keyword: String, bytes: ByteArray, plugin: Plugin) {
        File(pluginsDir, "$keyword.agp").writeBytes(bytes)
        val index = readJson(pluginsIndex) ?: JSONObject()
        val signer = (plugin.signature as? SignatureStatus.Valid)?.fingerprint
        index.put(keyword, JSONObject().put("id", plugin.manifest.id).put("hash", plugin.packageHash).put("signer", signer ?: ""))
        writeAtomically(pluginsIndex, index.toString(2))
    }

    fun removePlugin(keyword: String) {
        File(pluginsDir, "$keyword.agp").delete()
        val index = readJson(pluginsIndex) ?: return
        index.remove(keyword)
        writeAtomically(pluginsIndex, index.toString(2))
    }

    /** Signer of the installed plugin with this id, for signer continuity (ADR-009) */
    fun installedSigner(pluginId: String): InstalledSigner? {
        val index = readJson(pluginsIndex) ?: return null
        for (keyword in index.keys()) {
            val e = index.getJSONObject(keyword)
            if (e.getString("id") == pluginId) return InstalledSigner(e.optString("signer").ifEmpty { null })
        }
        return null
    }

    // ——— Trusted developer keys ———

    fun trustedKeys(): Map<String, String> {
        val json = readJson(trustedFile) ?: return emptyMap()
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    fun saveTrustedKeys(keys: Map<String, String>) {
        val json = JSONObject()
        keys.forEach { (fp, name) -> json.put(fp, name) }
        writeAtomically(trustedFile, json.toString(2))
    }

    // ——— Settings ———

    fun settings(): AgentSettings {
        val json = readJson(settingsFile) ?: return AgentSettings()
        val senders = json.optJSONArray("allowedSenders") ?: JSONArray()
        return AgentSettings(
            allowedSenders = (0 until senders.length()).map { senders.getString(it) }.toSet(),
            sheetId = json.optInt("sheetId", 0),
            sheetSize = json.optInt("sheetSize", 100),
            stopped = json.optBoolean("stopped", false),
            financialApps = json.optJSONArray("financialApps")
                ?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                ?: PluginClassifier.KNOWN_FINANCIAL_APPS
        )
    }

    fun saveSettings(s: AgentSettings) = writeAtomically(
        settingsFile,
        JSONObject()
            .put("allowedSenders", JSONArray(s.allowedSenders.sorted()))
            .put("sheetId", s.sheetId)
            .put("sheetSize", s.sheetSize)
            .put("stopped", s.stopped)
            .put("financialApps", JSONArray(s.financialApps.sorted()))
            .toString(2)
    )

    // ——— Authentication state ———

    fun authState(sheetId: Int): AuthState {
        val json = readJson(authFile) ?: return AuthState(sheetId)
        if (json.optInt("sheetId", -1) != sheetId) return AuthState(sheetId)
        val used = json.optJSONArray("used") ?: JSONArray()
        return AuthState(
            sheetId = sheetId,
            used = (0 until used.length()).map { used.getInt(it) }.toSet(),
            consecutiveFailures = json.optInt("consecutiveFailures"),
            totalFailures = json.optInt("totalFailures"),
            lockouts = json.optInt("lockouts"),
            lockedUntil = json.optLong("lockedUntil"),
            hardLocked = json.optBoolean("hardLocked")
        )
    }

    fun saveAuthState(a: AuthState) = writeAtomically(
        authFile,
        JSONObject()
            .put("sheetId", a.sheetId)
            .put("used", JSONArray(a.used.sorted()))
            .put("consecutiveFailures", a.consecutiveFailures)
            .put("totalFailures", a.totalFailures)
            .put("lockouts", a.lockouts)
            .put("lockedUntil", a.lockedUntil)
            .put("hardLocked", a.hardLocked)
            .toString()
    )

    // ——— Audit ———

    fun auditEntries(): List<AuditEntry> =
        if (!auditFile.exists()) emptyList() else auditFile.readLines().filter { it.isNotBlank() }.map { line ->
            val j = JSONObject(line)
            AuditEntry(
                j.getLong("seq"), j.getLong("time"), j.getString("channel"), j.getString("sender"),
                j.getString("event"), j.getString("detail"), j.getString("prevHash"), j.getString("hash")
            )
        }

    fun appendAudit(entries: List<AuditEntry>) {
        if (entries.isEmpty()) return
        auditFile.appendText(entries.joinToString("") { e ->
            JSONObject()
                .put("seq", e.seq).put("time", e.time).put("channel", e.channel).put("sender", e.sender)
                .put("event", e.event).put("detail", e.detail).put("prevHash", e.prevHash).put("hash", e.hash)
                .toString() + "\n"
        })
    }

    // ——— Helpers ———

    private fun readJson(file: File): JSONObject? =
        if (file.exists()) JSONObject(file.readText()) else null

    private fun writeAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
