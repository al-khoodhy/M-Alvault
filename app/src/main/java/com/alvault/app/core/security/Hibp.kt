package com.alvault.app.core.security

import com.alvault.app.core.crypto.Bytes
import java.security.MessageDigest

/**
 * Bagian murni (tanpa jaringan) dari HIBP Pwned Passwords k-anonymity:
 * hanya 5 karakter pertama SHA-1 yang pernah dikirim ke server.
 */
object Hibp {
    const val PREFIX_LENGTH = 5

    /** SHA-1(password) dalam hex huruf besar. */
    fun sha1Hex(password: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(Bytes.utf8(password))
        return Bytes.toHex(digest, uppercase = true)
    }

    fun prefix(hash: String): String = hash.substring(0, PREFIX_LENGTH)

    fun suffix(hash: String): String = hash.substring(PREFIX_LENGTH)

    /**
     * Mencari [suffix] pada respons `{SUFFIX}:{COUNT}` per baris.
     * Mengembalikan jumlah kebocoran (0 bila tidak ada). Baris padding (count 0) tidak dihitung.
     */
    fun breachCount(responseBody: String, suffix: String): Int {
        val wanted = suffix.uppercase()
        for (rawLine in responseBody.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            if (line.substring(0, separator).uppercase() == wanted) {
                return line.substring(separator + 1).trim().toIntOrNull() ?: 0
            }
        }
        return 0
    }
}
