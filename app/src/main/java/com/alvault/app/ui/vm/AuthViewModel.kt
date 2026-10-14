package com.alvault.app.ui.vm

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alvault.app.AppContainer
import com.alvault.app.core.auth.UnlockResult
import com.alvault.app.core.crypto.Bytes
import com.alvault.app.platform.BiometricKeyVault
import com.alvault.app.platform.BiometricResult
import com.alvault.app.ui.UiLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthState { LOADING, NEEDS_REGISTRATION, LOCKED, UNLOCKED }

data class AuthUi(
    val state: AuthState = AuthState.LOADING,
    val email: String = "",
    /** true selama Argon2id berjalan (butuh ~1 detik dan 64 MB). */
    val busy: Boolean = false,
    val error: String? = null,
    /** Berakhirnya jeda setelah terlalu banyak salah password (epoch millis, jam dinding); 0 = tidak ada jeda. */
    val lockedUntil: Long = 0L,
    val biometricReady: Boolean = false
)

/**
 * Registrasi lokal, login (master password / biometrik), penguncian, dan reset.
 * Argon2id berjalan di `Dispatchers.Default`; master password dipegang sebagai CharArray lalu di-wipe.
 */
class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(AuthUi())
    val ui: StateFlow<AuthUi> = _ui.asStateFlow()

    // Dipanggil dari thread mana pun (ticker auto-lock, IO, main); MutableStateFlow aman untuk itu.
    private val sessionListener: (Boolean) -> Unit = { unlocked ->
        if (!unlocked) onSessionLocked()
    }

    init {
        container.session.addListener(sessionListener)
        refresh()
    }

    override fun onCleared() {
        container.session.removeListener(sessionListener)
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accounts = container.accountService
                if (!accounts.hasAccount()) {
                    _ui.update { it.copy(state = AuthState.NEEDS_REGISTRATION) }
                    return@launch
                }
                val email = accounts.email.orEmpty()
                val lockedUntil = container.securePrefs.lockoutUntil
                val bio = biometricReady()
                _ui.update {
                    it.copy(
                        state = if (container.session.isUnlocked) AuthState.UNLOCKED else AuthState.LOCKED,
                        email = email,
                        lockedUntil = lockedUntil,
                        biometricReady = bio
                    )
                }
            } catch (e: Exception) {
                // Penyimpanan aman (Keystore) tidak bisa dibuka: jangan crash, jangan pura-pura belum punya akun.
                _ui.update {
                    it.copy(
                        state = AuthState.LOCKED,
                        error = "Penyimpanan aman perangkat tidak bisa dibuka. Coba lagi; bila terus berulang, hapus data aplikasi lewat pengaturan Android."
                    )
                }
            }
        }
    }

    private fun biometricReady(): Boolean =
        try {
            BiometricKeyVault.isAvailable(container.appContext) && container.biometric.isEnabled
        } catch (_: Exception) {
            false
        }

    private fun onSessionLocked() {
        _ui.update {
            if (it.state == AuthState.UNLOCKED) it.copy(state = AuthState.LOCKED, busy = false, error = null) else it
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ready = biometricReady()
            _ui.update { it.copy(biometricReady = ready) }
        }
    }

    private fun fail(message: String) {
        _ui.update { it.copy(busy = false, error = message) }
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }

    /** Registrasi lokal pertama kali. Berhasil -> langsung terbuka. */
    fun register(email: String, password: String, confirm: String) {
        if (_ui.value.busy) return
        UiLogic.registrationError(email, password, confirm)?.let { fail(it); return }
        _ui.update { it.copy(busy = true, error = null) }

        val chars = password.toCharArray()
        viewModelScope.launch(Dispatchers.Default) {
            var key: ByteArray? = null
            try {
                key = container.accountService.register(email, chars)
                container.session.unlock(key)
                val saved = container.accountService.email.orEmpty()
                _ui.value = AuthUi(state = AuthState.UNLOCKED, email = saved)
            } catch (e: IllegalArgumentException) {
                fail(e.message ?: "Isian tidak valid.")
            } catch (e: IllegalStateException) {
                fail(e.message ?: "Gagal membuat brankas.")
            } catch (e: Exception) {
                fail("Gagal membuat brankas. Coba lagi.")
            } finally {
                Bytes.wipe(chars)
                Bytes.wipe(key)
            }
        }
    }

    /** Login dengan master password. */
    fun unlock(password: String) {
        if (_ui.value.busy) return
        if (password.isEmpty()) {
            fail("Masukkan master password.")
            return
        }
        _ui.update { it.copy(busy = true, error = null) }

        val chars = password.toCharArray()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                when (val result = container.accountService.unlock(chars)) {
                    is UnlockResult.Success -> {
                        try {
                            container.session.unlock(result.encryptionKey)
                        } finally {
                            Bytes.wipe(result.encryptionKey)
                        }
                        _ui.update { it.copy(state = AuthState.UNLOCKED, busy = false, error = null, lockedUntil = 0L) }
                    }
                    is UnlockResult.WrongPassword -> {
                        val until = if (result.lockoutMillis > 0) System.currentTimeMillis() + result.lockoutMillis else 0L
                        _ui.update {
                            it.copy(
                                busy = false,
                                lockedUntil = until,
                                error = if (until > 0L) {
                                    "Master password salah. Terlalu banyak percobaan: tunggu ${UiLogic.formatRemaining(result.lockoutMillis)}."
                                } else {
                                    "Master password salah (percobaan ke-${result.failedAttempts})."
                                }
                            )
                        }
                    }
                    is UnlockResult.LockedOut -> {
                        _ui.update {
                            it.copy(
                                busy = false,
                                lockedUntil = System.currentTimeMillis() + result.remainingMillis,
                                error = "Terlalu banyak percobaan. Coba lagi dalam ${UiLogic.formatRemaining(result.remainingMillis)}."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                fail("Gagal membuka brankas. Coba lagi.")
            } finally {
                Bytes.wipe(chars)
            }
        }
    }

    /** Login dengan sidik jari/wajah. Hasil dikembalikan di main thread oleh BiometricPrompt. */
    fun unlockWithBiometric(activity: FragmentActivity) {
        val current = _ui.value
        if (current.busy || !current.biometricReady) return
        container.biometric.unlock(activity) { result ->
            when (result) {
                is BiometricResult.Unlocked -> {
                    try {
                        container.session.unlock(result.encryptionKey)
                    } finally {
                        Bytes.wipe(result.encryptionKey)
                    }
                    _ui.update { it.copy(state = AuthState.UNLOCKED, busy = false, error = null, lockedUntil = 0L) }
                }
                is BiometricResult.Failed -> {
                    _ui.update {
                        it.copy(
                            error = result.message,
                            biometricReady = if (result.invalidated) false else it.biometricReady
                        )
                    }
                }
                BiometricResult.Cancelled, BiometricResult.Enabled -> Unit
            }
        }
    }

    /** Menghapus kunci dari RAM sekarang. */
    fun lock() {
        container.session.lock()
    }

    /** Menghapus SEMUA data lokal (brankas, akun, kunci biometrik, konfigurasi). Tidak bisa dibatalkan. */
    fun resetAll() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                container.biometric.disable()
                container.accountService.resetAll()
            } catch (e: Exception) {
                _ui.update { it.copy(error = "Gagal menghapus data. Coba lagi.") }
                return@launch
            }
            container.session.lock()
            _ui.value = AuthUi(state = AuthState.NEEDS_REGISTRATION)
        }
    }
}
