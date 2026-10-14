package com.alvault.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alvault.app.AppContainer
import com.alvault.app.core.model.VaultItem
import com.alvault.app.core.security.AuditReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

sealed interface AuditUi {
    object Idle : AuditUi

    data class Running(val done: Int, val total: Int) : AuditUi

    /** Laporan hanya berisi judul/username dan status; password tidak pernah ikut disimpan. */
    data class Done(val report: AuditReport, val checkedAt: Long) : AuditUi

    data class Failed(val message: String) : AuditUi
}

/** Audit keamanan: cek lemah lokal + HIBP k-anonymity (hanya 5 karakter awal SHA-1 yang dikirim). */
class AuditViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow<AuditUi>(AuditUi.Idle)
    val ui: StateFlow<AuditUi> = _ui.asStateFlow()

    private var job: Job? = null
    private val cancelRequested = AtomicBoolean(false)

    private val sessionListener: (Boolean) -> Unit = { unlocked -> if (!unlocked) reset() }

    init {
        container.session.addListener(sessionListener)
    }

    override fun onCleared() {
        container.session.removeListener(sessionListener)
    }

    fun run(items: List<VaultItem>) {
        if (_ui.value is AuditUi.Running) return
        if (items.isEmpty()) {
            _ui.value = AuditUi.Failed("Brankas masih kosong, tidak ada yang diperiksa.")
            return
        }
        cancelRequested.set(false)
        _ui.value = AuditUi.Running(0, 0)
        job = viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    container.auditor.run(
                        items = items,
                        shouldContinue = { !cancelRequested.get() },
                        onProgress = { done, total ->
                            if (!cancelRequested.get()) _ui.value = AuditUi.Running(done, total)
                        }
                    )
                }
                // Dibatalkan/terkunci saat berjalan: jangan tampilkan laporan setengah jadi.
                if (cancelRequested.get()) {
                    _ui.value = AuditUi.Idle
                } else {
                    _ui.value = AuditUi.Done(report, System.currentTimeMillis())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = AuditUi.Failed("Audit gagal dijalankan. Coba lagi.")
            }
        }
    }

    fun cancel() {
        cancelRequested.set(true)
        _ui.value = AuditUi.Idle
    }

    /** Membuang laporan (dipanggil saat sesi terkunci atau pengguna menutup hasil). */
    fun reset() {
        cancelRequested.set(true)
        job?.cancel()
        job = null
        _ui.value = AuditUi.Idle
    }
}
