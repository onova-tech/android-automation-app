package com.proj.automation.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * The code-sheet key lives in the Android Keystore and cannot be exported: the phone can compute
 * codes, but the key itself never leaves secure storage (specs/003-command-channel, FR-005).
 */
object KeystoreKeys {

    private const val PROVIDER = "AndroidKeyStore"
    private fun alias(sheetId: Int) = "code-sheet-$sheetId"

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    /** HMAC function backed by the Keystore key for [sheetId]; creates the key if needed. */
    fun codeSheetMac(sheetId: Int): (ByteArray) -> ByteArray {
        val key = (keyStore().getKey(alias(sheetId), null) as? SecretKey) ?: generate(sheetId)
        return { data -> Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(data) }
    }

    /** Replaces the key: every code of the previous sheet stops working. */
    fun newSheetKey(oldSheetId: Int, newSheetId: Int) {
        keyStore().deleteEntry(alias(oldSheetId))
        generate(newSheetId)
    }

    private fun generate(sheetId: Int): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, PROVIDER)
        generator.init(KeyGenParameterSpec.Builder(alias(sheetId), KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }
}
