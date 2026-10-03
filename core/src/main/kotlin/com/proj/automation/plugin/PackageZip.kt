package com.proj.automation.plugin

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Reproducible zip writing: sorted entries, a fixed timestamp, maximum compression. */
internal object PackageZip {

    /** 1980-02-01 in local DOS time; any fixed value works, this one round-trips through zip */
    private const val FIXED_TIME = 315_532_800_000L + 31L * 24 * 3600 * 1000

    fun write(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setLevel(9)
            for ((path, bytes) in entries.toSortedMap()) {
                zip.putNextEntry(ZipEntry(path).apply { time = FIXED_TIME })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
