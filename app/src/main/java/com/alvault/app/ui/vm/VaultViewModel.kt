package com.alvault.app.ui.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alvault.app.AppContainer
import com.alvault.app.core.backup.BackupService
import com.alvault.app.core.crypto.Bytes
import com.alvault.app.core.crypto.CryptoException
import com.alvault.app.core.model.MergeMode
import com.alvault.app.core.model.MergeResult
import com.alvault.app.core.model.VaultItem
import com.alvault.app.core.model.VaultMerge
import com.alvault.app.core.session.Hardening
import com.alvault.app.core.session.SessionLockedException
import com.alvault.app.core.vault.VaultQuery
import com.alvault.app.ui.UiLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class VaultUi(
    val loading: Boolean = false,
    /** true setelah brankas berhasil didekripsi ke RAM pada sesi ini. */
    val loaded: Boolean = false,
    val items: List<VaultItem> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val loadError: String? = null
) {
    /** Hasil pencarian + filter kategori (dihitung ulang tiap akses; daftar kecil). */
    val visible: List<VaultItem> get() = VaultQuery.filter(items, query, category)
}

/** Isi backup `.aegis` yang SUDAH terenkripsi, menunggu pengguna memilih lokasi simpan. */
class ExportFile(val fileName: String, val content: String)

/**
 * Isi brankas di RAM (hasil dekripsi) + operasi CRUD, impor/ekspor `.aegis`, dan clipboard.
 * Semua perubahan diserialisasi lewat [writeLock] dan langsung dienkripsi ulang ke penyimpanan.
 * State dikosongkan seketika saat sesi terkunci.
 */
class VaultViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(VaultUi())
    val ui: StateFlow<VaultUi> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** true selama impor/ekspor (PBKDF2 600.000 iterasi) berjalan. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _pendingExport = MutableStateFlow<ExportFile?>(null)
    val pendingExport: StateFlow<ExportFile?> = _pendingExport.asStateFlow()

    /** Teks berkas `.aegis` yang sudah dibaca, menunggu password backup dari pengguna. */
    private val _pendingImport = MutableStateFlow<String?>(null)
    val pendingImport: StateFlow<String?> = _pendingImport.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val writeLock = Mutex()

    private val sessionListener: (Boolean) -> Unit = { unlocked -> if (!unlocked) clear() }

    init {
        container.session.addListener(sessionListener)
        // Setelah Pull dari cloud mengganti isi lokal, muat ulang.
        viewModelScope.launch {
            container.vaultChanged.collect {
                if (container.session.isUnlocked) loadInternal()
            }
        }
    }

    override fun onCleared() {
        container.session.removeListener(sessionListener)
    }

    private fun post(message: String) {
        _messages.tryEmit(message)
    }

    // ---- Muat / kosongkan ----

    fun load() {
        viewModelScope.launch { loadInternal() }
    }

    private suspend fun loadInternal() {
        writeLock.withLock {
            _ui.update { it.copy(loading = true, loadError = null) }
            try {
                val loaded = withContext(Dispatchers.IO) {
                    container.session.withKey { key -> container.vaultService.load(key) }
                }
                // Sesi bisa terkunci saat dekripsi berjalan: jangan isi ulang RAM setelah clear().
                if (container.session.isUnlocked) {
                    _ui.update { it.copy(items = loaded.items, loading = false, loaded = true) }
                } else {
                    _ui.update { it.copy(loading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionLockedException) {
                _ui.update { it.copy(loading = false) }
            } catch (e: CryptoException) {
                _ui.update {
                    it.copy(
                        loading = false,
                        loadError = "Brankas tidak bisa didekripsi dengan kunci ini. Data mungkin rusak."
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, loadError = "Gagal membaca brankas dari penyimpanan.") }
            }
        }
    }

    /** Membuang semua isi dari RAM (dipanggil saat sesi terkunci atau direset). */
    fun clear() {
        _ui.value = VaultUi()
        _pendingExport.value = null
        _pendingImport.value = null
        _importError.value = null
        _busy.value = false
    }

    // ---- Pencarian ----

    fun setQuery(query: String) {
        _ui.update { it.copy(query = query) }
    }

    fun setCategory(category: String?) {
        _ui.update { it.copy(category = category) }
    }

    fun find(id: String): VaultItem? = VaultQuery.find(_ui.value.items, id)

    // ---- Tulis ----

    /**
     * Menerapkan [transform] atas daftar di RAM lalu mengenkripsi & menyimpannya.
     * @return true bila tersimpan; pada kegagalan pesan dikirim ke [messages] dan daftar tidak berubah.
     */
    private suspend fun persist(transform: (List<VaultItem>) -> List<VaultItem>): Boolean =
        writeLock.withLock {
            try {
                val next = transform(_ui.value.items)
                withContext(Dispatchers.IO) {
                    container.session.withKey { key -> container.vaultService.save(key, next) }
                }
                // Tersimpan di disk; RAM hanya diperbarui bila sesi masih terbuka (lihat loadInternal).
                if (container.session.isUnlocked) _ui.update { it.copy(items = next) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionLockedException) {
                post(e.message ?: "Sesi terkunci. Perubahan tidak disimpan.")
                false
            } catch (e: CryptoException) {
                post(e.message ?: "Perubahan tidak bisa dienkripsi.")
                false
            } catch (e: Exception) {
                post("Gagal menyimpan. Perubahan tidak tersimpan.")
                false
            }
        }

    /** Menambah atau mengubah entri. [onResult] dipanggil di main thread. */
    fun save(item: VaultItem, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = persist { VaultQuery.upsert(it, item) }
            if (ok) post("Entri disimpan.")
            onResult(ok)
        }
    }

    fun delete(id: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = persist { VaultQuery.delete(it, id) }
            if (ok) post("Entri dihapus.")
            onResult(ok)
        }
    }

    // ---- Clipboard ----

    fun copyPassword(password: String) {
        container.clipboard.copySensitive(password)
        post("Password disalin. Clipboard dibersihkan otomatis dalam ${Hardening.CLIPBOARD_CLEAR_MS / 1000} detik.")
    }

    fun copyText(label: String, text: String) {
        container.clipboard.copyPlain(label, text)
        post("$label disalin.")
    }

    // ---- Ekspor .aegis ----

    /** Mengenkripsi seluruh brankas dengan [password] backup; hasilnya menunggu lokasi simpan dari UI. */
    fun prepareExport(password: String) {
        if (_busy.value) return
        val chars = password.toCharArray()
        val snapshot = _ui.value.items
        viewModelScope.launch {
            _busy.value = true
            try {
                val content = withContext(Dispatchers.Default) { BackupService.export(snapshot, chars) }
                _pendingExport.value = ExportFile(BackupService.suggestedFileName(System.currentTimeMillis()), content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                post("Gagal membuat backup.")
            } finally {
                Bytes.wipe(chars)
                _busy.value = false
            }
        }
    }

    /** Menulis backup terenkripsi ke [uri] pilihan pengguna; null = dibatalkan. */
    fun writeExport(uri: Uri?) {
        val file = _pendingExport.value
        _pendingExport.value = null
        if (uri == null || file == null) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val out = container.appContext.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IllegalStateException("Tidak bisa menulis ke lokasi yang dipilih.")
                    out.use { it.write(file.content.toByteArray(Charsets.UTF_8)) }
                }
                post("Backup .aegis disimpan.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                post("Gagal menyimpan berkas backup.")
            }
        }
    }

    // ---- Impor .aegis ----

    /** Membaca berkas `.aegis` dari [uri]; setelah itu UI meminta password backup. */
    fun readImport(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val text = withContext(Dispatchers.IO) {
                    val bytes = container.appContext.contentResolver.openInputStream(uri)?.use {
                        UiLogic.readLimited(it, BackupService.MAX_IMPORT_BYTES)
                    }
                    bytes?.toString(Charsets.UTF_8)
                }
                if (text == null) {
                    post("Berkas tidak bisa dibaca atau terlalu besar (maksimal 20 MB).")
                } else {
                    _importError.value = null
                    _pendingImport.value = text
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                post("Gagal membaca berkas.")
            } finally {
                _busy.value = false
            }
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
        _importError.value = null
    }

    /** Mendekripsi berkas yang sudah dibaca lalu menggabungkan/menggantinya ke brankas. */
    fun applyImport(password: String, mode: MergeMode) {
        if (_busy.value) return
        val content = _pendingImport.value ?: return
        val chars = password.toCharArray()
        viewModelScope.launch {
            _busy.value = true
            _importError.value = null
            try {
                val incoming = withContext(Dispatchers.Default) { BackupService.decrypt(content, chars) }
                var summary: MergeResult? = null
                val saved = persist { current ->
                    val outcome = VaultMerge.apply(mode, current, incoming)
                    summary = outcome.result
                    outcome.items
                }
                val result = summary
                if (saved && result != null) {
                    _pendingImport.value = null
                    post(UiLogic.describeImport(mode, result))
                } else if (!saved) {
                    _importError.value = "Gagal menyimpan hasil impor. Brankas tidak berubah."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CryptoException) {
                _importError.value = e.message ?: "Password salah atau berkas rusak."
            } catch (e: Exception) {
                _importError.value = "Gagal mengimpor berkas."
            } finally {
                Bytes.wipe(chars)
                _busy.value = false
            }
        }
    }
}
