package com.proj.automation.plugin

import com.proj.automation.dsl.DslParser
import com.proj.automation.dsl.Flow
import com.proj.automation.dsl.Node
import com.proj.automation.dsl.Program
import com.proj.automation.parser.ActionType
import com.proj.automation.parser.YamlParseException
import com.proj.automation.parser.YamlParser
import com.proj.automation.resolve.Target

/** An SMS/channel verb mapped to a skill, with its effective risk level (0–5) */
data class Command(val verb: String, val skill: String, val args: String?, val risk: Int)

data class Skill(val name: String, val description: String?, val declaredRisk: Int?, val program: Program)

/** A validated, loaded plugin package. */
data class Plugin(
    val manifest: Manifest,
    /** SHA-256 of PACKAGE.lock — what the owner approves */
    val packageHash: String,
    val commands: Map<String, Command>,
    val skills: Map<String, Skill>,
    val targets: Set<String>,
    /** Named targets as parsed definitions (templates left unrendered) */
    val targetDefs: Map<String, Target>,
    val screens: Set<String>,
    val fixtures: List<String>,
    val readme: String?
) {
    /** Text shown in admin mode before the owner approves the install (plugins.md section 10) */
    fun installSummary(): String = buildString {
        val m = manifest
        appendLine("${m.name} ${m.version} (${m.id})")
        appendLine("Operates: ${m.appPackage}${if (m.treeOnly) " (accessibility tree only)" else ""}")
        appendLine("Category: ${m.category.name.lowercase()}${if (m.readOnly) ", read-only" else ""}")
        appendLine("Tested app versions: ${m.testedVersions.ifEmpty { listOf("none declared") }.joinToString()}")
        appendLine("Permissions requested:")
        m.capabilities.describe().ifEmpty { listOf("none") }.forEach { appendLine("  - $it") }
        if (m.secrets.isNotEmpty()) {
            appendLine("Secrets you will be asked to type now:")
            m.secrets.forEach { appendLine("  - ${it.name}: ${it.prompt}") }
        }
        appendLine("Commands (risk level):")
        commands.values.sortedBy { it.verb }.forEach { c ->
            val args = c.args?.let { " $it" } ?: ""
            appendLine("  - ${m.id.uppercase()} ${c.verb}$args (${c.risk})")
        }
        if (m.libraries.isNotEmpty()) appendLine("Bundled libraries: ${m.libraries.entries.joinToString { "${it.key} ${it.value}" }}")
        append("Package hash: $packageHash")
    }
}

/**
 * Loads a `.agp` package: verifies `PACKAGE.lock`, parses the manifest, libraries, targets,
 * screens, flows, skills and commands, and runs the static checks described in
 * docs/vision/plugins.md. Any problem raises [PluginPackageException]; nothing partial is returned.
 */
object PluginLoader {

    private val VERB = Regex("[A-Z][A-Z0-9_]{1,15}")
    /** Verbs the base app handles itself on every channel */
    val RESERVED_VERBS = setOf("HELP", "STOP", "STATUS", "MORE", "RESEND", "OK", "LOGIN", "CANCEL")

    fun load(bytes: ByteArray, limits: PackageLimits = PackageLimits()): Plugin = load(PackageReader.read(bytes, limits))

