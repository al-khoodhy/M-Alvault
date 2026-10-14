package com.alvault.app.core.auth

import com.alvault.app.core.crypto.AesGcm
import com.alvault.app.core.crypto.Argon2Hasher
import com.alvault.app.core.crypto.Base64Codec
import com.alvault.app.core.crypto.LocalVerifier
import com.alvault.app.core.crypto.MasterKeyDeriver
import com.alvault.app.core.vault.VaultService

/** Penyimpanan metadata akun lokal (produksi: EncryptedSharedPreferences). Tidak pernah berisi password/kunci. */
interface AccountStore {
    val userEmail: String?
    val localSalt: String?
    val kdfIterations: Int
    val storedVerifier: String?

    /** Disimpan sinkron agar akun tidak setengah tersimpan. @return true bila sukses. */
    fun saveAccount(email: String, localSalt: String, kdfIterations: Int, verifier: String): Boolean

    var failedAttempts: Int
    var lockoutUntil: Long

    fun clearAll(): Boolean

    fun hasAccount(): Boolean = userEmail != null && localSalt != null && storedVerifier != null
}

sealed interface UnlockResult {
    /** [encryptionKey] adalah salinan milik pemanggil (wajib di-wipe setelah dipakai). */
    class Success(val encryptionKey: ByteArray) : UnlockResult

    class WrongPassword(val failedAttempts: Int, val lockoutMillis: Long) : UnlockResult

    class LockedOut(val remainingMillis: Long) : UnlockResult
}

/**
 * Registrasi & login lokal tanpa server (Zero-Knowledge):
 * master password -> Argon2id (64 byte) -> EncryptionKey (0..31) + LocalAuthHash (32..63).
 * Hanya verifier `SHA3-512(LocalAuthHash + email + pepper)` yang disimpan; kunci tidak pernah ke disk.
 */
class AccountService(
    private val hasher: Argon2Hasher,
    private val accounts: AccountStore,
    private val vault: VaultService,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun hasAccount(): Boolean = accounts.hasAccount()

    val email: String? get() = accounts.userEmail

    /**
     * Registrasi lokal (inisialisasi pertama).
     * @return EncryptionKey (salinan milik pemanggil; wajib di-wipe).
     * @throws IllegalArgumentException input tidak valid
     * @throws IllegalStateException akun sudah ada atau penyimpanan gagal
     */
    fun register(email: String, masterPassword: CharArray): ByteArray {
        check(!accounts.hasAccount()) { "Brankas sudah dibuat di perangkat ini." }
        val normalized = MasterKeyDeriver.normalizeEmail(email)
        require(EMAIL_REGEX.matches(normalized)) { "Format email tidak valid." }
        require(masterPassword.size >= MIN_MASTER_PASSWORD_LENGTH) {
            "Master password minimal $MIN_MASTER_PASSWORD_LENGTH karakter."
        }

        val localSalt = Base64Codec.encode(AesGcm.randomBytes(LOCAL_SALT_BYTES))
        val keys = MasterKeyDeriver.derive(hasher, normalized, masterPassword, localSalt)
        try {
            val verifier = LocalVerifier.compute(keys.localAuthHash, normalized)
            // Brankas kosong ditulis lebih dulu: keberadaan akun adalah penanda "sudah terdaftar",
            // sehingga crash di tengah proses tidak meninggalkan akun tanpa brankas.
            vault.initialize(keys.encryptionKey)
            val saved = accounts.saveAccount(normalized, localSalt, MasterKeyDeriver.ARGON2_ITERATIONS, verifier)
            if (!saved) {
                vault.clear()
                throw IllegalStateException("Gagal menyimpan data akun.")
            }
            accounts.failedAttempts = 0
            accounts.lockoutUntil = 0L
            return keys.encryptionKey.copyOf()
        } finally {
            keys.wipe()
        }
    }

    /** Login dengan master password: derive Argon2id -> verifikasi hash -> kembalikan EncryptionKey. */
    fun unlock(masterPassword: CharArray): UnlockResult {
        val now = clock()
        val lockedUntil = accounts.lockoutUntil
        if (lockedUntil > now) return UnlockResult.LockedOut(lockedUntil - now)

        val email = checkNotNull(accounts.userEmail) { "Belum ada akun." }
        val salt = checkNotNull(accounts.localSalt) { "Belum ada akun." }
        val stored = checkNotNull(accounts.storedVerifier) { "Belum ada akun." }

        val keys = MasterKeyDeriver.derive(hasher, email, masterPassword, salt, accounts.kdfIterations)
        try {
            val computed = LocalVerifier.compute(keys.localAuthHash, email)
            if (!LocalVerifier.matches(stored, computed)) {
                val attempts = accounts.failedAttempts + 1
                accounts.failedAttempts = attempts
                val lockout = lockoutFor(attempts)
                if (lockout > 0) accounts.lockoutUntil = now + lockout
                return UnlockResult.WrongPassword(attempts, lockout)
            }
            accounts.failedAttempts = 0
            accounts.lockoutUntil = 0L
            return UnlockResult.Success(keys.encryptionKey.copyOf())
        } finally {
            keys.wipe()
        }
    }

    /** Menghapus seluruh data lokal (brankas + akun). Tidak bisa dibatalkan. */
    fun resetAll() {
        vault.clear()
        accounts.clearAll()
    }

    companion object {
        const val MIN_MASTER_PASSWORD_LENGTH = 8
        const val LOCAL_SALT_BYTES = 16
        const val FREE_ATTEMPTS = 4
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 15 * 60_000L

        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /** 4 percobaan salah pertama gratis; setelah itu jeda 30 dtk, 60 dtk, 2 mnt, ... maks 15 mnt. */
        fun lockoutFor(failedAttempts: Int): Long {
            if (failedAttempts <= FREE_ATTEMPTS) return 0L
            val exponent = (failedAttempts - FREE_ATTEMPTS - 1).coerceAtMost(10)
            return (BASE_LOCKOUT_MS shl exponent).coerceAtMost(MAX_LOCKOUT_MS)
        }
    }
}
