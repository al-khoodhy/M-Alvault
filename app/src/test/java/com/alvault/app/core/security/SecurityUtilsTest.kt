package com.alvault.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PasswordGeneratorTest {

    @Test
    fun charsetSizesMatchSpecification() {
        assertEquals(26, PasswordGenerator.UPPER.length)
        assertEquals(26, PasswordGenerator.LOWER.length)
        assertEquals(10, PasswordGenerator.NUMBERS.length)
        assertEquals("@#!?", PasswordGenerator.SAFE_SYMBOLS)
    }

    @Test
    fun generate_respectsLengthAndCharsets() {
        val combos = listOf(
            GeneratorOptions(),
            GeneratorOptions(upper = false),
            GeneratorOptions(lower = false, numbers = false),
            GeneratorOptions(extendedSymbols = true),
            GeneratorOptions(upper = false, lower = false, numbers = false, safeSymbols = false, extendedSymbols = true),
            GeneratorOptions(length = PasswordGenerator.MIN_LENGTH, extendedSymbols = true),
            GeneratorOptions(length = PasswordGenerator.MAX_LENGTH, extendedSymbols = true)
        )
        for (options in combos) {
            repeat(200) {
                val password = PasswordGenerator.generate(options)
                assertEquals(options.length, password.length)
                val sets = options.enabledSets
                val allowed = sets.joinToString("")
                assertTrue(password.all { it in allowed })
                // minimal satu karakter dari tiap set aktif
                for (set in sets) assertTrue(password.any { it in set })
            }
        }
    }

    @Test
    fun generate_clampsLengthIntoRange() {
        assertEquals(8, PasswordGenerator.generate(GeneratorOptions(length = 1)).length)
        assertEquals(42, PasswordGenerator.generate(GeneratorOptions(length = 500)).length)
    }

    @Test
    fun generate_requiresAtLeastOneSet() {
        val none = GeneratorOptions(upper = false, lower = false, numbers = false, safeSymbols = false)
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(none) }
    }

    @Test
    fun randomIndex_isUniformWithoutModuloBias() {
        // 256 % 3 = 1: tanpa rejection sampling indeks 0 akan lebih sering muncul.
        val random = SecureRandom()
        val bound = 3
        val samples = 90_000
        val counts = IntArray(bound)
        repeat(samples) { counts[PasswordGenerator.randomIndex(bound, random)]++ }
        val expected = samples / bound
        for (c in counts) assertTrue("count=$c", kotlin.math.abs(c - expected) < 1_000)
    }

    @Test
    fun randomIndex_staysInBounds() {
        for (bound in listOf(1, 2, 26, 89, 255, 256)) {
            repeat(2_000) { assertTrue(PasswordGenerator.randomIndex(bound) in 0 until bound) }
        }
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.randomIndex(0) }
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.randomIndex(257) }
    }
}

class PasswordStrengthTest {

    @Test
    fun flagsWeakPasswordsLocally() {
        for (weak in listOf(
            "abc", "1234567", "12345678", "password", "Password", "PASSWORD123", "qwerty123",
            "aaaaaaaa", "abcdefgh", "87654321", "abcabcabc", "qwertyuiop", "p@ssw0rd", "rahasia"
        )) {
            assertTrue("seharusnya lemah: $weak", PasswordStrength.isWeakLocal(weak))
        }
    }

    @Test
    fun acceptsStrongPasswords() {
        for (strong in listOf("Xk9#mP2\$vL7@qR4!", "kuda-biru-melompat-7-pagar", "T8!nQz@4wLp#")) {
            assertFalse("seharusnya kuat: $strong", PasswordStrength.isWeakLocal(strong))
        }
    }

    @Test
    fun scoreOrdersByQuality() {
        assertEquals(Strength.VERY_WEAK, PasswordStrength.score(""))
        assertEquals(Strength.VERY_WEAK, PasswordStrength.score("abc"))
        assertEquals(Strength.VERY_WEAK, PasswordStrength.score("password"))
        val generated = PasswordGenerator.generate(GeneratorOptions(length = 20, extendedSymbols = true))
        assertTrue(PasswordStrength.score(generated) >= Strength.STRONG)
    }
}

class HibpTest {

    @Test
    fun sha1_prefixSuffix() {
        val hash = Hibp.sha1Hex("password")
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", hash)
        assertEquals("5BAA6", Hibp.prefix(hash))
        assertEquals("1E4C9B93F3F0682250B6CF8331B7EE68FD8", Hibp.suffix(hash))
    }

    @Test
    fun breachCount_parsesRangeResponse() {
        val body = "003D68EB55068C33ACE09247EE4C639306B:3\r\n" +
            "1E4C9B93F3F0682250B6CF8331B7EE68FD8:9545824\r\n" +
            "1E4C9B93F3F0682250B6CF8331B7EE68FD9:0\r\n"
        assertEquals(9545824, Hibp.breachCount(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD8"))
        assertEquals(3, Hibp.breachCount(body, "003d68eb55068c33ace09247ee4c639306b"))
        // Baris padding (count 0) dan suffix tak ada -> 0
        assertEquals(0, Hibp.breachCount(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD9"))
        assertEquals(0, Hibp.breachCount(body, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"))
        assertEquals(0, Hibp.breachCount("", "ABC"))
    }
}
