package com.proj.automation.plugin

import com.proj.automation.parser.YamlParser
import java.io.File

/**
 * Builds a `.agp` from a plugin source folder (docs/vision/plugins.md section 4.5):
 * vendors the libraries named in `plugin.yaml` from a libraries folder, writes `PACKAGE.lock`,
 * and produces a reproducible zip (sorted entries, fixed timestamps), so the same sources
 * always give the same bytes and the same package hash. The result is loaded once to make
 * sure it would install.
 */
object PackageBuilder {

    data class Result(val bytes: ByteArray, val plugin: Plugin)

    /** @param signingKey when given, the package is signed with it (ADR-009) */
    fun build(pluginDir: File, librariesDir: File? = null, signingKey: java.security.KeyPair? = null): Result {
        if (!pluginDir.isDirectory) throw PluginPackageException("Not a folder: $pluginDir")
        val entries = sortedMapOf<String, ByteArray>()

        collect(pluginDir).forEach { (path, bytes) ->
            if (path == PackageLock.FILE || path == PackageSignature.FILE || path.startsWith("lib/")) return@forEach // regenerated / vendored / signed later
            entries[path] = bytes
        }

        val manifestText = entries["plugin.yaml"]?.toString(Charsets.UTF_8)
            ?: throw PluginPackageException("$pluginDir has no plugin.yaml")
        val manifest = Manifest.parse(YamlParser().loadDocument(manifestText))
        for ((lib, version) in manifest.libraries) {
            val dir = librariesDir?.resolve(lib)
            if (dir == null || !dir.isDirectory) throw PluginPackageException("Library '$lib' not found in ${librariesDir ?: "(no libraries folder given)"}")
            val files = collect(dir)
            val info = files["library.yaml"]?.toString(Charsets.UTF_8)
                ?.let { YamlParser().loadDocument(it) }
                ?: throw PluginPackageException("$dir has no library.yaml")
            if (info["version"]?.toString() != version) {
                throw PluginPackageException("Library '$lib' is version ${info["version"]}, plugin.yaml asks for $version")
            }
            files.forEach { (path, bytes) -> entries["lib/$lib/$path"] = bytes }
        }

        entries[PackageLock.FILE] = PackageLock.forFiles(entries, manifest.libraries).render().toByteArray()
        entries.keys.forEach { PackageReader.checkPath(it); PackageReader.checkAllowed(it) }

        val unsigned = PackageZip.write(entries)
        val bytes = signingKey?.let { PackageSignature.signPackage(unsigned, it.private, it.public) } ?: unsigned
        val plugin = PluginLoader.load(bytes)
        return Result(bytes, plugin)
    }

    /** Regular files under [dir], by relative path with '/' separators; hidden files are skipped */
    private fun collect(dir: File): Map<String, ByteArray> =
        dir.walkTopDown()
            .onEnter { it == dir || !it.name.startsWith(".") }
            .filter { it.isFile && !it.name.startsWith(".") }
            .associate { it.relativeTo(dir).invariantSeparatorsPath to it.readBytes() }
}
