package com.alvault.app.core.crypto

/** Isi mentah berkas `.aegis` setelah Base64 di-decode. */
class AegisPayload(
    val version: Int,
    val salt: ByteArray,
    val iv: ByteArray,
    /** Ciphertext || GCM tag (16 byte). */
    val ciphertext: ByteArray
)

/**
 * Enkripsi/dekripsi backup `.aegis`:
 * PBKDF2-HMAC-SHA256 (600.000 iterasi, salt 16B) -> AES-256-GCM (IV 12B, tag 128 bit).
 * Parameter wajib identik dengan aplikasi web.
 */
object AegisCrypto {
    const val VERSION = 1
    const val KDF_ITERATIONS = 600_000
    const val SALT_SIZE = 16
    const val IV_SIZE = AesGcm.IV_SIZE

    fun encrypt(
        plaintext: ByteArray,
        password: CharArray,
        iterations: Int = KDF_ITERATIONS,
        salt: ByteArray = AesGcm.randomBytes(SALT_SIZE),
        iv: ByteArray = AesGcm.randomBytes(IV_SIZE)
    ): AegisPayload {
        val key = Pbkdf2.deriveKey(password, salt, iterations)
        try {
            return AegisPayload(VERSION, salt, iv, AesGcm.encrypt(key, iv, plaintext))
        } finally {
            Bytes.wipe(key)
        }
    }

    /** @throws CryptoException jika password salah atau berkas rusak. */
    fun decrypt(payload: AegisPayload, password: CharArray, iterations: Int = KDF_ITERATIONS): ByteArray {
        if (payload.version != VERSION) {
            throw CryptoException("Versi berkas .aegis (${payload.version}) tidak didukung.")
        }
        val key = Pbkdf2.deriveKey(password, payload.salt, iterations)
        try {
            return AesGcm.decrypt(key, payload.iv, payload.ciphertext)
        } finally {
            Bytes.wipe(key)
        }
    }
}
