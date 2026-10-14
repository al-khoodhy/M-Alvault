package com.alvault.app.core.backup

import com.alvault.app.core.crypto.AegisFile
import com.alvault.app.core.crypto.CryptoException
import com.alvault.app.core.crypto.Fixtures
import com.alvault.app.core.model.MergeMode
import com.alvault.app.core.model.VaultItem
import com.alvault.app.core.model.VaultJson
import com.alvault.app.core.model.VaultMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class BackupServiceTest {

    private val fastIterations = 1_000 // hanya untuk tes round-trip cepat; fixture memakai 600.000
    private val pw = "backup-pass é✓ 2026".toCharArray()

    private val items = listOf(
        VaultItem("a", "GitHub", "me@example.com", "p\"w\\d/+=", "Kustom...", "https://github.com", "baris1\r\nbaris2", 10L),
        VaultItem("b", "Bank Éclair ✓", "ani", "𝄞pässwörd", "Bank / Finansial", null, null, 20L)
    )

    @Test
    fun `export then decrypt round-trips all fields including unicode`() {
        val file = BackupService.export(items, pw, fastIterations)
        assertEquals(items, BackupService.decrypt(file, pw, fastIterations))
    }

    @Test
    fun `exported file follows the aegis schema with fresh salt and iv`() {
        val a = BackupService.export(items, pw, fastIterations)
        val b = BackupService.export(items, pw, fastIterations)
        val pa = AegisFile.parse(a)
        val pb = AegisFile.parse(b)
        assertEquals(1, pa.version)
        assertEquals(16, pa.salt.size)
        assertEquals(12, pa.iv.size)
        assertNotEquals(a, b)
        assertTrue(!pa.salt.contentEquals(pb.salt))
        assertTrue(!pa.iv.contentEquals(pb.iv))
        // Hanya field v, s, i, c (tanpa data mentah).
        assertEquals(setOf("v", "s", "i", "c"), Regex("\"(\\w+)\":").findAll(a).map { it.groupValues[1] }.toSet())
        assertTrue("password tidak boleh muncul di berkas", !a.contains("GitHub") && !a.contains("pässwörd"))
    }

    @Test
    fun `wrong password fails with CryptoException`() {
        val file = BackupService.export(items, pw, fastIterations)
        assertThrows(CryptoException::class.java) {
            BackupService.decrypt(file, "salah".toCharArray(), fastIterations)
        }
    }

    @Test
    fun `tampered ciphertext is rejected by the GCM tag`() {
        val file = BackupService.export(items, pw, fastIterations)
        val payload = AegisFile.parse(file)
        payload.ciphertext[0] = (payload.ciphertext[0].toInt() xor 1).toByte()
        assertThrows(CryptoException::class.java) {
            BackupService.decrypt(AegisFile.toJson(payload), pw, fastIterations)
        }
    }

    @Test
    fun `garbage and non aegis json are rejected`() {
        assertThrows(CryptoException::class.java) { BackupService.decrypt("bukan json", pw) }
        assertThrows(CryptoException::class.java) { BackupService.decrypt("[]", pw) }
        assertThrows(CryptoException::class.java) { BackupService.decrypt("{\"v\":2,\"s\":\"AA==\",\"i\":\"AA==\",\"c\":\"AA==\"}", pw) }
    }

    @Test
    fun `empty backup password is refused`() {
        assertThrows(IllegalArgumentException::class.java) { BackupService.export(items, CharArray(0), fastIterations) }
    }

    @Test
    fun `empty vault round-trips`() {
        val file = BackupService.export(emptyList(), pw, fastIterations)
        assertEquals(emptyList<VaultItem>(), BackupService.decrypt(file, pw, fastIterations))
    }

    /** Berkas dibuat oleh WebCrypto Node (PBKDF2-SHA256 600.000 + AES-GCM): bukti interoperabilitas dengan web. */
    @Test
    fun `imports a file produced by WebCrypto with real 600000 iterations`() {
        val decrypted = BackupService.decrypt(Fixtures.text("web-node.aegis"), Fixtures.PASSWORD)
        val expected = VaultJson.decode(Fixtures.bytes("web-node.plain.json"))
        assertEquals(2, decrypted.size)
        assertEquals(expected, decrypted)
        assertEquals("Netflix 🎬", decrypted[0].title)
        assertEquals("baris1\r\nbaris2 \"kutip\" \\ garis", decrypted[0].notes)
        assertEquals("𝄞-clef-üñí", decrypted[1].password)
    }

    @Test
    fun `imports the python generated sample as well`() {
        val decrypted = BackupService.decrypt(Fixtures.text("web-sample.aegis"), Fixtures.PASSWORD)
        assertEquals(VaultJson.decode(Fixtures.bytes("web-sample.plain.json")), decrypted)
    }

    @Test
    fun `import accepts a UTF-8 BOM`() {
        val withBom = "﻿" + Fixtures.text("web-node.aegis")
        assertEquals(2, BackupService.decrypt(withBom, Fixtures.PASSWORD).size)
    }

    @Test
    fun `import then merge keeps newer local entries and adds new ones`() {
        val local = listOf(
            VaultItem("a", "GitHub (lokal, lebih baru)", "u", "p", null, null, null, 999L),
            VaultItem("z", "Hanya lokal", "u", "p", null, null, null, 1L)
        )
        val incoming = BackupService.decrypt(BackupService.export(items, pw, fastIterations), pw, fastIterations)
        val outcome = VaultMerge.apply(MergeMode.MERGE, local, incoming)
        assertEquals(3, outcome.items.size)
        assertEquals(1, outcome.result.added)
        assertEquals(1, outcome.result.skipped)
        assertEquals("GitHub (lokal, lebih baru)", outcome.items.first { it.id == "a" }.title)
    }

    @Test
    fun `suggested file name uses date and aegis extension`() {
        // 2026-10-12T00:00:00Z
        val name = BackupService.suggestedFileName(1_791_763_200_000L, ZoneOffset.UTC)
        assertEquals("alvault-backup-20261012.aegis", name)
    }
}
