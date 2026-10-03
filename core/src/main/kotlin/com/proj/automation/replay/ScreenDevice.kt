package com.proj.automation.replay

import com.proj.automation.engine.DevicePort
import com.proj.automation.engine.LaunchResult
import com.proj.automation.ui.UiNode

/**
 * A screen change triggered by an interaction. [on] is the interaction kind (`click`, `type`,
 * `scroll`, `back`, `home`, `launch_app`, `open_url`); [label], when set, must be contained in the
 * element's label (or the package / URL for `launch_app` / `open_url`).
 */
data class Transition(val on: String, val label: String? = null, val screen: UiNode?)

/**
 * A simulated device for tests and `agp test` replays: it shows recorded screens, logs every
 * interaction, and moves to another screen when an interaction matches a [Transition]
 * (first match wins). Interactions that match no transition leave the screen as it is.
 */
open class ScreenDevice(
    var screen: UiNode?,
    private val transitions: List<Transition> = emptyList(),
    /** Packages `launch_app` can open; null means any */
    private val installed: Set<String>? = null,
    /** Device language reported to the engine (selects plugin texts) */
    private val language: String? = null
) : DevicePort {

    override fun language(): String? = language

    /** Every interaction in order, e.g. `click:Enviar`, `open_url:https://wa.me/...`, `type:Mensagem` */
    val interactions = mutableListOf<String>()

    override fun snapshot(): UiNode? = screen

    override fun foregroundPackage(): String? = screen?.walk()?.firstNotNullOfOrNull { it.packageName }

    override fun click(node: UiNode): Boolean = interact("click", node)

    override fun setText(node: UiNode, text: String): Boolean = interact("type", node)

    override fun scroll(node: UiNode, forward: Boolean): Boolean {
        if (!onScreen(node)) return false
        record(if (forward) "scroll" else "scroll_back", null)
        return true
    }

    override fun back(): Boolean = true.also { record("back", null) }

    override fun home(): Boolean = true.also { record("home", null) }

    override fun launchApp(packageName: String): LaunchResult {
        if (installed != null && packageName !in installed) return LaunchResult.NOT_INSTALLED
        record("launch_app", packageName)
        return LaunchResult.OK
    }

    override fun openUrl(url: String): Boolean = true.also { record("open_url", url) }

    private fun interact(kind: String, node: UiNode): Boolean {
        if (!onScreen(node)) return false
        record(kind, node.label)
        return true
    }

    /** Only elements of the screen being shown can be acted on */
    private fun onScreen(node: UiNode): Boolean = screen?.walk()?.any { it === node } == true

    protected open fun record(kind: String, label: String?) {
        interactions += if (label == null) kind else "$kind:${label.replace('\n', ' ')}"
        transitions.firstOrNull { t -> t.on == kind && (t.label == null || label?.contains(t.label) == true) }
            ?.let { screen = it.screen }
    }
}
