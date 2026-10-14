package com.alvault.app.core.security

import java.security.SecureRandom

data class GeneratorOptions(
    val length: Int = PasswordGenerator.DEFAULT_LENGTH,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val numbers: Boolean = true,
    val safeSymbols: Boolean = true,
    val extendedSymbols: Boolean = false
) {
    val enabledSets: List<String>
        get() = buildList {
            if (upper) add(PasswordGenerator.UPPER)
            if (lower) add(PasswordGenerator.LOWER)
            if (numbers) add(PasswordGenerator.NUMBERS)
            if (safeSymbols) add(PasswordGenerator.SAFE_SYMBOLS)
            if (extendedSymbols) add(PasswordGenerator.EXTENDED_SYMBOLS)
        }
}

/**
 * Generator password berbasis CSPRNG dengan rejection sampling:
 * byte acak >= (256 - 256 % n) dibuang agar tidak ada bias modulo.
 */
object PasswordGenerator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 42
    const val DEFAULT_LENGTH = 16

    const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    const val NUMBERS = "0123456789"
    const val SAFE_SYMBOLS = "@#!?"
    const val EXTENDED_SYMBOLS = "^&*()_+~`|}{[]:;<>,./-="

    private val defaultRandom = SecureRandom()

    /**
     * Menghasilkan password dengan minimal satu karakter dari setiap set yang aktif,
     * lalu mengacak urutannya (Fisher-Yates dengan rejection sampling).
     */
    fun generate(options: GeneratorOptions, random: SecureRandom = defaultRandom): String {
        val sets = options.enabledSets
        require(sets.isNotEmpty()) { "Pilih minimal satu jenis karakter." }
        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        require(length >= sets.size) { "Panjang terlalu pendek untuk set karakter yang dipilih." }

        val pool = sets.joinToString("")
        val chars = CharArray(length)
        // Jaminan minimal satu karakter per set.
        sets.forEachIndexed { index, set -> chars[index] = set[randomIndex(set.length, random)] }
        for (i in sets.size until length) chars[i] = pool[randomIndex(pool.length, random)]
        // Acak posisi.
        for (i in chars.size - 1 downTo 1) {
            val j = randomIndex(i + 1, random)
            val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        }
        return String(chars)
    }

    /** Indeks acak seragam di [0, bound) dengan rejection sampling pada byte (bound <= 256). */
    fun randomIndex(bound: Int, random: SecureRandom = defaultRandom): Int {
        require(bound in 1..256) { "bound harus 1..256" }
        val limit = 256 - (256 % bound) // nilai >= limit ditolak (menghilangkan bias modulo)
        val buffer = ByteArray(1)
        while (true) {
            random.nextBytes(buffer)
            val value = buffer[0].toInt() and 0xFF
            if (value < limit) return value % bound
        }
    }
}
