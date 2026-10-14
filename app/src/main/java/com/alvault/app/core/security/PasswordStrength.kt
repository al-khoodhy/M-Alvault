package com.alvault.app.core.security

import java.util.Locale
import kotlin.math.ln

enum class Strength { VERY_WEAK, WEAK, FAIR, STRONG, VERY_STRONG }

/**
 * Pemeriksaan lokal (offline) untuk password lemah: terlalu pendek (< 8), password umum,
 * karakter berulang, urutan sederhana, dan pola keyboard.
 */
object PasswordStrength {
    const val MIN_LENGTH = 8

    /** Daftar kecil password paling umum (Inggris & Indonesia). Dicocokkan tanpa memedulikan huruf besar/kecil. */
    private val COMMON = setOf(
        "123456", "1234567", "12345678", "123456789", "1234567890", "12345", "1234", "111111", "000000",
        "654321", "123123", "112233", "121212", "123321", "666666", "7777777", "987654321", "159753",
        "password", "passw0rd", "p@ssw0rd", "p@ssword", "pass", "qwerty", "qwertyuiop", "qwerty123",
        "abc123", "a123456", "123456a", "iloveyou", "admin", "admin123", "administrator", "welcome",
        "letmein", "monkey", "dragon", "master", "login", "princess", "sunshine", "shadow", "football",
        "baseball", "superman", "batman", "trustno1", "freedom", "whatever", "hello", "hello123",
        "1q2w3e4r", "1qaz2wsx", "zaq12wsx", "qazwsx", "asdfgh", "asdfghjkl", "zxcvbnm", "zxcvbn",
        "rahasia", "sayang", "bismillah", "indonesia", "kucing", "cinta", "jakarta", "123qwe",
        "qwe123", "password1", "password123", "pass123", "test", "test123", "user", "guest", "root", "toor"
    )

    private val KEYBOARD_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890", "qazwsxedcrfv")

    fun isCommon(password: String): Boolean {
        val lower = password.lowercase(Locale.ROOT)
        if (lower in COMMON) return true
        // Varian "password" + angka/simbol di ujung, mis. "password2024!".
        val trimmed = lower.trimEnd { it.isDigit() || it in "!@#$%^&*._-" }
        return trimmed.length >= 4 && trimmed in COMMON
    }

    /** true bila password dianggap lemah secara lokal (tanpa jaringan). */
    fun isWeakLocal(password: String): Boolean {
        if (password.length < MIN_LENGTH) return true
        if (isCommon(password)) return true
        if (password.all { it == password[0] }) return true
        if (isSequential(password)) return true
        if (isKeyboardPattern(password)) return true
        if (isRepeatedBlock(password)) return true
        return false
    }

    /** Perkiraan entropi (bit) berdasarkan variasi karakter dan panjang; bukan jaminan keamanan. */
    fun estimateBits(password: String): Double {
        if (password.isEmpty()) return 0.0
        var pool = 0
        if (password.any { it in 'a'..'z' }) pool += 26
        if (password.any { it in 'A'..'Z' }) pool += 26
        if (password.any { it in '0'..'9' }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 33
        if (pool == 0) pool = 26
        val uniqueRatio = password.toSet().size.toDouble() / password.length
        val bits = password.length * (ln(pool.toDouble()) / ln(2.0)) * (0.5 + 0.5 * uniqueRatio)
        return bits
    }

    fun score(password: String): Strength {
        if (password.isEmpty()) return Strength.VERY_WEAK
        if (isWeakLocal(password)) {
            return if (password.length < MIN_LENGTH || isCommon(password)) Strength.VERY_WEAK else Strength.WEAK
        }
        val bits = estimateBits(password)
        return when {
            bits < 40 -> Strength.WEAK
            bits < 60 -> Strength.FAIR
            bits < 85 -> Strength.STRONG
            else -> Strength.VERY_STRONG
        }
    }

    private fun isSequential(password: String): Boolean {
        if (password.length < 4) return false
        val codes = password.lowercase(Locale.ROOT).map { it.code }
        val step = codes[1] - codes[0]
        if (step != 1 && step != -1) return false
        return codes.zipWithNext().all { (a, b) -> b - a == step }
    }

    private fun isKeyboardPattern(password: String): Boolean {
        val lower = password.lowercase(Locale.ROOT)
        if (lower.length < 6) return false
        return KEYBOARD_ROWS.any { row -> row.contains(lower) || row.reversed().contains(lower) }
    }

    private fun isRepeatedBlock(password: String): Boolean {
        val n = password.length
        for (block in 1..n / 2) {
            if (n % block != 0) continue
            val unit = password.substring(0, block)
            if (unit.repeat(n / block) == password) return true
        }
        return false
    }
}
