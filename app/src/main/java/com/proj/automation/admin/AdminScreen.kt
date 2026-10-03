package com.proj.automation.admin

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.proj.automation.agent.AgentCoordinator
import com.proj.automation.channel.Envelope
import com.proj.automation.channel.GlobalVerb
import com.proj.automation.plugin.InstallDecision
import com.proj.automation.plugin.Plugin
import com.proj.automation.plugin.PluginLoader
import com.proj.automation.plugin.PluginPackageException
import com.proj.automation.plugin.SignatureStatus
import com.proj.automation.security.AuditLog
import com.proj.automation.service.EventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_PACKAGE_BYTES = 8 * 1024 * 1024

/** A package read from a file and waiting for the owner's approval */
private class PendingInstall(val bytes: ByteArray, val plugin: Plugin, val keyword: String)

/**
 * On-device admin screen (specs/003-command-channel, User Story 5). Everything that
 * changes what the agent may do — installing plugins, the code sheet, re-enabling after STOP —
 * asks for the device credential first. Nothing here is reachable from a remote channel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var refresh by remember { mutableIntStateOf(0) }
    var accessibilityOn by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var smsGranted by remember { mutableStateOf(hasSmsPermission(context)) }
    val deviceSecure = remember(refresh) { context.getSystemService(KeyguardManager::class.java).isDeviceSecure }
    var message by remember { mutableStateOf<String?>(null) }

    var pendingInstall by remember { mutableStateOf<PendingInstall?>(null) }
    var sheetCodes by remember { mutableStateOf<List<String>?>(null) }
    var sendersText by remember(refresh) { mutableStateOf(AgentCoordinator.settings.allowedSenders.sorted().joinToString("\n")) }
    var consoleInput by remember { mutableStateOf("") }
    var consoleLog by remember { mutableStateOf(listOf<String>()) }

    // Re-check permissions when coming back from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = isAccessibilityEnabled(context)
                smsGranted = hasSmsPermission(context)
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Step results from the engine, appended on the main thread
    DisposableEffect(Unit) {
        val main = Handler(Looper.getMainLooper())
        val sub = EventBus.default.subscribe { event ->
            if (event is EventBus.Event.StepCompleted) {
                val line = (if (event.success) "  ✓ " else "  ✗ ") + event.action + (event.errorMessage?.let { " — $it" } ?: "")
                main.post { consoleLog = (consoleLog + line).takeLast(200) }
            }
        }
        onDispose { EventBus.default.unsubscribe(sub) }
    }

    // ——— Device credential gate ———
    var afterCredential by remember { mutableStateOf<(() -> Unit)?>(null) }
    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val action = afterCredential
        afterCredential = null
        if (r.resultCode == Activity.RESULT_OK) action?.invoke() else message = "Device credential not confirmed"
    }
    fun withCredential(reason: String, action: () -> Unit) {
        val km = context.getSystemService(KeyguardManager::class.java)
        if (!km.isDeviceSecure) {
            message = "Set a screen-lock PIN first (Settings → Lock screen)"
            return
        }
        @Suppress("DEPRECATION")
        val intent = km.createConfirmDeviceCredentialIntent("Admin", reason)
        if (intent == null) { message = "Device credential unavailable"; return }
        afterCredential = action
        credentialLauncher.launch(intent)
    }

    // ——— Launchers ———
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        smsGranted = hasSmsPermission(context)
    }
    val openPackage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val data = input.readBytes()
                        if (data.size > MAX_PACKAGE_BYTES) throw PluginPackageException("File larger than $MAX_PACKAGE_BYTES bytes")
                        data
                    } ?: throw PluginPackageException("Cannot read the file")
                }
                val plugin = withContext(Dispatchers.Default) { PluginLoader.load(bytes) }
                val keyword = plugin.manifest.id.uppercase().filter(Char::isLetterOrDigit).take(10)
                pendingInstall = PendingInstall(bytes, plugin, keyword)
                null
            } catch (e: PluginPackageException) {
                "Package rejected: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Automation agent — admin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        message?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                Row(Modifier.padding(8.dp)) {
                    Text(it, Modifier.weight(1f))
                    TextButton(onClick = { message = null }) { Text("OK") }
                }
            }
        }

        // ——— Status ———
        Section("Status") {
            StatusLine(accessibilityOn, "Accessibility service", "Open settings") {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            StatusLine(smsGranted, "SMS permission", "Grant") {
                smsPermission.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS))
            }
            StatusLine(deviceSecure, "Screen lock (PIN) set", "Open settings") {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            if (AgentCoordinator.stopped) {
                Text("Remote commands are STOPPED.", color = Color(0xFFD32F2F))
                Button(onClick = {
                    withCredential("Re-enable remote commands") { scope.launch { AgentCoordinator.reenable(); refresh++ } }
                }) { Text("Re-enable") }
            }
        }

        // ——— Plugins ———
        Section("Plugins") {
            key(refresh) {
                if (AgentCoordinator.plugins.isEmpty()) Text("No plugins installed")
                AgentCoordinator.plugins.forEach { p ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            "${p.keyword}: ${p.plugin.manifest.name} ${p.plugin.manifest.version}  #${p.plugin.packageHash.take(8)}",
                            Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            withCredential("Remove plugin ${p.keyword}") { scope.launch { AgentCoordinator.removePlugin(p.keyword); refresh++ } }
                        }) { Text("Remove") }
                    }
                }
                AgentCoordinator.loadProblems.forEach { Text("⚠ $it", color = Color(0xFFD32F2F)) }
            }
            Button(onClick = { openPackage.launch(arrayOf("*/*")) }) { Text("Install plugin (.agp)") }
        }

        // ——— Trusted developers ———
        Section("Trusted developers") {
            Text("Keys whose packages install as verified. Financial plugins need one of these.", style = MaterialTheme.typography.bodySmall)
            key(refresh) {
                val keys = AgentCoordinator.trustedKeys()
                if (keys.isEmpty()) Text("None")
                keys.entries.sortedBy { it.value }.forEach { (fp, name) ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name)
                            Text(fp, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp))
                        }
                        TextButton(onClick = {
                            withCredential("Stop trusting $name") { scope.launch { AgentCoordinator.untrustKey(fp); refresh++ } }
                        }) { Text("Remove") }
                    }
                }
            }
        }

        // ——— Financial apps ———
        Section("Financial apps") {
            Text(
                "Plugins that operate or read these apps are treated as financial, whatever they declare: " +
                    "trusted signer required, highest risk level, no interrupt rules. One package name per line.",
                style = MaterialTheme.typography.bodySmall
            )
            var financialText by remember(refresh) { mutableStateOf(AgentCoordinator.settings.financialApps.sorted().joinToString("\n")) }
            OutlinedTextField(value = financialText, onValueChange = { financialText = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(onClick = {
                val apps = financialText.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                withCredential("Change the list of financial apps") { scope.launch { AgentCoordinator.setFinancialApps(apps); refresh++ } }
            }) { Text("Save financial apps") }
        }

        // ——— Senders ———
        Section("Allowed SMS senders") {
            Text("One number per line. This filters noise only; every command still needs a code.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = sendersText, onValueChange = { sendersText = it },
                modifier = Modifier.fillMaxWidth(), minLines = 2
            )
            Button(onClick = {
                val senders = sendersText.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                withCredential("Change allowed senders") { scope.launch { AgentCoordinator.setAllowedSenders(senders); refresh++ } }
            }) { Text("Save senders") }
        }

        // ——— Code sheet ———
        Section("Code sheet") {
            Text(
                "Sheet ${AgentCoordinator.settings.sheetId}. A new sheet invalidates every code of the old one. " +
                    "The codes are shown once and never stored.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = {
                withCredential("Generate a new code sheet") { scope.launch { sheetCodes = AgentCoordinator.newCodeSheet(); refresh++ } }
            }) { Text("Generate new sheet") }
        }

        // ——— Console ———
        Section("Console (on this phone, no code needed)") {
            OutlinedTextField(
                value = consoleInput, onValueChange = { consoleInput = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("Command, e.g. WHATSAPP SEND 5511999: hi") }, singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = consoleInput.isNotBlank(), onClick = {
                    val text = consoleInput
                    consoleLog = consoleLog + "> $text"
                    scope.launch {
                        val replies = AgentCoordinator.handle(
                            Envelope("local", AgentCoordinator.LOCAL_SENDER, text, System.currentTimeMillis()),
                            AgentCoordinator.localChannel()
                        )
                        consoleLog = (consoleLog + replies.map { "< $it" }).takeLast(200)
                        refresh++
                    }
                }) { Text("Run") }
                OutlinedButton(onClick = { AgentCoordinator.cancelRunning() }) { Text("Stop") }
            }
            SelectionContainer {
                Text(
                    consoleLog.takeLast(40).joinToString("\n").ifEmpty { "No output yet" },
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
            }
        }

        // ——— Audit ———
        Section("Audit log") {
            key(refresh) {
                val entries = AgentCoordinator.auditEntries()
                val broken = AuditLog.verify(entries)
                Text(
                    if (broken == null) "Chain intact (${entries.size} records)" else "⚠ Chain broken at record ${broken + 1}",
                    color = if (broken == null) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                )
                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                Text(
                    entries.takeLast(15).reversed().joinToString("\n") { "${fmt.format(Date(it.time))} ${it.channel} ${it.event} ${it.detail}" },
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                )
            }
        }
    }

    // ——— Dialogs ———
    pendingInstall?.let { install ->
        var keyword by remember(install) { mutableStateOf(install.keyword) }
        var trustKey by remember(install) { mutableStateOf(false) }
        var trustName by remember(install) { mutableStateOf("") }
        var unsignedAccepted by remember(install) { mutableStateOf(false) }
        val keywordError = keywordProblem(keyword, install.plugin.manifest.id)
        val fingerprint = (install.plugin.signature as? SignatureStatus.Valid)?.fingerprint
        val knownName = fingerprint?.let { AgentCoordinator.trustedKeys()[it] }
        val newTrust = if (trustKey && fingerprint != null && trustName.isNotBlank()) mapOf(fingerprint to trustName.trim()) else emptyMap()
        val decision = AgentCoordinator.installDecision(install.plugin, newTrust)
        val canApprove = keywordError == null && decision is InstallDecision.Allowed &&
            (fingerprint != null || unsignedAccepted) && (!trustKey || trustName.isNotBlank())
        AlertDialog(
            onDismissRequest = { pendingInstall = null },
            title = { Text("Approve plugin?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // ——— Identity (specs/006-package-signing) ———
                    when (decision) {
                        is InstallDecision.Blocked -> Text("⛔ ${decision.reason}", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                        is InstallDecision.Allowed -> {
                            if (decision.classification.financial) {
                                Text("Treated as FINANCIAL: ${decision.classification.financialReasons.joinToString()}", fontWeight = FontWeight.Bold)
                            }
                            decision.verifiedAs?.let { Text("✓ Verified developer: $it", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold) }
                            decision.warnings.forEach { Text("⚠ $it", color = Color(0xFFE65100), fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (fingerprint != null && knownName == null) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = trustKey, onCheckedChange = { trustKey = it })
                            Text("Trust this developer's key from now on")
                        }
                        if (trustKey) {
                            Text("Only if the developer gave you this fingerprint through another channel:", style = MaterialTheme.typography.bodySmall)
                            Text(fingerprint, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
                            OutlinedTextField(value = trustName, onValueChange = { trustName = it.take(40) }, label = { Text("Developer name") }, singleLine = true)
                        }
                    }
                    if (fingerprint == null && decision is InstallDecision.Allowed) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = unsignedAccepted, onCheckedChange = { unsignedAccepted = it })
                            Text("I understand this package's identity cannot be verified")
                        }
                    }
                    // ——— What it asks for ———
                    Text(install.plugin.installSummary(), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
                    OutlinedTextField(
                        value = keyword, onValueChange = { keyword = it.uppercase().take(10) },
                        label = { Text("Command keyword") }, isError = keywordError != null,
                        supportingText = { keywordError?.let { Text(it) } }, singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(enabled = canApprove, onClick = {
                    withCredential("Install plugin ${install.plugin.manifest.id}") {
                        scope.launch {
                            try {
                                newTrust.forEach { (fp, name) -> AgentCoordinator.trustKey(fp, name) }
                                AgentCoordinator.installPlugin(keyword, install.bytes, install.plugin)
                            } catch (e: PluginPackageException) {
                                message = "Not installed: ${e.message}"
                            }
                            pendingInstall = null
                            refresh++
                        }
                    }
                }) { Text("Approve") }
            },
            dismissButton = { TextButton(onClick = { pendingInstall = null }) { Text("Cancel") } }
        )
    }

    sheetCodes?.let { codes ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("New code sheet") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Copy these codes to paper now. They will not be shown again.", fontWeight = FontWeight.Bold)
                    Text(codes.joinToString("\n"), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                }
            },
            confirmButton = { Button(onClick = { sheetCodes = null }) { Text("I wrote them down") } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean, label: String, actionLabel: String, action: () -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(if (ok) "✓" else "⚠", color = if (ok) Color(0xFF2E7D32) else Color(0xFFF57C00), modifier = Modifier.padding(end = 8.dp))
        Text(label, Modifier.weight(1f))
        if (!ok) TextButton(onClick = action) { Text(actionLabel) }
    }
}

private val KEYWORD = Regex("[A-Z][A-Z0-9]{0,9}")

private fun keywordProblem(keyword: String, pluginId: String): String? = when {
    !KEYWORD.matches(keyword) -> "1–10 letters or digits, starting with a letter"
    GlobalVerb.entries.any { it.name == keyword } -> "Reserved word"
    AgentCoordinator.plugins.any { it.keyword == keyword && it.plugin.manifest.id != pluginId } -> "Used by another plugin"
    else -> null
}

private fun hasSmsPermission(context: Context): Boolean =
    listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun isAccessibilityEnabled(context: Context): Boolean = try {
    Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.contains(context.packageName) == true
} catch (_: Exception) {
    false
}