    fun load(files: PackageFiles): Plugin {
        val yaml = YamlParser()
        fun doc(path: String): Map<String, Any?> = try {
            yaml.loadDocument(files.text(path) ?: throw PluginPackageException("Missing $path"))
        } catch (e: YamlParseException) {
            throw PluginPackageException("$path: ${e.message}")
        }

        val (lock, packageHash) = PackageLock.verify(files)
        val manifest = Manifest.parse(doc("plugin.yaml"))

        // ——— Libraries (vendored under lib/<id>/) ———
        val libIds = files.paths("lib/").map { it.split('/')[1] }.toSet()
        if (libIds != manifest.libraries.keys || lock.libraries != manifest.libraries) {
            throw PluginPackageException(
                "Libraries do not match: plugin.yaml ${manifest.libraries}, ${PackageLock.FILE} ${lock.libraries}, lib/ $libIds"
            )
        }
        val libTargets = mutableMapOf<String, Map<*, *>>()
        val libScreens = mutableMapOf<String, Any?>()
        val libFlows = mutableMapOf<String, Flow>()
        for (lib in libIds.sorted()) {
            val info = doc("lib/$lib/library.yaml")
            if (info["library"]?.toString() != lib || info["version"]?.toString() != manifest.libraries[lib]) {
                throw PluginPackageException("lib/$lib/library.yaml must declare library: $lib and version: ${manifest.libraries[lib]}")
            }
            val targets = named(files, "lib/$lib/targets/", ::doc).also { checkTargets(it, "lib/$lib/targets") }
            val screens = named(files, "lib/$lib/screens/", ::doc)
            val flowsRaw = flowFiles(files, "lib/$lib/flows/", ::doc).mapKeys { "$lib.${it.key}" }
            val parsed = parse("lib/$lib/flows") {
                DslParser(yaml, targets, screens).parseDocument(mapOf("flows" to flowsRaw))
            }
            libFlows += parsed.flows
            targets.forEach { (k, v) -> libTargets["$lib.$k"] = v }
            screens.forEach { (k, v) -> libScreens["$lib.$k"] = v }
        }

        // ——— Plugin content ———
        val targets = named(files, "targets/", ::doc).also { checkTargets(it, "targets") }
        val screens = named(files, "screens/", ::doc)
        val allTargets = targets + libTargets
        val allScreens = screens + libScreens
        duplicates(targets.keys, libTargets.keys)?.let { throw PluginPackageException("Target name clashes with a library: $it") }
        val flowsRaw = flowFiles(files, "flows/", ::doc)

        val skills = files.paths("skills/").associate { path ->
            val d = doc(path)
            val extra = d.keys - setOf("skill", "description", "params", "risk", "steps")
            if (extra.isNotEmpty()) throw PluginPackageException("$path: unknown keys $extra")
            val name = d["skill"]?.toString() ?: throw PluginPackageException("$path: missing 'skill' name")
            val risk = d["risk"]?.let { (it as? Number)?.toInt()?.takeIf { r -> r in 0..5 } ?: throw PluginPackageException("$path: risk must be 0–5") }
            val program = parse(path) {
                DslParser(yaml, allTargets, allScreens, libFlows).parseDocument(
                    mapOf("name" to name, "params" to d["params"], "flows" to flowsRaw, "steps" to d["steps"])
                )
            }
            checkStatic(program, manifest, path)
            name to Skill(name, d["description"]?.toString(), risk, program)
        }
        if (skills.size != files.paths("skills/").size) throw PluginPackageException("Two skill files declare the same skill name")
        if (skills.isEmpty()) throw PluginPackageException("A plugin needs at least one skill in skills/")
        // plugin flows are validated even if no skill calls them
        if (skills.isNotEmpty() && flowsRaw.isNotEmpty()) {
            parse("flows") { DslParser(yaml, allTargets, allScreens, libFlows).parseDocument(mapOf("flows" to flowsRaw)) }
        }

        // ——— Commands ———
        val floor = riskFloor(manifest)
        val commandsDoc = doc("commands.yaml")
        if (commandsDoc.keys != setOf("commands")) throw PluginPackageException("commands.yaml must have only a 'commands' list")
        val commandList = commandsDoc["commands"] as? List<*> ?: throw PluginPackageException("commands.yaml: 'commands' must be a list")
        val commands = linkedMapOf<String, Command>()
        commandList.forEachIndexed { i, raw ->
            val c = raw as? Map<*, *> ?: throw PluginPackageException("commands[$i] must be a mapping")
            val extra = c.keys.map { it.toString() } - setOf("verb", "skill", "args")
            if (extra.isNotEmpty()) throw PluginPackageException("commands[$i]: unknown keys $extra")
            val verb = c["verb"]?.toString()?.takeIf { VERB.matches(it) }
                ?: throw PluginPackageException("commands[$i].verb must be UPPERCASE letters/digits")
            if (verb in RESERVED_VERBS) throw PluginPackageException("commands[$i]: '$verb' is reserved by the base app")
            val skillName = c["skill"]?.toString() ?: throw PluginPackageException("commands[$i]: missing 'skill'")
            val skill = skills[skillName] ?: throw PluginPackageException("commands[$i]: unknown skill '$skillName'")
            if (commands.containsKey(verb)) throw PluginPackageException("commands: duplicate verb '$verb'")
            commands[verb] = Command(verb, skillName, c["args"]?.toString(), maxOf(floor, skill.declaredRisk ?: 0))
        }
        if (commands.isEmpty()) throw PluginPackageException("commands.yaml needs at least one command")

        return Plugin(
            manifest = manifest,
            packageHash = packageHash,
            commands = commands,
            skills = skills,
            targets = allTargets.keys,
            targetDefs = allTargets.mapValues { Target.parse(it.value) },
            screens = allScreens.keys,
            fixtures = files.paths("fixtures/"),
            readme = files.text("README.md")
        )
    }

