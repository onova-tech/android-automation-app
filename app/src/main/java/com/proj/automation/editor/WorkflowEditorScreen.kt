package com.proj.automation.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proj.automation.engine.ActionDispatcher
import com.proj.automation.engine.ErrorHandler
import com.proj.automation.engine.ExecutionEngine
import com.proj.automation.engine.buildHandlerRegistry
import com.proj.automation.engine.models.ExecutionResult
import com.proj.automation.parser.YamlParser
import com.proj.automation.service.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.coroutines.cancellation.CancellationException

/**
 * Main screen for composing and running YAML workflows.
 * Provides a monospace editor, run/stop controls, and execution log output.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowEditorScreen() {
    val context = LocalContext.current
    var permissionEnabled by remember { mutableStateOf(checkAccessibilityPermission(context)) }

    // Re-check when returning from system Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionEnabled = checkAccessibilityPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Editor state
    var yamlText by remember { mutableStateOf(DEFAULT_WORKFLOW) }
    var isRunning by remember { mutableStateOf(false) }
    var logLines by remember { mutableStateOf(emptyList<LogEntry>()) }
    var statusText by remember { mutableStateOf("Idle") }

    // Engine references (created once per composition)
    val parser = remember { YamlParser() }
    val eventBus = remember { EventBus.default }
    val handlers = remember { buildHandlerRegistry() }
    val dispatcher = remember { ActionDispatcher(handlers) }
    val errorHandler = remember { ErrorHandler() }
    val engine = remember { ExecutionEngine(dispatcher, errorHandler, eventBus) }

    // Coroutine scope for execution
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    var currentJob by remember { mutableStateOf<Job?>(null) }

    // Subscribe to event bus for log updates. Events arrive on background threads,
    // so appends are posted to the main thread to keep them ordered and race-free.
    DisposableEffect(eventBus) {
        val mainHandler = Handler(Looper.getMainLooper())
        val subscription = eventBus.subscribe { event ->
            val text = when (event) {
                is EventBus.Event.StepCompleted ->
                    if (event.success) "[✓] Step ${event.stepIndex}: ${event.action}"
                    else "[✗] Step ${event.stepIndex}: ${event.action} — ${event.errorMessage ?: "failed"}"
                is EventBus.Event.LogMessage -> "[LOG] ${event.message}"
                else -> null
            }
            if (text != null) {
                mainHandler.post {
                    logLines = logLines + LogEntry(timestamp = System.currentTimeMillis(), text = text)
                }
            }
        }
        onDispose { eventBus.unsubscribe(subscription) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Android Automation POC",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Permission status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (permissionEnabled) "✓" else "⚠",
                style = MaterialTheme.typography.bodyMedium,
                color = if (permissionEnabled) Color(0xFF4CAF50) else Color(0xFFFF9800),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = if (permissionEnabled) "Accessibility Service enabled"
                else "Enable Accessibility Service to run workflows",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (!permissionEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Settings")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // YAML Editor
        OutlinedTextField(
            value = yamlText,
            onValueChange = { yamlText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            label = { Text("YAML Workflow") },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            singleLine = false,
            maxLines = Int.MAX_VALUE,
            enabled = !isRunning,
            colors = OutlinedTextFieldDefaults.colors()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Run / Stop buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    isRunning = true
                    statusText = "Running…"
                    logLines = emptyList()
                    currentJob = scope.launch {
                        executeWorkflow(
                            engine = engine,
                            parser = parser,
                            yamlText = yamlText,
                            onError = { message ->
                                logLines = logLines + LogEntry(System.currentTimeMillis(), "[✗] $message")
                            },
                            onResult = { result ->
                                isRunning = false
                                statusText = if (result.completedSuccessfully) {
                                    "Completed: ${result.stepCount} steps"
                                } else if (result.cancelled) {
                                    "Cancelled"
                                } else {
                                    "Failed: ${result.failedSteps.size} step(s)"
                                }
                            }
                        )
                    }
                },
                enabled = permissionEnabled && !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text("▶ Run")
            }

            Button(
                onClick = {
                    currentJob?.cancel()
                    engine.cancel()
                    isRunning = false
                    statusText = "Cancelled"
                },
                enabled = isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF44336)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("■ Stop")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status indicator
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = when {
                statusText.startsWith("Completed") -> Color(0xFFE8F5E9)
                statusText.startsWith("Failed") || statusText.startsWith("Cancelled") -> Color(0xFFFFEBEE)
                else -> Color(0xFFE3F2FD)
            },
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = statusText,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Execution Log
        Text(
            text = "Execution Log",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable log output
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                ) {
                    if (logLines.isEmpty()) {
                        Text(
                            text = "No execution log yet. Paste a workflow and tap Run.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    } else {
                        logLines.forEach { entry ->
                            Text(
                                text = entry.text,
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                color = if (entry.text.startsWith("[✗]") || entry.text.startsWith("[LOG]")) {
                                    Color(0xFFD32F2F)
                                } else {
                                    Color(0xFF2E7D32)
                                },
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ——— Helper Functions ———

private suspend fun executeWorkflow(
    engine: ExecutionEngine,
    parser: YamlParser,
    yamlText: String,
    onError: (String) -> Unit,
    onResult: (ExecutionResult) -> Unit
) {
    try {
        val workflow = parser.parse(yamlText)
        val result = engine.execute(workflow)
        withContext(Dispatchers.Main) {
            onResult(result)
        }
    } catch (e: CancellationException) {
        // Stop pressed — the UI already shows "Cancelled"
        throw e
    } catch (e: Exception) {
        val errorMsg = "Parse error: ${e.message}"
        withContext(Dispatchers.Main) {
            onError(errorMsg)
            onResult(
                ExecutionResult(
                    workflowName = null,
                    steps = mutableListOf(),
                    completedSuccessfully = false,
                    startTime = System.currentTimeMillis(),
                    endTime = System.currentTimeMillis()
                )
            )
            android.util.Log.e("WorkflowEditor", errorMsg, e)
        }
    }
}

private fun checkAccessibilityPermission(context: android.content.Context): Boolean {
    return try {
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        enabled?.contains("com.proj.automation") == true
    } catch (_: Exception) {
        false
    }
}

// ——— Data Classes ———

data class LogEntry(
    val timestamp: Long,
    val text: String
)

// ——— Default Workflow (Calculator Demo) ———

private val DEFAULT_WORKFLOW = """
name: Calculator - Add 2 + 3
steps:
  - launch_app:
      package: com.google.android.calculator

  - wait:
      seconds: 2

  - wait_for:
      selector:
        text: "2"
      timeout: 5000

  - click:
      selector:
        text: "2"
      retries: 3

  - click:
      selector:
        text: "+"

  - click:
      selector:
        text: "3"

  - click:
      selector:
        text: "="
      retries: 5
      retry_delay: 1000

  - wait:
      seconds: 2
""".trimIndent()
