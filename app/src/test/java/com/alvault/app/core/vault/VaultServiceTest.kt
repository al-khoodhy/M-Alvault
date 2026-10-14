package com.alvault.app.core.vault

import com.alvault.app.core.FakeVaultStore
import com.alvault.app.core.crypto.AesGcm
import com.alvault.app.core.crypto.CryptoException
import com.alvault.app.core.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultServiceTest {
    private val store = FakeVaultStore()
    private var now = 42L
    private val service = VaultService(store) { now }
    private val key = AesGcm.randomBytes(32)

    private fun item(id: String, title: String = "T-$id") =
        VaultItem(id = id, title = title, username = "u", password = "Pässw0rd!$id", lastModified = 1)

    @Test
    fun load_withoutStoredVault_isEmpty() {
        val loaded = service.load(key)
        assertTrue(loaded.items.isEmpty())
        assertEquals(0L, loaded.revision)
    }

    @Test
    fun saveAndLoad_roundTrip_andRevisionIncrements() {
        val first = service.save(key, listOf(item("a")))
        assertEquals(1L, first.revision)
        assertEquals(1, first.itemCount)
        assertEquals(42L, first.updatedAt)

        val second = service.save(key, listOf(item("a"), item("b")))
        assertEquals(2L, second.revision)
        val loaded = service.load(key)
        assertEquals(listOf("a", "b"), loaded.items.map { it.id })
        assertEquals(2L, loaded.revision)
    }

    @Test
    fun storedBlob_isCiphertext_andUsesFreshIvEachSave() {
        val items = listOf(item("a"))
        val b1 = service.save(key, items).blob
        val b2 = service.save(key, items).blob
        assertFalse(b1 == b2)
        assertFalse(b1.contains("Pässw0rd"))
    }

    @Test
    fun load_withWrongKey_throwsCryptoException() {
        service.save(key, listOf(item("a")))
        assertThrows(CryptoException::class.java) { service.load(AesGcm.randomBytes(32)) }
    }

    @Test
    fun save_refusesWipedOrInvalidKey_soVaultIsNeverEncryptedWithZeroKey() {
        service.save(key, listOf(item("a")))
        val before = store.stored

        assertThrows(CryptoException::class.java) { service.save(ByteArray(32), listOf(item("evil"))) }
        assertThrows(CryptoException::class.java) { service.save(ByteArray(16) { 1 }, listOf(item("evil"))) }

        assertEquals(before, store.stored) // tidak berubah
        assertEquals(listOf("a"), service.load(key).items.map { it.id })
    }

    @Test
    fun replaceWithBlob_andSetRevision() {
        val blob = service.save(key, listOf(item("a"), item("b"))).blob
        service.clear()
        assertNull(service.peek())

        val replaced = service.replaceWithBlob(blob, 2, 9L)
        assertEquals(9L, replaced.revision)
        assertEquals(2, service.load(key).items.size)

        service.setRevision(10L)
        assertEquals(10L, service.peek()!!.revision)
        assertEquals(blob, service.peek()!!.blob)
    }

    @Test
    fun explicitRevision_isHonored() {
        val s = service.save(key, emptyList(), revision = 77L)
        assertEquals(77L, s.revision)
        assertEquals(78L, service.save(key, emptyList()).revision)
    }
}
