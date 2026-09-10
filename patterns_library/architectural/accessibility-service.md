# Accessibility Service Implementation Pattern

## Purpose

Guide the implementation of an `AccessibilityService` subclass that bridges the automation runtime to Android's `AccessibilityNodeInfo` API. Covers lifecycle management, foreground service setup, event handling, element actions, and tree traversal.

## When This Pattern Applies

Use this pattern when:
- Implementing the `AutomationService` that the execution engine communicates with
- Setting up foreground service notification (required API 26+)
- Handling `AccessibilityEvent` types for window/content changes
- Performing element actions (click, setText, scroll) via the Accessibility API
- Navigating with system global actions (back, home)
- Retrieving the accessibility tree root for selector engine use

## Foreground Service Setup (API 26+)

Foreground services are mandatory on API 26+ for long-running automation. The service must post a notification that the user can see and dismiss.

```kotlin
package com.proj.automation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.proj.automation.MainActivity
import com.proj.automation.R

class AutomationService : AccessibilityService() {

    companion object {
        const val CHANNEL_ID = "automation_service_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Automation Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Android Automation Runtime service"
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
            .setContentTitle("Android Automation")
            .setContentText("Automation service is running")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
```

## Lifecycle Methods

```kotlin
override fun onServiceConnected() {
    // Called by the system after the service is bound and permissions granted
    // Service is now ready to receive events and perform actions
    Log.i(TAG, "AutomationService connected — ready for automation")
    // Publish connection event to EventBus if needed
}

override fun onUnbind(intent: android.content.Intent?): Boolean {
    // Called when the service is unbound (user disables Accessibility Service)
    // Stop foreground service and clean up resources
    stopForeground(STOP_FOREGROUND_REMOVE)
    return super.onUnbind(intent)
}
```

## Event Handling

Handle the three event types declared in `accessibility_service_config.xml`:

- `typeWindowStateChanged` — Activity/screen changed
- `typeWindowContentChanged` — UI content changed (text updated, new views added)
- `typeWindowsChanged` — Windows added/removed (dialogs, toasts)

```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent) {
    when (event.eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
            val packageName = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: ""
            // State manager captures active package/activity
            stateManager.onWindowStateChanged(packageName, className)
            eventBus.publish(Event.WindowChanged(packageName, className))
        }

        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
            // Tree content changed — snapshot for selector engine
            stateManager.onContentChanged(getRootNode())
            eventBus.publish(Event.AccessibilityTreeChanged(getRootNode()))
        }

        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
            // New window appeared — check for dialogs/toasts
            detectDialogOrToast(event)
        }
    }
}

private fun detectDialogOrToast(event: AccessibilityEvent) {
    // Check if a dialog window appeared (dialog classes from common frameworks)
    val className = event.className?.toString() ?: return
    if (className.contains("Dialog", ignoreCase = true)) {
        eventBus.publish(Event.DialogDetected(className))
    }
    // Toast detection — toast classes have specific patterns
    if (className.contains("Toast", ignoreCase = true)) {
        val text = event.text?.firstOrNull()?.toString() ?: ""
        if (text.isNotBlank()) {
            eventBus.publish(Event.ToastDetected(text))
        }
    }
}
```

## Tree Access — `getRootNode()`

```kotlin
/**
 * Returns the root of the accessibility tree for the currently active window.
 * Returns null if no window is active or the service is not connected.
 *
 * IMPORTANT: Callers MUST recycle the returned node tree when done to avoid leaks:
 *   rootNode?.let {
 *       try { /* search tree */ } finally { it.recycle() }
 *   }
 */
fun getRootNode(): AccessibilityNodeInfo? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        rootInActiveWindow
    } else {
        null
    }
}
```

## Element Actions

### Click

```kotlin
fun click(node: AccessibilityNodeInfo): Boolean {
    return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
}

/** Click with retry — attempts click multiple times if it fails to change focus */
fun clickWithRetry(node: AccessibilityNodeInfo, maxAttempts: Int = 3, delayMs: Long = 300): Boolean {
    repeat(maxAttempts) { attempt ->
        val success = click(node)
        if (success) return true
        if (attempt < maxAttempts - 1) {
            Thread.sleep(delayMs)
        }
    }
    return false
}
```

### Set Text

```kotlin
fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
    val args = Bundle().apply {
        putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
    }
    return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
}
```

### Scroll Forward

