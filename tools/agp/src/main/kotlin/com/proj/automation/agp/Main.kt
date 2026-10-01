package com.proj.automation.agp

import com.proj.automation.plugin.PackageBuilder
import com.proj.automation.plugin.PluginLoader
import com.proj.automation.plugin.PluginPackageException
import com.proj.automation.plugin.Plugin
import com.proj.automation.resolve.Resolution
import com.proj.automation.resolve.TargetResolver
import com.proj.automation.ui.UiXml
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """agp — plugin tool for the Android automation runtime

Usage:
  agp validate <plugin-dir> [--libs <dir>]          check a plugin folder and print its install summary
  agp build <plugin-dir> [--libs <dir>] -o <file>   build a reproducible .agp package
  agp inspect <file.agp>                            verify a package and print its install summary
  agp targets <plugin-dir|file.agp> <dump.xml> [--libs <dir>]
                                                    resolve every named target against a
                                                    `uiautomator dump` of a real screen
"""

fun main(args: Array<String>) {
    val code = try {
        run(args.toList())
    } catch (e: PluginPackageException) {
        System.err.println("error: ${e.message}")
        1
    } catch (e: UsageException) {
        System.err.println("error: ${e.message}\n\n$USAGE")
        2
    }
    exitProcess(code)
}

private class UsageException(message: String) : Exception(message)

internal fun run(args: List<String>): Int {
    val command = args.firstOrNull() ?: throw UsageException("missing command")
    val rest = args.drop(1)
    val libs = option(rest, "--libs")?.let(::File)
    val positional = positional(rest, setOf("--libs", "-o"))

    when (command) {
        "validate" -> {
            val plugin = PackageBuilder.build(dir(positional, 0), libs).plugin
            println(plugin.installSummary())
            println("\nOK")
        }
        "build" -> {
            val out = option(rest, "-o")?.let(::File) ?: throw UsageException("build needs -o <file>")
            val result = PackageBuilder.build(dir(positional, 0), libs)
            out.absoluteFile.parentFile.mkdirs()
            out.writeBytes(result.bytes)
            println(result.plugin.installSummary())
            println("\nWrote ${out.path} (${result.bytes.size} bytes)")
        }
        "inspect" -> {
            val file = positional.getOrNull(0)?.let(::File) ?: throw UsageException("inspect needs a .agp file")
            println(PluginLoader.load(file.readBytes()).installSummary())
        }
        "targets" -> {
            val source = positional.getOrNull(0)?.let(::File) ?: throw UsageException("targets needs a plugin folder or .agp")
            val dump = positional.getOrNull(1)?.let(::File) ?: throw UsageException("targets needs a dump.xml")
            val plugin = if (source.isDirectory) PackageBuilder.build(source, libs).plugin else PluginLoader.load(source.readBytes())
            return checkTargets(plugin, dump)
        }
        "help", "--help", "-h" -> println(USAGE)
        else -> throw UsageException("unknown command '$command'")
    }
    return 0
}

/** Prints how each named target resolves on a real screen; exit code 1 if any fails */
private fun checkTargets(plugin: Plugin, dump: File): Int {
    val screen = UiXml.parse(dump.readText())
    val resolver = TargetResolver()
    var failures = 0
    for ((name, target) in plugin.targetDefs.toSortedMap()) {
        if (target.strings().any { it.contains("\${") }) {
            println("~ $name: skipped (uses variables)")
            continue
        }
        when (val r = resolver.resolve(target, screen)) {
            is Resolution.Found -> println("✓ $name: ${r.stage} %.2f → ${r.node}".format(r.confidence))
            is Resolution.NotFound -> { failures++; println("✗ $name: not found (${r.reason})") }
            is Resolution.Ambiguous -> {
                failures++
                println("? $name: ambiguous (${r.reason})")
                r.top.forEach { (n, s) -> println("    %.2f $n".format(s)) }
            }
        }
    }
    println("\n${plugin.targetDefs.size - failures} of ${plugin.targetDefs.size} targets resolved on ${dump.name}")
    return if (failures == 0) 0 else 1
}

private fun option(args: List<String>, name: String): String? {
    val i = args.indexOf(name)
    if (i < 0) return null
    return args.getOrNull(i + 1) ?: throw UsageException("$name needs a value")
}

private fun positional(args: List<String>, withValue: Set<String>): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        if (args[i] in withValue) i += 2 else out += args[i++]
    }
    return out
}

private fun dir(positional: List<String>, index: Int): File =
    positional.getOrNull(index)?.let(::File)?.takeIf { it.isDirectory }
        ?: throw UsageException("expected a plugin folder")
