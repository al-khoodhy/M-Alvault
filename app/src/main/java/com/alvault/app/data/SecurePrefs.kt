package com.alvault.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alvault.app.core.auth.AccountStore
import com.alvault.app.core.sync.SyncSettingsStore

/**
 * Metadata akun & konfigurasi di `EncryptedSharedPreferences` (kunci dilindungi Android Keystore).
 * TIDAK pernah berisi master password maupun EncryptionKey.
 *
 * Semua metode bersifat blocking (akses Keystore/disk): panggil dari dispatcher IO.
 */
class SecurePrefs(context: Context) : AccountStore, SyncSettingsStore {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ---- Akun lokal ----
    override val userEmail: String? get() = prefs.getString(USER_EMAIL, null)
    override val localSalt: String? get() = prefs.getString(LOCAL_SALT, null)
    override val kdfIterations: Int get() = prefs.getInt(KDF_ITERATIONS, DEFAULT_KDF_ITERATIONS)
    override val storedVerifier: String? get() = prefs.getString(STORED_VERIFIER, null)

    /** Disimpan sinkron (commit) agar akun tidak setengah tersimpan bila proses mati. */
    override fun saveAccount(email: String, localSalt: String, kdfIterations: Int, verifier: String): Boolean =
        prefs.edit()
            .putString(USER_EMAIL, email)
            .putString(LOCAL_SALT, localSalt)
            .putInt(KDF_ITERATIONS, kdfIterations)
            .putString(STORED_VERIFIER, verifier)
            .commit()

    // ---- Pembatasan percobaan login (commit: tidak bisa dihindari dengan mematikan proses) ----
    override var failedAttempts: Int
        get() = prefs.getInt(FAILED_ATTEMPTS, 0)
        set(value) { prefs.edit().putInt(FAILED_ATTEMPTS, value).commit() }

    override var lockoutUntil: Long
        get() = prefs.getLong(LOCKOUT_UNTIL, 0L)
        set(value) { prefs.edit().putLong(LOCKOUT_UNTIL, value).commit() }

    // ---- Biometrik (EncryptionKey terbungkus kunci Keystore) ----
    val biometricEnabled: Boolean get() = prefs.getBoolean(BIO_ENABLED, false)
    val biometricIv: String? get() = prefs.getString(BIO_IV, null)
    val biometricWrappedKey: String? get() = prefs.getString(BIO_WRAPPED_KEY, null)

    fun saveBiometric(iv: String, wrappedKey: String) {
        prefs.edit().putBoolean(BIO_ENABLED, true).putString(BIO_IV, iv).putString(BIO_WRAPPED_KEY, wrappedKey).commit()
    }

    fun clearBiometric() {
        prefs.edit().putBoolean(BIO_ENABLED, false).remove(BIO_IV).remove(BIO_WRAPPED_KEY).commit()
    }

    // ---- Supabase (opsional) ----
    override var supabaseEnabled: Boolean
        get() = prefs.getBoolean(SB_ENABLED, false)
        set(value) { prefs.edit().putBoolean(SB_ENABLED, value).commit() }

    override var supabaseUrl: String
        get() = prefs.getString(SB_URL, "") ?: ""
        set(value) { prefs.edit().putString(SB_URL, value).commit() }

    override var supabaseKey: String
        get() = prefs.getString(SB_KEY, "") ?: ""
        set(value) { prefs.edit().putString(SB_KEY, value).commit() }

    override var lastSyncedRevision: Long
        get() = prefs.getLong(SB_LAST_REVISION, 0L)
        set(value) { prefs.edit().putLong(SB_LAST_REVISION, value).commit() }

    override fun clearAll(): Boolean = prefs.edit().clear().commit()

    private companion object {
        const val FILE_NAME = "alvault_secure_prefs"
        const val DEFAULT_KDF_ITERATIONS = 3

        const val USER_EMAIL = "user_email"
        const val LOCAL_SALT = "local_salt"
        const val KDF_ITERATIONS = "kdf_iterations"
        const val STORED_VERIFIER = "stored_verifier"

        const val FAILED_ATTEMPTS = "failed_attempts"
        const val LOCKOUT_UNTIL = "lockout_until"

        const val BIO_ENABLED = "bio_enabled"
        const val BIO_IV = "bio_iv"
        const val BIO_WRAPPED_KEY = "bio_wrapped_key"

        const val SB_ENABLED = "supabase_enabled"
        const val SB_URL = "supabase_url"
        const val SB_KEY = "supabase_key"
        const val SB_LAST_REVISION = "supabase_last_revision"
    }
}