```kotlin
fun scrollForward(node: AccessibilityNodeInfo): Boolean {
    return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
}

fun scrollBackward(node: AccessibilityNodeInfo): Boolean {
    return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
}
```

### Long Click

```kotlin
fun longClick(node: AccessibilityNodeInfo): Boolean {
    return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
}
```

## Global Navigation Actions

```kotlin
fun goBack(): Boolean {
    return performGlobalAction(GLOBAL_ACTION_BACK)
}

fun goHome(): Boolean {
    return performGlobalAction(GLOBAL_ACTION_HOME)
}

fun goToAppSwitcher(): Boolean {
    return performGlobalAction(GLOBAL_ACTION_APP_SWITCH)
}
```

## Screenshot (API 29+) — Placeholder for Phase 2

```kotlin
/**
 * Takes a screenshot of the current screen. Requires API 29+ and the user must
 * grant screen capture permission via system dialog.
 *
 * Not available in Phase 1 — placeholder for Phase 2 OCR fallback.
 */
@RequiresApi(Build.VERSION_CODES.Q)
fun takeScreenshot(): Bitmap? {
    return try {
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // Requires user permission dialog — not suitable for automated POC
        null // Placeholder: implement with createScreenCaptureIntent() in Phase 2
    } catch (e: SecurityException) {
        Log.w(TAG, "Screenshot not permitted: ${e.message}")
        null
    }
}
```

## Resource ID and Notification Icon

**`res/drawable/ic_notification.xml`** (vector drawable):

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-2h2v2zM13,13h-2V7h2v6z" />
</vector>
```

**`res/values/strings.xml`** additions:

```xml
<string name="notification_channel_name">Automation Service</string>
<string name="notification_channel_description">Android Automation Runtime service notification</string>
<string name="notification_title">Android Automation</string>
<string name="notification_text">Automation service is running</string>
```

## Customization Guide

| Parameter | Default | Notes |
|-----------|---------|-------|
| `notificationTimeout` | 100ms | Minimum recommended 50ms; lower = faster event processing |
| `accessibilityEventTypes` | `typeWindowStateChanged\|typeWindowContentChanged\|typeWindowsChanged` | Add `typeViewClicked` to monitor tap events |
| `accessibilityFeedbackType` | `feedbackGeneric` | Does not generate audible/haptic feedback |
| `canPerformGestures` | `true` | Required for click/scroll/longClick actions |
| `canRetrieveWindowContent` | `true` | Required for getRootNode() / selector engine |
| `accessibilityFlags` | `flagDefault\|flagIncludeNotImportantViews\|flagRetrieveInteractiveWindows` | `flagRetrieveInteractiveWindows` needed for cross-window access |
| Notification channel importance | `IMPORTANCE_LOW` | Avoids sound/vibration; use `IMPORTANCE_DEFAULT` if user needs to be alerted |
| Notification content intent | `MainActivity` | Tapping notification opens the app |

## Validation

Before using this service in the execution engine, verify:

- [ ] `onServiceConnected()` is called after user enables the service in Settings
- [ ] Foreground notification appears immediately after service starts (API 26+)
- [ ] `getRootNode()` returns a non-null `AccessibilityNodeInfo` for the active window
- [ ] `click()` returns `true` when called on a clickable node
- [ ] `setText()` replaces the text in an EditText node
- [ ] `scrollForward()` scrolls the node content
- [ ] `goBack()` triggers system back navigation
- [ ] `goHome()` returns to the launcher
- [ ] `onAccessibilityEvent()` receives all three declared event types
- [ ] `onUnbind()` stops the foreground service cleanly
- [ ] Service survives configuration change (rotation)
- [ ] Notification persists when app is backgrounded (START_STICKY)
- [ ] Tree nodes are recycled after use (no memory leaks)

## Known Limitations

| Limitation | Impact | Mitigation |
|-----------|--------|------------|
| `FLAG_SECURE` apps block tree access | Cannot automate banking, VPN, or DRM apps | POC avoids FLAG_SECURE apps; document as Phase 2 gap |
| `ACTION_SET_TEXT` unreliable on some EditText | Text input may fail on custom views | POC uses Calculator (standard EditText); acceptable for Phase 1 |
| No programmatic enable/disable of service | User must manually enable in Settings | Acceptable for POC; document setup steps |
| Tree recycling required | Forgetting to recycle causes memory leaks | Wrap tree access in try/finally blocks |
| Emulator Accessibility gaps | Non-Google-Play emulators may have incomplete support | Use Google Play system images only |