    /**
     * Minimum risk level of every command (docs/vision/plugins.md section 9). A plugin can
     * declare a higher level per skill, never a lower one.
     */
    fun riskFloor(m: Manifest): Int = when {
        m.category == Category.FINANCIAL -> if (m.readOnly) 4 else 5
        m.secrets.isNotEmpty() || m.capabilities.deviceCredentialPrompt -> 4
        m.category == Category.MESSAGING -> 2
        else -> 1
    }

    // ——— Helpers ———

    /** Merges `name: definition` mappings from every YAML file under [prefix]; duplicate names fail. */
    private fun named(files: PackageFiles, prefix: String, doc: (String) -> Map<String, Any?>): Map<String, Map<*, *>> {
        val out = linkedMapOf<String, Map<*, *>>()
        for (path in files.paths(prefix)) {
            for ((k, v) in doc(path)) {
                if (out.containsKey(k)) throw PluginPackageException("$path: '$k' is defined twice")
                out[k] = v as? Map<*, *> ?: throw PluginPackageException("$path: '$k' must be a mapping")
            }
        }
        return out
    }

    /** One flow per file: `flow: name`, optional `params`, `steps` */
    private fun flowFiles(files: PackageFiles, prefix: String, doc: (String) -> Map<String, Any?>): Map<String, Map<String, Any?>> {
        val out = linkedMapOf<String, Map<String, Any?>>()
        for (path in files.paths(prefix)) {
            val d = doc(path)
            val extra = d.keys - setOf("flow", "params", "steps")
            if (extra.isNotEmpty()) throw PluginPackageException("$path: unknown keys $extra")
            val name = d["flow"]?.toString() ?: throw PluginPackageException("$path: missing 'flow' name")
            if (out.containsKey(name)) throw PluginPackageException("$path: flow '$name' is defined twice")
            out[name] = mapOf("params" to d["params"], "steps" to d["steps"])
        }
        return out
    }

    private fun checkTargets(targets: Map<String, Map<*, *>>, where: String) = targets.forEach { (name, raw) ->
        try {
            Target.parse(raw)
        } catch (e: IllegalArgumentException) {
            throw PluginPackageException("$where: target '$name': ${e.message}")
        }
    }

    private fun duplicates(a: Set<String>, b: Set<String>): Set<String>? = (a intersect b).takeIf { it.isNotEmpty() }

    private fun <T> parse(where: String, block: () -> T): T = try {
        block()
    } catch (e: YamlParseException) {
        throw PluginPackageException("$where: ${e.message}")
    }

    /** Checks that can be done before running: launch targets and that UI actions have an app to act on */
    private fun checkStatic(program: Program, m: Manifest, where: String) {
        val operable = m.capabilities.uiAutomation
        fun walk(nodes: List<Node>) {
            for (n in nodes) when (n) {
                is Node.Action -> {
                    val step = n.step
                    if (step.action == ActionType.LAUNCH_APP) {
                        val pkg = step.parameters["package"] as? String
                        if (pkg != null && !pkg.contains("\${") && pkg !in operable) {
                            throw PluginPackageException("$where: launch_app '$pkg' is not in capabilities.ui_automation")
                        }
                    }
                    if (step.action == ActionType.OPEN_URL && m.capabilities.deeplinks.isEmpty()) {
                        throw PluginPackageException("$where: open_url needs capabilities.deeplinks")
                    }
                    if (step.action in CapabilityGuard.UI_ACTIONS && operable.isEmpty()) {
                        throw PluginPackageException("$where: ${step.action.yamlValue} needs capabilities.ui_automation")
                    }
                }
                is Node.Sequence -> walk(n.nodes)
                is Node.If -> { walk(n.then); walk(n.otherwise) }
                is Node.FirstThatWorks -> walk(n.alternatives)
                is Node.Try -> { walk(n.body); walk(n.onError) }
                else -> {}
            }
        }
        walk(program.body)
        program.flows.values.forEach { walk(it.body) }
    }
}
