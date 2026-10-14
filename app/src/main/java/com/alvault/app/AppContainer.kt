package com.alvault.app

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import com.alvault.app.core.auth.AccountService
import com.alvault.app.core.crypto.Argon2ktHasher
import com.alvault.app.core.security.HibpClient
import com.alvault.app.core.security.SecurityAuditor
import com.alvault.app.core.session.SessionManager
import com.alvault.app.core.sync.SyncService
import com.alvault.app.core.vault.VaultService
import com.alvault.app.data.SecurePrefs
import com.alvault.app.data.db.RoomVaultBlobStore
import com.alvault.app.data.db.VaultDatabase
import com.alvault.app.data.net.OkHttpExecutor
import com.alvault.app.platform.AutoLockTicker
import com.alvault.app.platform.BiometricKeyVault
import com.alvault.app.platform.ClipboardCleaner
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Penyedia dependensi manual (tanpa framework DI). Semua properti `by lazy`: pembuatan [SecurePrefs]
 * dan database bersifat blocking, jadi pertama kali diakses dari dispatcher IO (lihat ViewModel).
 */
class AppContainer(val appContext: Context) {

    /** Satu-satunya pemegang EncryptionKey di RAM. Memakai jam monoton agar tidur perangkat ikut terhitung. */
    val session = SessionManager(clock = SystemClock::elapsedRealtime)

    val securePrefs: SecurePrefs by lazy { SecurePrefs(appContext) }

    private val database: VaultDatabase by lazy {
        // Tanpa fallbackToDestructiveMigration: brankas tidak boleh hilang diam-diam (lihat VaultDatabase).
        Room.databaseBuilder(appContext, VaultDatabase::class.java, "alvault.db").build()
    }

    val vaultService: VaultService by lazy { VaultService(RoomVaultBlobStore(database.vaultDao())) }

    private val http by lazy { OkHttpExecutor() }

    val accountService: AccountService by lazy { AccountService(Argon2ktHasher(), securePrefs, vaultService) }

    val syncService: SyncService by lazy { SyncService(securePrefs, vaultService, securePrefs, http) }

    val auditor: SecurityAuditor by lazy { SecurityAuditor(HibpClient(http)) }

    val biometric: BiometricKeyVault by lazy { BiometricKeyVault(securePrefs) }

    val clipboard: ClipboardCleaner by lazy { ClipboardCleaner(appContext) }

    /** Sinyal "isi brankas lokal berubah di luar VaultViewModel" (mis. setelah Pull dari cloud). */
    val vaultChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val autoLockTicker = AutoLockTicker(session)

    init {
        session.addListener { unlocked -> if (unlocked) autoLockTicker.start() else autoLockTicker.stop() }
    }
}
