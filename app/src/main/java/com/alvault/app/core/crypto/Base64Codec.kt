package com.alvault.app.core.crypto

import java.util.Base64

/**
 * Base64 standar (alfabet `+` `/`, padding `=`), identik dengan `btoa`/`atob` pada aplikasi web.
 * Validasi memakai regex dari spesifikasi: `^[a-zA-Z0-9\/\r\n+]*={0,2}$`.
 */
object Base64Codec {
    private val VALID = Regex("^[a-zA-Z0-9/\\r\\n+]*={0,2}$")

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun isValid(text: String): Boolean = VALID.matches(text)

    /** @throws CryptoException jika string bukan Base64 yang valid. */
    fun decode(text: String): ByteArray {
        if (!isValid(text)) throw CryptoException("Data bukan Base64 yang valid.")
        return try {
            // MIME decoder mengabaikan \r\n yang diizinkan oleh regex spesifikasi.
            Base64.getMimeDecoder().decode(text)
        } catch (e: IllegalArgumentException) {
            throw CryptoException("Data bukan Base64 yang valid.", e)
        }
    }
}
