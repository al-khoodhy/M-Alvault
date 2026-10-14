package com.alvault.app.core.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-HMAC-SHA256 (standar WebCrypto). Password di-encode UTF-8 oleh provider JCA. */
object Pbkdf2 {
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = AegisCrypto.KDF_ITERATIONS,
        keyBits: Int = 256
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyBits)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } catch (e: Exception) {
            throw CryptoException("Gagal menurunkan kunci (PBKDF2).", e)
        } finally {
            spec.clearPassword()
        }
    }
}
