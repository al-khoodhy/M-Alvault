package com.alvault.app.ui

import com.alvault.app.core.model.MergeMode
import com.alvault.app.core.model.MergeResult
import com.alvault.app.core.model.VaultCategories
import com.alvault.app.core.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class UiLogicTest {

    @Test
    fun `maskEmail hides the middle of the local part`() {
        assertEquals("a•••e@example.com", UiLogic.maskEmail("alice@example.com"))
        assertEquals("a•••@x.id", UiLogic.maskEmail("ab@x.id"))
        assertEquals("a•••@x.id", UiLogic.maskEmail("a@x.id"))
        assertEquals("tanpa-at", UiLogic.maskEmail("tanpa-at"))
    }

    @Test
    fun `formatRemaining rounds up to whole seconds`() {
        assertEquals("1 dtk", UiLogic.formatRemaining(1))
        assertEquals("30 dtk", UiLogic.formatRemaining(30_000))
        assertEquals("30 dtk", UiLogic.formatRemaining(29_001))
        assertEquals("1 mnt", UiLogic.formatRemaining(60_000))
        assertEquals("1 mnt 1 dtk", UiLogic.formatRemaining(60_001))
        assertEquals("15 mnt", UiLogic.formatRemaining(15 * 60_000L))
        assertEquals("0 dtk", UiLogic.formatRemaining(-5))
    }

    @Test
    fun `registrationError checks email, length and confirmation in order`() {
        assertNotNull(UiLogic.registrationError("bukan-email", "password-panjang", "password-panjang"))
        assertNotNull(UiLogic.registrationError("a@b.id", "pendek", "pendek"))
        assertNotNull(UiLogic.registrationError("a@b.id", "password-panjang", "beda-sekali-1"))
        assertNull(UiLogic.registrationError(" a@b.id ", "password-panjang", "password-panjang"))
        // Tepat 8 karakter diterima (sama dengan AccountService).
        assertNull(UiLogic.registrationError("a@b.id", "12345678", "12345678"))
    }

    @Test
    fun `backupPasswordError requires length and confirmation only when exporting`() {
        assertNotNull(UiLogic.backupPasswordError("", null))
        assertNull(UiLogic.backupPasswordError("x", null)) // impor: password web bisa pendek
        assertNotNull(UiLogic.backupPasswordError("pendek", "pendek"))
        assertNotNull(UiLogic.backupPasswordError("password-ok-1", "password-ok-2"))
        assertNull(UiLogic.backupPasswordError("password-ok-1", "password-ok-1"))
    }

    @Test
    fun `itemError only requires a title`() {
        assertNotNull(UiLogic.itemError("   "))
        assertNull(UiLogic.itemError("GitHub"))
    }

    @Test
    fun `resolveCategory handles blank, default and custom`() {
        assertNull(UiLogic.resolveCategory(null, "x"))
        assertNull(UiLogic.resolveCategory("", "x"))
        assertEquals("Email", UiLogic.resolveCategory("Email", "diabaikan"))
        assertEquals("Hobi", UiLogic.resolveCategory(VaultCategories.CUSTOM_LABEL, "  Hobi "))
        assertNull(UiLogic.resolveCategory(VaultCategories.CUSTOM_LABEL, "   "))
    }

    @Test
    fun `buildItem trims text but keeps the password exactly and keeps id when editing`() {
        val existing = VaultItem(id = "id-1", title = "Lama", username = "u", password = "p", lastModified = 1L)
        val edited = UiLogic.buildItem(existing, "  Baru ", " user ", "  sandi dengan spasi  ", "Email", " ", "  ", now = 99L)
        assertEquals("id-1", edited.id)
        assertEquals("Baru", edited.title)
        assertEquals("user", edited.username)
        assertEquals("  sandi dengan spasi  ", edited.password)
        assertNull(edited.url)
        assertNull(edited.notes)
        assertEquals(99L, edited.lastModified)

        val created = UiLogic.buildItem(null, "X", "", "", null, " https://a.id ", " catatan ", now = 5L)
        assertNotEquals("", created.id)
        assertEquals("https://a.id", created.url)
        assertEquals("catatan", created.notes)
    }

    @Test
    fun `initialOf picks the first letter or digit`() {
        assertEquals("G", UiLogic.initialOf("github"))
        assertEquals("7", UiLogic.initialOf("  7-Eleven"))
        assertEquals("É", UiLogic.initialOf("école"))
        assertEquals("•", UiLogic.initialOf("   "))
        assertEquals("•", UiLogic.initialOf("--"))
    }

    @Test
    fun `browsableUrl only allows http and https`() {
        assertEquals("https://github.com", UiLogic.browsableUrl("github.com"))
        assertEquals("http://a.id/x", UiLogic.browsableUrl(" http://a.id/x "))
        assertEquals("https://a.id", UiLogic.browsableUrl("https://a.id"))
        assertNull(UiLogic.browsableUrl("javascript://alert(1)"))
        assertNull(UiLogic.browsableUrl("file:///sdcard/x"))
        assertNull(UiLogic.browsableUrl("intent://x#Intent;end"))
        assertNull(UiLogic.browsableUrl(""))
        assertNull(UiLogic.browsableUrl("ada spasi.com"))
    }

    @Test
    fun `describeImport reports each mode`() {
        assertEquals(
            "Impor selesai: 3 ditambahkan, 1 diperbarui, 2 dilewati.",
            UiLogic.describeImport(MergeMode.MERGE, MergeResult(3, 1, 2))
        )
        assertEquals(
            "Brankas diganti dengan 4 entri dari backup.",
            UiLogic.describeImport(MergeMode.REPLACE, MergeResult(4, 0, 0))
        )
    }

    @Test
    fun `readLimited returns bytes within the limit and null above it`() {
        val data = ByteArray(20_000) { (it % 251).toByte() }
        assertEquals(data.toList(), UiLogic.readLimited(ByteArrayInputStream(data), 20_000)!!.toList())
        assertNull(UiLogic.readLimited(ByteArrayInputStream(data), 19_999))
        assertEquals(0, UiLogic.readLimited(ByteArrayInputStream(ByteArray(0)), 10)!!.size)
    }
}
