package com.alvault.app.core.crypto

import java.util.Locale

/** Abstraksi Argon2id agar logika pemisahan kunci bisa diuji tanpa pustaka native. */
interface Argon2Hasher {
    fun hash(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        memoryKib: Int,
        parallelism: Int,
        outputLength: Int
    ): ByteArray
}

/**
 * Hasil derivasi master key (64 byte) setelah pemisahan kunci:
 * - [encryptionKey]  = byte 0..31  (AES-256-GCM, hanya di RAM)
 * - [localAuthHash]  = Base64(byte 32..63) (untuk verifikasi login lokal)
 */
class MasterKeys(val encryptionKey: ByteArray, val localAuthHash: String) {
    fun wipe() = Bytes.wipe(encryptionKey)
}

object MasterKeyDeriver {
    const val ARGON2_ITERATIONS = 3
    const val ARGON2_MEMORY_KIB = 65_536 // 64 MB
    const val ARGON2_PARALLELISM = 1
    const val OUTPUT_LENGTH = 64

    fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    /** Salt Argon2id = UTF-8("{email}:{localSalt}"). */
    fun argonSalt(email: String, localSalt: String): ByteArray =
        Bytes.utf8("${normalizeEmail(email)}:$localSalt")

    fun derive(
        hasher: Argon2Hasher,
        email: String,
        masterPassword: CharArray,
        localSalt: String,
        iterations: Int = ARGON2_ITERATIONS
    ): MasterKeys {
        val passwordBytes = Bytes.utf8(masterPassword)
        val output = try {
            hasher.hash(
                password = passwordBytes,
                salt = argonSalt(email, localSalt),
                iterations = iterations,
                memoryKib = ARGON2_MEMORY_KIB,
                parallelism = ARGON2_PARALLELISM,
                outputLength = OUTPUT_LENGTH
            )
        } finally {
            Bytes.wipe(passwordBytes)
        }
        try {
            check(output.size == OUTPUT_LENGTH) { "Output Argon2id harus $OUTPUT_LENGTH byte" }
            val encryptionKey = output.copyOfRange(0, 32)
            val localAuthHash = Base64Codec.encode(output.copyOfRange(32, 64))
            return MasterKeys(encryptionKey, localAuthHash)
        } finally {
            Bytes.wipe(output)
        }
    }
}
