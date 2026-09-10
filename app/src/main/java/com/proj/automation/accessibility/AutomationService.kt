package com.proj.automation.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.proj.automation.MainActivity
import com.proj.automation.R
import com.proj.automation.service.EventBus
import com.proj.automation.service.StateManager
import java.util.concurrent.atomic.AtomicReference

class AutomationService : AccessibilityService(), AutomationBridge {

    companion object {
        const val CHANNEL_ID = "automation_service_channel"
        const val NOTIFICATION_ID = 1
        const val TAG = "AutomationService"

        @Volatile
        private var _instance: AutomationService? = null
        val instance: AutomationService
            get() = _instance
                ?: throw IllegalStateException(
                    "AutomationService not connected. " +
                    "Enable Accessibility Service in Settings first."
                )
    }

    private val rootNodeRef = AtomicReference<AccessibilityNodeInfo?>(null)
    private var _connected = false

    // State Manager reference (initialized in onServiceConnected)
    @Suppress("unused")
    private var stateManager: com.proj.automation.service.StateManager? = null

    // Event Bus reference (initialized in onServiceConnected)
    @Suppress("unused")
    private var eventBus: com.proj.automation.service.EventBus? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        _instance = this
        AutomationBridge.init(this)
    }

    override fun onServiceConnected() {
        _connected = true
        android.util.Log.i(TAG, "AutomationService connected — ready for automation")

        // Initialize StateManager and EventBus (deferred to avoid circular imports)
        stateManager = StateManager()
        eventBus = EventBus()

        // Wire EventBus to publish events from this service
        stateManager?.setEventBus(eventBus)

        // Publish connection event
        eventBus?.publish(com.proj.automation.service.EventBus.Event.ServiceConnected)
    }

    override fun onInterrupt() {
        // No-op for this POC.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        android.util.Log.i(TAG, "AutomationService unbinding")
        _connected = false
        eventBus?.publish(com.proj.automation.service.EventBus.Event.ServiceDisconnected("User disabled service"))
        return super.onUnbind(intent)
    }

    // ——— Foreground Service Setup (API 26+) ———

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // ——— AccessibilityEvent Handling ———

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                val className = event.className?.toString() ?: ""
                stateManager?.onWindowStateChanged(packageName, className)
                eventBus?.publish(
                    com.proj.automation.service.EventBus.Event.WindowChanged(packageName, className)
                )
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                stateManager?.onContentChanged(getRootNode())
                eventBus?.publish(
                    com.proj.automation.service.EventBus.Event.AccessibilityTreeChanged(getRootNode())
                )
            }

            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                detectDialogOrToast(event)
            }
        }
    }

    private fun detectDialogOrToast(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: return
        if (className.contains("Dialog", ignoreCase = true)) {
            eventBus?.publish(com.proj.automation.service.EventBus.Event.DialogDetected(className))
        }
        if (className.contains("Toast", ignoreCase = true)) {
            val text = event.text?.firstOrNull()?.toString() ?: ""
            if (text.isNotBlank()) {
                eventBus?.publish(com.proj.automation.service.EventBus.Event.ToastDetected(text))
            }
        }
    }

    // ——— AutomationBridge Implementation ———

    override fun getRootNode(): AccessibilityNodeInfo? {
        val node = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            rootInActiveWindow
        } else {
            null
        }
        rootNodeRef.set(node)
        return node
    }

    override fun click(node: AccessibilityNodeInfo): Boolean {
        return try {
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            // Recycle after use to prevent memory leaks
            // Note: callers are responsible for recycling nodes they own
            success
        } catch (e: Exception) {
            android.util.Log.w(TAG, "click failed: ${e.message}")
            false
        }
    }

    override fun longClick(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    override fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "setText failed: ${e.message}")
            false
        }
    }

    override fun scrollForward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    override fun scrollBackward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    override fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun startActivity(intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            super.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "startActivity failed: ${e.message}")
        }
    }
}
