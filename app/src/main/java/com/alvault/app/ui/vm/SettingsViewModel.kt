package com.alvault.app.ui.vm

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alvault.app.AppContainer
import com.alvault.app.core.crypto.Bytes
import com.alvault.app.core.crypto.CryptoException
import com.alvault.app.core.net.NetworkException
import com.alvault.app.core.session.SessionLockedException
import com.alvault.app.core.sync.PingResult
import com.alvault.app.core.sync.PullResult
import com.alvault.app.core.sync.PullStrategy
import com.alvault.app.core.sync.PushResult
import com.alvault.app.core.sync.RemoteDecryptException
import com.alvault.app.core.sync.SupabaseException
import com.alvault.app.platform.BiometricKeyVault
import com.alvault.app.platform.BiometricResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatusKind { INFO, OK, WARN, ERROR }

data class SyncStatus(val text: String, val kind: StatusKind)

data class SettingsUi(
    val email: String = "",

    // Biometrik
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,

    // Supabase (opsional)
    val syncEnabled: Boolean = false,
    val syncUrl: String = "",
    val syncKey: String = "",
    val lastSyncedRevision: Long = 0L,
    val localRevision: Long = 0L,
    val syncBusy: Boolean = false,
    val syncStatus: SyncStatus? = null,
    /** Revisi cloud yang belum pernah ditarik; menunggu konfirmasi pengguna untuk menimpa. */
    val pushConflict: Long? = null,
    val pullConflict: PullResult.Conflict? = null
)

