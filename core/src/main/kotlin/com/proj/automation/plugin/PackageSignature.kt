package com.proj.automation.plugin

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Signature state of a package (ADR-009). */
sealed class SignatureStatus {
    object Unsigned : SignatureStatus()
    data class Valid(val fingerprint: String, val publicKey: ByteArray) : SignatureStatus() {
        override fun equals(other: Any?) = other is Valid && other.fingerprint == fingerprint
        override fun hashCode() = fingerprint.hashCode()
    }
}

/**
 * `PACKAGE.sig`: an ECDSA P-256 signature over the package hash (the SHA-256 of
 * `PACKAGE.lock`, which covers every file). Format, one field per line:
 * ```
 * agp-sig 1
 * algorithm ECDSA-P256-SHA256
 * public-key <base64 X.509 SubjectPublicKeyInfo>
 * signature <base64 DER signature>
 * ```
 */
object PackageSignature {

    const val FILE = "PACKAGE.sig"
    private const val HEADER = "agp-sig 1"
    private const val ALGORITHM = "ECDSA-P256-SHA256"
    private const val JCA_ALGORITHM = "SHA256withECDSA"

    /** Domain-separated message: a signature for anything else can never verify here */
    private fun message(packageHash: String) = "agp-package-v1\n$packageHash".toByteArray()

    fun sign(packageHash: String, privateKey: PrivateKey, publicKey: PublicKey): String {
        val signature = Signature.getInstance(JCA_ALGORITHM).run {
            initSign(privateKey)
            update(message(packageHash))
            sign()
        }
        val b64 = Base64.getEncoder()
        return "$HEADER\nalgorithm $ALGORITHM\npublic-key ${b64.encodeToString(publicKey.encoded)}\nsignature ${b64.encodeToString(signature)}\n"
    }

    /** Adds (or replaces) `PACKAGE.sig` in a built package; the package hash does not change. */
    fun signPackage(bytes: ByteArray, privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val files = PackageReader.read(bytes)
        val (_, packageHash) = PackageLock.verify(files)
        val entries = files.entries.toMutableMap()
        entries[FILE] = sign(packageHash, privateKey, publicKey).toByteArray()
        return PackageZip.write(entries)
    }

    /**
     * Checks `PACKAGE.sig` against [packageHash]. A malformed or non-matching signature throws
     * [PluginPackageException]: a broken signature means an altered package and is never a warning.
     */
    fun verify(sigText: String?, packageHash: String): SignatureStatus {
        if (sigText == null) return SignatureStatus.Unsigned
        val fields = sigText.lines().filter { it.isNotBlank() }
        if (fields.firstOrNull() != HEADER) throw PluginPackageException("$FILE: unsupported format")
        val map = fields.drop(1).associate { line ->
            val parts = line.split(' ', limit = 2)
            if (parts.size != 2) throw PluginPackageException("$FILE: invalid line")
            parts[0] to parts[1]
        }
        if (map["algorithm"] != ALGORITHM) throw PluginPackageException("$FILE: unsupported algorithm ${map["algorithm"]}")
        val keyBytes: ByteArray
        val sigBytes: ByteArray
        val publicKey: PublicKey
        try {
            keyBytes = Base64.getDecoder().decode(map["public-key"] ?: throw PluginPackageException("$FILE: missing public-key"))
            sigBytes = Base64.getDecoder().decode(map["signature"] ?: throw PluginPackageException("$FILE: missing signature"))
            publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
        } catch (e: IllegalArgumentException) {
            throw PluginPackageException("$FILE: invalid encoding")
        } catch (e: java.security.GeneralSecurityException) {
            throw PluginPackageException("$FILE: invalid public key")
        }
        val ok = try {
            Signature.getInstance(JCA_ALGORITHM).run {
                initVerify(publicKey)
                update(message(packageHash))
                verify(sigBytes)
            }
        } catch (e: java.security.GeneralSecurityException) {
            false
        }
        if (!ok) throw PluginPackageException("$FILE: the signature does not match this package (altered or wrongly signed)")
        return SignatureStatus.Valid(fingerprint(keyBytes), keyBytes)
    }

    /** SHA-256 of the encoded public key, hex, in groups of four for reading aloud */
    fun fingerprint(encodedPublicKey: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(encodedPublicKey)
            .joinToString("") { "%02X".format(it) }
            .chunked(4).joinToString(" ")
}
