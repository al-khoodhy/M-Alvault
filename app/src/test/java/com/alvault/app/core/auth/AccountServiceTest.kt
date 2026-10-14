package com.alvault.app.core.auth

import com.alvault.app.core.FakeAccountStore
import com.alvault.app.core.FakeArgon2
import com.alvault.app.core.FakeVaultStore
import com.alvault.app.core.crypto.Base64Codec
import com.alvault.app.core.crypto.CryptoException
import com.alvault.app.core.model.VaultItem
import com.alvault.app.core.vault.VaultService
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountServiceTest {
    private val hasher = FakeArgon2()
    private val accounts = FakeAccountStore()
    private val store = FakeVaultStore()
    private var now = 1_000_000L
    private val vault = VaultService(store) { now }
    private val service = AccountService(hasher, accounts, vault) { now }

    private val pw = "Tr0ub4dor&3-kuda".toCharArray()

    private fun unlockOk(password: CharArray = pw): ByteArray =
        (service.unlock(password) as UnlockResult.Success).encryptionKey

    @Test
    fun register_createsEncryptedEmptyVault_andStoresOnlyMetadata() {
        val key = service.register("  Ani@Example.COM ", pw.copyOf())

        assertEquals(32, key.size)
        assertEquals("ani@example.com", accounts.userEmail)
        assertEquals(16, Base64Codec.decode(accounts.localSalt!!).size)
        assertEquals(3, accounts.kdfIterations)
        // SHA3-512 = 64 byte -> Base64 88 karakter
        assertEquals(64, Base64Codec.decode(accounts.storedVerifier!!).size)

        // Vault awal "[]" terenkripsi dengan EncryptionKey dan bisa dibuka dengannya.
        val stored = vault.peek()!!
        assertEquals(1L, stored.revision)
        assertEquals(0, stored.itemCount)
        assertTrue(vault.load(key).items.isEmpty())
        assertThrows(CryptoException::class.java) { vault.load(ByteArray(32) { 7 }) }
    }

    @Test
    fun storedMaterial_neverContainsPasswordOrEncryptionKey() {
        val key = service.register("a@b.co", pw.copyOf())
        val keyB64 = Base64Codec.encode(key)
        val dump = listOf(accounts.userEmail, accounts.localSalt, accounts.storedVerifier, store.stored!!.blob)
            .joinToString("|")
        assertFalse(dump.contains(String(pw)))
        assertFalse(dump.contains(keyB64))
    }

    @Test
    fun unlock_returnsSameKeyAsRegistration() {
        val registered = service.register("a@b.co", pw.copyOf())
        assertArrayEquals(registered, unlockOk(pw.copyOf()))
    }

    @Test
    fun unlock_isCaseAndSpaceInsensitiveOnEmailOnlyBecauseItIsNormalizedAtRegistration() {
        service.register("  A@B.Co", pw.copyOf())
        assertEquals("a@b.co", accounts.userEmail)
        unlockOk(pw.copyOf())
    }

    @Test
    fun unlock_wrongPassword_countsAttempts_andLocksOutAfterFreeAttempts() {
        service.register("a@b.co", pw.copyOf())
        val wrong = "salah-banget".toCharArray()

        repeat(AccountService.FREE_ATTEMPTS) { i ->
            val r = service.unlock(wrong.copyOf()) as UnlockResult.WrongPassword
            assertEquals(i + 1, r.failedAttempts)
            assertEquals(0L, r.lockoutMillis)
        }
        val fifth = service.unlock(wrong.copyOf()) as UnlockResult.WrongPassword
        assertEquals(30_000L, fifth.lockoutMillis)

        // Selama jeda, bahkan password benar ditolak TANPA menjalankan Argon2 lagi.
        val callsBefore = hasher.calls
        now += 10_000
        val locked = service.unlock(pw.copyOf()) as UnlockResult.LockedOut
        assertEquals(20_000L, locked.remainingMillis)
        assertEquals(callsBefore, hasher.calls)

        // Setelah jeda berakhir, password benar berhasil dan menghapus penghitung.
        now += 20_001
        unlockOk(pw.copyOf())
        assertEquals(0, accounts.failedAttempts)
        assertEquals(0L, accounts.lockoutUntil)
    }

    @Test
    fun lockoutSchedule_isExponentialAndCapped() {
        assertEquals(0L, AccountService.lockoutFor(1))
        assertEquals(0L, AccountService.lockoutFor(4))
        assertEquals(30_000L, AccountService.lockoutFor(5))
        assertEquals(60_000L, AccountService.lockoutFor(6))
        assertEquals(120_000L, AccountService.lockoutFor(7))
        assertEquals(AccountService.MAX_LOCKOUT_MS, AccountService.lockoutFor(50))
        assertEquals(AccountService.MAX_LOCKOUT_MS, AccountService.lockoutFor(Int.MAX_VALUE / 2))
    }

    @Test
    fun register_rejectsInvalidInput_andSecondRegistration() {
        assertThrows(IllegalArgumentException::class.java) { service.register("bukan-email", pw.copyOf()) }
        assertThrows(IllegalArgumentException::class.java) { service.register("a@b.co", "pendek".toCharArray()) }
        assertFalse(service.hasAccount())
        assertNull(store.stored)

        service.register("a@b.co", pw.copyOf())
        assertThrows(IllegalStateException::class.java) { service.register("c@d.co", pw.copyOf()) }
        assertEquals("a@b.co", accounts.userEmail)
    }

    @Test
    fun register_rollsBackVault_whenAccountCannotBeSaved() {
        accounts.saveShouldFail = true
        assertThrows(IllegalStateException::class.java) { service.register("a@b.co", pw.copyOf()) }
        assertNull(store.stored)
        assertFalse(service.hasAccount())
    }

    @Test
    fun resetAll_removesEverything() {
        val key = service.register("a@b.co", pw.copyOf())
        vault.save(key, listOf(VaultItem(title = "x", username = "u", password = "p")))
        service.resetAll()
        assertFalse(service.hasAccount())
        assertNull(store.stored)
        assertNull(accounts.storedVerifier)
    }

    @Test
    fun differentUsers_haveDifferentKeys_evenWithSamePassword() {
        val k1 = service.register("a@b.co", pw.copyOf())
        service.resetAll()
        val k2 = service.register("a@b.co", pw.copyOf())
        // localSalt acak per registrasi -> kunci berbeda walau email & password sama
        assertNotNull(k1)
        assertFalse(k1.contentEquals(k2))
    }
}
