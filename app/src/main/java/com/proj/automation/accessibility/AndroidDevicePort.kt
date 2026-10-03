package com.proj.automation.accessibility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.proj.automation.engine.DevicePort
import com.proj.automation.engine.LaunchResult
import com.proj.automation.ui.UiNode
import com.proj.automation.ui.UiSnapshots

/** [DevicePort] backed by the accessibility service: acts on the live node behind each [UiNode]. */
class AndroidDevicePort(private val bridge: AutomationBridge, private val context: Context) : DevicePort {

    override fun snapshot(): UiNode? = bridge.getRootNode()?.let { UiSnapshots.fromAccessibility(it) }

    override fun foregroundPackage(): String? = bridge.getRootNode()?.packageName?.toString()

    override fun click(node: UiNode): Boolean = live(node)?.let { bridge.click(it) } ?: false

    override fun setText(node: UiNode, text: String): Boolean = live(node)?.let { bridge.setText(it, text) } ?: false

    override fun scroll(node: UiNode, forward: Boolean): Boolean =
        live(node)?.let { if (forward) bridge.scrollForward(it) else bridge.scrollBackward(it) } ?: false

    override fun back(): Boolean = bridge.goBack()

    override fun home(): Boolean = bridge.goHome()

    override fun launchApp(packageName: String): LaunchResult {
        val pm = context.packageManager
        try {
            pm.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return LaunchResult.NOT_INSTALLED
        }
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return LaunchResult.NOT_LAUNCHABLE
        bridge.startActivity(intent)
        return LaunchResult.OK
    }

    override fun openUrl(url: String): Boolean {
        bridge.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return true
    }

    private fun live(node: UiNode): AccessibilityNodeInfo? = node.ref as? AccessibilityNodeInfo

    companion object {
        /** The device port while the accessibility service is running, else null */
        fun current(context: Context): DevicePort? = AutomationBridge.instance?.let { AndroidDevicePort(it, context) }
    }
}
