package com.proj.automation.replay

import com.proj.automation.dsl.RunResult
import com.proj.automation.dsl.RunStatus
import com.proj.automation.engine.ActionDispatcher
import com.proj.automation.engine.ErrorCode
import com.proj.automation.engine.ErrorHandler
import com.proj.automation.engine.ExecutionEngine
import com.proj.automation.engine.buildHandlerRegistry
import com.proj.automation.parser.YamlParseException
import com.proj.automation.parser.YamlParser
import com.proj.automation.plugin.Plugin
import com.proj.automation.plugin.PluginPackageException
import com.proj.automation.service.EventBus
import com.proj.automation.ui.UiXml
import kotlinx.coroutines.runBlocking

/** One replay test from a YAML file in the plugin's `tests` folder */
data class ReplayCase(
    val name: String,
    val skill: String,
    val args: Map<String, String>,
    /** Fixture shown before the first interaction; null = no app on screen */
    val start: String?,
    val transitions: List<TransitionSpec>,
    val expect: Expectation
)

data class TransitionSpec(val on: String, val label: String?, val screen: String?)

data class Expectation(
    val status: RunStatus,
    val error: ErrorCode?,
    val returnValue: String?,
    /** Exact interaction log, when given */
    val interactions: List<String>?
)

data class ReplayOutcome(
    val case: ReplayCase,
    val failures: List<String>,
    val result: RunResult?,
    val interactions: List<String>
) {
    val passed: Boolean get() = failures.isEmpty()
}

/**
 * Runs a plugin skill against recorded screens with the real engine (same interpreter, actions,
 * resolver and capability guard as on the phone), in virtual time, and checks the outcome.
 *
 * Test file format (`tests/<name>.yaml`):
 * ```
 * test: sends a message
 * skill: send
 * args: { phone: "5511999", text: "oi" }
 * start: home.xml                       # optional
 * transitions:                          # first match wins
 *   - { on: open_url, screen: chat.xml }
 *   - { on: click, label: "Enviar", screen: chat_sent.xml }
 * expect:
 *   status: succeeded                   # succeeded | failed | cancelled
 *   error: E_NOT_FOUND                  # optional
 *   return: "Sent to *****99"           # optional
 *   interactions: ["open_url:https://wa.me/5511999?text=oi", "click:Enviar"]   # optional, exact
 * ```
 */
object Replay {

    fun parseCase(text: String, where: String): ReplayCase {
        fun fail(msg: String): Nothing = throw PluginPackageException("$where: $msg")
        val doc = try {
            YamlParser().loadDocument(text)
        } catch (e: YamlParseException) {
            fail(e.message ?: "invalid YAML")
        }
        val extra = doc.keys - setOf("test", "skill", "args", "start", "transitions", "expect")
        if (extra.isNotEmpty()) fail("unknown keys $extra")
        val expect = doc["expect"] as? Map<*, *> ?: fail("missing 'expect'")
        val expectExtra = expect.keys.map { it.toString() } - setOf("status", "error", "return", "interactions")
        if (expectExtra.isNotEmpty()) fail("unknown keys in expect: $expectExtra")
        val status = expect["status"]?.toString()?.uppercase()?.let { s ->
            RunStatus.entries.find { it.name == s } ?: fail("unknown status '$s'")
        } ?: fail("expect.status is required")
        val error = expect["error"]?.toString()?.let { e -> ErrorCode.entries.find { it.name == e } ?: fail("unknown error code '$e'") }
        val transitions = (doc["transitions"] as? List<*>).orEmpty().mapIndexed { i, raw ->
            val t = raw as? Map<*, *> ?: fail("transitions[$i] must be a mapping")
            TransitionSpec(
                on = t["on"]?.toString() ?: fail("transitions[$i].on is required"),
                label = t["label"]?.toString(),
                screen = t["screen"]?.toString()
            )
        }
        return ReplayCase(
            name = doc["test"]?.toString() ?: where,
            skill = doc["skill"]?.toString() ?: fail("missing 'skill'"),
            args = (doc["args"] as? Map<*, *>).orEmpty().entries.associate { it.key.toString() to it.value.toString() },
            start = doc["start"]?.toString(),
            transitions = transitions,
            expect = Expectation(
                status = status,
                error = error,
                returnValue = expect["return"]?.toString(),
                interactions = (expect["interactions"] as? List<*>)?.map { it.toString() }
            )
        )
    }

    /** @param fixture returns the XML of a fixture by file name, or null when missing */
    fun run(plugin: Plugin, case: ReplayCase, fixture: (String) -> String?): ReplayOutcome {
        fun screen(name: String?) = name?.let { n ->
            UiXml.parse(fixture(n) ?: throw PluginPackageException("${case.name}: fixture '$n' not found"))
        }
        val device = ScreenDevice(
            screen(case.start),
            case.transitions.map { Transition(it.on, it.label, screen(it.screen)) }
        )
        var now = 0L
        val engine = ExecutionEngine(
            ActionDispatcher(buildHandlerRegistry()), ErrorHandler(), EventBus(),
            device = { device },
            clock = { now },
            sleep = { now += it }
        )
        if (case.skill !in plugin.skills) {
            return ReplayOutcome(case, listOf("unknown skill '${case.skill}'"), null, emptyList())
        }
        val result = runBlocking { engine.runSkill(plugin, case.skill, case.args) }

        val failures = mutableListOf<String>()
        val e = case.expect
        if (result.status != e.status) failures += "status ${result.status} (expected ${e.status})" + (result.message?.let { ": $it" } ?: "")
        if (e.error != null && result.errorCode != e.error) failures += "error ${result.errorCode} (expected ${e.error})"
        if (e.returnValue != null && result.returnValue != e.returnValue) failures += "return '${result.returnValue}' (expected '${e.returnValue}')"
        if (e.interactions != null && device.interactions != e.interactions) {
            failures += "interactions ${device.interactions} (expected ${e.interactions})"
        }
        return ReplayOutcome(case, failures, result, device.interactions.toList())
    }
}
