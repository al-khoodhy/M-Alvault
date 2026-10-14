package com.alvault.app.core.crypto

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

/** Kegagalan kripto yang aman ditampilkan ke pengguna (tanpa membocorkan detail rahasia). */
class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Utilitas byte/char: konversi UTF-8 tanpa meninggalkan salinan String, dan penghapusan memori. */
object Bytes {
    /** Mengubah CharArray (password) menjadi byte UTF-8, sama dengan TextEncoder pada WebCrypto. */
    fun utf8(chars: CharArray): ByteArray {
        val buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val out = ByteArray(buffer.remaining())
        buffer.get(out)
        // Hapus buffer sementara yang dibuat encoder.
        if (buffer.hasArray()) buffer.array().fill(0)
        return out
    }

    fun utf8(text: String): ByteArray = text.toByteArray(StandardCharsets.UTF_8)

    fun wipe(array: ByteArray?) {
        array?.fill(0)
    }

    fun wipe(array: CharArray?) {
        array?.fill('\u0000')
    }

    fun toHex(bytes: ByteArray, uppercase: Boolean = false): String {
        val digits = if (uppercase) "0123456789ABCDEF" else "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(digits[v ushr 4]).append(digits[v and 0x0F])
        }
        return sb.toString()
    }
}
