package com.proj.automation.engine

import com.proj.automation.ui.UiNode

/** Outcome of asking the device to open an app */
enum class LaunchResult { OK, NOT_INSTALLED, NOT_LAUNCHABLE }

/**
 * Everything the engine needs from a device. On the phone it is backed by the accessibility
 * service; in tests and in `agp test` replays it is backed by recorded screens. Elements are
 * [UiNode] snapshots; an implementation acts on the live element behind [UiNode.ref].
 */
interface DevicePort {
    /** The current screen, or null when it cannot be read */
    fun snapshot(): UiNode?

    /** Package of the app in the foreground, or null when unknown */
    fun foregroundPackage(): String?

    fun click(node: UiNode): Boolean
    fun setText(node: UiNode, text: String): Boolean
    fun scroll(node: UiNode, forward: Boolean): Boolean
    fun back(): Boolean
    fun home(): Boolean
    fun launchApp(packageName: String): LaunchResult
    fun openUrl(url: String): Boolean
}