/** Pengaturan keamanan (biometrik) dan sinkronisasi Supabase opsional. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val sessionListener: (Boolean) -> Unit = { unlocked ->
        // Kunci/URL tidak perlu tinggal di RAM UI saat terkunci; status & konflik juga dibuang.
        if (!unlocked) _ui.value = SettingsUi()
    }

    init {
        container.session.addListener(sessionListener)
    }

    override fun onCleared() {
        container.session.removeListener(sessionListener)
    }

    private fun post(message: String) {
        _messages.tryEmit(message)
    }

    /** Memuat ulang semua nilai dari penyimpanan (dipanggil setelah brankas terbuka). */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = container.securePrefs
                val available = BiometricKeyVault.isAvailable(container.appContext)
                val enabled = available && container.biometric.isEnabled
                _ui.update {
                    it.copy(
                        email = container.accountService.email.orEmpty(),
                        biometricAvailable = available,
                        biometricEnabled = enabled,
                        syncEnabled = prefs.supabaseEnabled,
                        syncUrl = prefs.supabaseUrl,
                        syncKey = prefs.supabaseKey,
                        lastSyncedRevision = prefs.lastSyncedRevision,
                        localRevision = container.vaultService.peek()?.revision ?: 0L
                    )
                }
            } catch (e: Exception) {
                post("Gagal memuat pengaturan.")
            }
        }
    }

    private fun refreshRevisions() {
        try {
            _ui.update {
                it.copy(
                    lastSyncedRevision = container.securePrefs.lastSyncedRevision,
                    localRevision = container.vaultService.peek()?.revision ?: 0L
                )
            }
        } catch (_: Exception) {
            // Hanya tampilan; abaikan.
        }
    }

    // ---- Biometrik ----

    fun setBiometric(activity: FragmentActivity, enable: Boolean) {
        if (!enable) {
            viewModelScope.launch(Dispatchers.IO) {
                container.biometric.disable()
                _ui.update { it.copy(biometricEnabled = false) }
                post("Biometrik dinonaktifkan.")
            }
            return
        }
        try {
            // withKey memberi salinan kunci yang di-wipe setelah blok; BiometricKeyVault menyalinnya sendiri.
            container.session.withKey { key ->
                container.biometric.enable(activity, key) { result ->
                    when (result) {
                        BiometricResult.Enabled -> {
                            _ui.update { it.copy(biometricEnabled = true) }
                            post("Biometrik diaktifkan.")
                        }
                        BiometricResult.Cancelled -> Unit
                        is BiometricResult.Failed -> post(result.message)
                        is BiometricResult.Unlocked -> Bytes.wipe(result.encryptionKey)
                    }
                }
            }
        } catch (e: SessionLockedException) {
            post(e.message ?: "Sesi terkunci.")
        } catch (e: Exception) {
            post("Gagal mengaktifkan biometrik.")
        }
    }

    // ---- Supabase ----

    fun setSyncEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            container.securePrefs.supabaseEnabled = enabled
            _ui.update { it.copy(syncEnabled = enabled, syncStatus = null) }
        }
    }

    /** Menyimpan URL & API key (EncryptedSharedPreferences). */
    fun saveConfig(url: String, key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            persistConfig(url, key)
            _ui.update { it.copy(syncStatus = SyncStatus("Konfigurasi disimpan.", StatusKind.OK)) }
        }
    }

    private fun persistConfig(url: String, key: String) {
        val prefs = container.securePrefs
        prefs.supabaseUrl = url.trim()
        prefs.supabaseKey = key.trim()
        _ui.update { it.copy(syncUrl = url.trim(), syncKey = key.trim()) }
    }

    /**
     * Menjalankan operasi sync di IO dengan penanganan kesalahan seragam.
     * [op] mengembalikan status untuk ditampilkan, atau null bila hasilnya ditangani dialog (konflik).
     */
    private fun runSync(op: () -> SyncStatus?) {
        if (_ui.value.syncBusy) return
        _ui.update { it.copy(syncBusy = true, syncStatus = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val status: SyncStatus? = try {
                op()
            } catch (e: SupabaseException) {
                SyncStatus(e.message ?: "Supabase menolak permintaan.", StatusKind.ERROR)
            } catch (e: NetworkException) {
                SyncStatus(e.message ?: "Tidak bisa terhubung ke server.", StatusKind.ERROR)
            } catch (e: RemoteDecryptException) {
                SyncStatus(e.message ?: "Data cloud tidak bisa didekripsi.", StatusKind.ERROR)
            } catch (e: SessionLockedException) {
                SyncStatus(e.message ?: "Sesi terkunci.", StatusKind.ERROR)
            } catch (e: CryptoException) {
                SyncStatus(e.message ?: "Kesalahan kripto.", StatusKind.ERROR)
            } catch (e: Exception) {
                SyncStatus("Terjadi kesalahan tak terduga.", StatusKind.ERROR)
            }
            _ui.update { it.copy(syncBusy = false, syncStatus = status ?: it.syncStatus) }
            refreshRevisions()
        }
    }

    /** Test Connection: GET ping ke Supabase REST. */
    fun testConnection(url: String, key: String) = runSync {
        persistConfig(url, key)
        when (container.syncService.testConnection(url, key)) {
            PingResult.Ok -> SyncStatus("Koneksi berhasil. Tabel user_vaults ditemukan.", StatusKind.OK)
            PingResult.TableMissing -> SyncStatus(
                "Koneksi dan API key valid, tetapi tabel user_vaults belum ada. Jalankan SQL di bawah pada SQL Editor Supabase.",
                StatusKind.WARN
            )
        }
    }

    /** Push (Upload). Bila cloud punya revisi yang belum ditarik, pengguna diminta konfirmasi. */
    fun push(url: String, key: String, force: Boolean = false) = runSync {
        persistConfig(url, key)
        _ui.update { it.copy(pushConflict = null) }
        when (val result = container.syncService.push(force)) {
            is PushResult.Success ->
                SyncStatus("Upload berhasil: ${result.itemCount} entri (revisi ${result.revision}).", StatusKind.OK)
            is PushResult.Conflict -> {
                _ui.update { it.copy(pushConflict = result.remoteRevision) }
                null
            }
        }
    }

    /** Pull (Download). [strategy] hanya dipakai saat konflik. */
    fun pull(url: String, key: String, strategy: PullStrategy? = null) = runSync {
        persistConfig(url, key)
        _ui.update { it.copy(pullConflict = null) }
        val result = container.session.withKey { encryptionKey ->
            container.syncService.pull(encryptionKey, strategy)
        }
        when (result) {
            PullResult.NoRemote ->
                SyncStatus("Belum ada data di cloud untuk email ini. Lakukan Push terlebih dahulu.", StatusKind.INFO)
            PullResult.UpToDate ->
                SyncStatus("Sudah yang terbaru.", StatusKind.OK)
            PullResult.LocalAhead ->
                SyncStatus("Ada perubahan lokal yang belum di-upload, sedangkan cloud tidak lebih baru. Lakukan Push.", StatusKind.INFO)
            is PullResult.Applied -> {
                container.vaultChanged.tryEmit(Unit)
                SyncStatus("Download berhasil: ${result.itemCount} entri (revisi ${result.revision}).", StatusKind.OK)
            }
            is PullResult.Conflict -> {
                _ui.update { it.copy(pullConflict = result) }
                null
            }
        }
    }

    fun dismissConflicts() {
        _ui.update {
            it.copy(
                pushConflict = null,
                pullConflict = null,
                syncStatus = SyncStatus("Dibatalkan. Tidak ada perubahan.", StatusKind.INFO)
            )
        }
    }
}
