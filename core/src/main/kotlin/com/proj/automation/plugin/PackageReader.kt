package com.proj.automation.plugin

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class PluginPackageException(message: String) : Exception(message)

/** Limits applied while reading a package, before any content is interpreted */
data class PackageLimits(
    val maxEntries: Int = 500,
    val maxFileBytes: Int = 512 * 1024,
    val maxTotalBytes: Long = 8L * 1024 * 1024
)

/** The files of a plugin package, by normalized path. Nothing is ever written to disk. */
class PackageFiles(val entries: Map<String, ByteArray>) {
    fun text(path: String): String? = entries[path]?.toString(Charsets.UTF_8)
    fun paths(prefix: String): List<String> = entries.keys.filter { it.startsWith(prefix) }.sorted()
}

/**
 * Reads a `.agp` zip into memory with the protections listed in docs/vision/plugins.md
 * (section 11): no path traversal or absolute paths, no duplicate or case-colliding names,
 * bounded entry count and sizes (counted on decompressed bytes, which defeats zip bombs),
 * and only the file types a plugin may contain.
 */
object PackageReader {

    fun read(bytes: ByteArray, limits: PackageLimits = PackageLimits()): PackageFiles =
        read(ByteArrayInputStream(bytes), limits)

    fun read(input: InputStream, limits: PackageLimits = PackageLimits()): PackageFiles {
        val entries = linkedMapOf<String, ByteArray>()
        val lowerNames = mutableSetOf<String>()
        var total = 0L
        var count = 0

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > limits.maxEntries) throw PluginPackageException("More than ${limits.maxEntries} entries")
                val path = checkPath(entry.name)
                if (entry.isDirectory) continue
                if (!lowerNames.add(path.lowercase())) throw PluginPackageException("Duplicate entry '$path'")
                checkAllowed(path)

                val data = readBounded(zip, limits.maxFileBytes, path)
                total += data.size
                if (total > limits.maxTotalBytes) throw PluginPackageException("Package larger than ${limits.maxTotalBytes} bytes")
                entries[path] = data
            }
        }
        if (entries.isEmpty()) throw PluginPackageException("Not a plugin package (empty or not a zip)")
        return PackageFiles(entries)
    }

    private fun readBounded(input: InputStream, max: Int, path: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > max) throw PluginPackageException("'$path' is larger than $max bytes")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    internal fun checkPath(name: String): String {
        if (name.isEmpty() || name.length > 200) throw PluginPackageException("Invalid entry name")
        if (name.startsWith("/") || name.contains('\\') || name.contains(':') || name.contains('\u0000')) {
            throw PluginPackageException("Invalid entry path '$name'")
        }
        val parts = name.trimEnd('/').split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.startsWith(".") }) {
            throw PluginPackageException("Invalid entry path '$name'")
        }
        return name.trimEnd('/')
    }

    private val YAML_DIRS = setOf("skills", "flows", "targets", "screens", "tests")
    private val LIB_DIRS = setOf("flows", "targets", "screens")
    private val RESERVED = setOf("i18n")

    /** Allowed layout (plugins.md section 4.2); everything else is rejected */
    internal fun checkAllowed(path: String) {
        val parts = path.split('/')
        val ext = path.substringAfterLast('.', "")
        val ok = when {
            parts[0] in RESERVED ->
                throw PluginPackageException("'$path': ${parts[0]} is reserved for a later version and not supported yet")
            parts.size == 1 -> path in setOf("plugin.yaml", "commands.yaml", "interrupts.yaml", "README.md", PackageLock.FILE)
            parts.size == 2 && parts[0] in YAML_DIRS -> ext == "yaml"
            parts.size == 2 && parts[0] == "fixtures" -> ext == "json" || ext == "xml"
            parts[0] == "lib" && parts.size == 3 -> parts[2] == "library.yaml" || parts[2] == "README.md"
            parts[0] == "lib" && parts.size == 4 -> parts[2] in LIB_DIRS && ext == "yaml"
            else -> false
        }
        if (!ok) throw PluginPackageException("File not allowed in a plugin package: '$path'")
    }
}
