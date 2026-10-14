package com.alvault.app.platform

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.alvault.app.core.crypto.AesGcm
import com.alvault.app.core.crypto.Base64Codec
import com.alvault.app.core.crypto.Bytes
import com.alvault.app.data.SecurePrefs
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed interface BiometricResult {
    /** [encryptionKey] adalah salinan milik pemanggil (wajib di-wipe setelah dipakai). */
    class Unlocked(val encryptionKey: ByteArray) : BiometricResult

    object Enabled : BiometricResult

    /** Pengguna membatalkan; bukan kesalahan, tidak perlu pesan. */
    object Cancelled : BiometricResult

    class Failed(val message: String, val invalidated: Boolean = false) : BiometricResult
}

/**
 * Login biometrik (sidik jari/wajah) berbasis Android Keystore:
 *
 * - Kunci AES-256-GCM di Keystore dengan `setUserAuthenticationRequired(true)` dan biometrik KUAT per-penggunaan
 *   (hanya bisa dipakai lewat `BiometricPrompt` + `CryptoObject`), dan dibatalkan bila biometrik baru didaftarkan.
 * - `EncryptionKey` dibungkus (di-enkripsi) kunci itu; hanya bungkusan + IV yang disimpan di
 *   EncryptedSharedPreferences. Setelah autentikasi berhasil, Keystore mendekripsi langsung ke RAM.
 * - Master password plaintext tidak pernah disimpan.
 */
class BiometricKeyVault(private val prefs: SecurePrefs) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Biometrik aktif DAN kunci Keystore-nya masih ada. Blocking ringan (baca prefs/Keystore). */
    val isEnabled: Boolean
        get() = prefs.biometricEnabled && try {
            keyStore.containsAlias(KEY_ALIAS)
        } catch (_: Exception) {
            false
        }

    fun disable() {
        prefs.clearBiometric()
        try {
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
            // Entri sudah tidak ada atau Keystore tidak tersedia: tidak ada yang perlu dibersihkan.
        }
    }

    /** Mengikat [encryptionKey] ke biometrik perangkat. Tidak menyimpan referensi ke [encryptionKey]. */
    fun enable(activity: FragmentActivity, encryptionKey: ByteArray, onResult: (BiometricResult) -> Unit) {
        val keyCopy = encryptionKey.copyOf()
        val cipher = try {
            deleteKeyOnly()
            createKey()
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        } catch (_: Exception) {
            Bytes.wipe(keyCopy)
            onResult(BiometricResult.Failed("Perangkat ini tidak bisa membuat kunci biometrik. Pastikan sidik jari atau wajah sudah terdaftar."))
            return
        }

        authenticate(
            activity, cipher,
            title = "Aktifkan biometrik",
            subtitle = "Pindai untuk mengikat kunci brankas ke perangkat ini",
            negative = "Batal"
        ) { auth ->
            try {
                when (auth) {
                    is AuthResult.Success -> {
                        val wrapped = auth.cipher.doFinal(keyCopy)
                        prefs.saveBiometric(Base64Codec.encode(auth.cipher.iv), Base64Codec.encode(wrapped))
                        onResult(BiometricResult.Enabled)
                    }
                    AuthResult.Cancelled -> {
                        deleteKeyOnly()
                        onResult(BiometricResult.Cancelled)
                    }
                    is AuthResult.Error -> {
                        deleteKeyOnly()
                        onResult(BiometricResult.Failed(auth.message))
                    }
                }
            } catch (_: Exception) {
                disable()
                onResult(BiometricResult.Failed("Gagal mengaktifkan biometrik."))
            } finally {
                Bytes.wipe(keyCopy)
            }
        }
    }

    /** Membuka kunci dengan biometrik; hasilnya lewat [onResult] (di main thread). */
    fun unlock(activity: FragmentActivity, onResult: (BiometricResult) -> Unit) {
        val iv = prefs.biometricIv
        val wrapped = prefs.biometricWrappedKey
        if (!prefs.biometricEnabled || iv == null || wrapped == null) {
            onResult(BiometricResult.Failed("Biometrik belum diaktifkan."))
            return
        }

        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(AesGcm.TAG_SIZE * 8, Base64Codec.decode(iv)))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            disable()
            onResult(
                BiometricResult.Failed(
                    "Data biometrik di perangkat berubah, jadi biometrik dinonaktifkan. " +
                        "Masuk dengan master password lalu aktifkan lagi di Pengaturan.",
                    invalidated = true
                )
            )
            return
        } catch (_: Exception) {
            disable()
            onResult(BiometricResult.Failed("Kunci biometrik tidak ditemukan, jadi biometrik dinonaktifkan. Masuk dengan master password.", invalidated = true))
            return
        }

        authenticate(
            activity, cipher,
            title = "Buka AlVault",
            subtitle = "Pindai sidik jari atau wajah",
            negative = "Pakai master password"
        ) { auth ->
            when (auth) {
                is AuthResult.Success -> try {
                    val key = auth.cipher.doFinal(Base64Codec.decode(wrapped))
                    if (key.size == AesGcm.KEY_SIZE) {
                        onResult(BiometricResult.Unlocked(key))
                    } else {
                        Bytes.wipe(key)
                        onResult(BiometricResult.Failed("Data biometrik rusak. Masuk dengan master password."))
                    }
                } catch (_: Exception) {
                    onResult(BiometricResult.Failed("Gagal membuka kunci dengan biometrik. Masuk dengan master password."))
                }
                AuthResult.Cancelled -> onResult(BiometricResult.Cancelled)
                is AuthResult.Error -> onResult(BiometricResult.Failed(auth.message))
            }
        }
    }

    // ---- internal ----

    private sealed interface AuthResult {
        class Success(val cipher: Cipher) : AuthResult
        object Cancelled : AuthResult
        class Error(val message: String) : AuthResult
    }

    private fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        negative: String,
        onResult: (AuthResult) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authorized = result.cryptoObject?.cipher
                    if (authorized == null) {
                        onResult(AuthResult.Error("Autentikasi biometrik tidak mengembalikan kunci."))
                    } else {
                        onResult(AuthResult.Success(authorized))
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> onResult(AuthResult.Cancelled)

                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            onResult(AuthResult.Error("Terlalu banyak percobaan biometrik. Gunakan master password."))

                        else -> onResult(AuthResult.Error(errString.toString()))
                    }
                }
                // onAuthenticationFailed (jari tidak cocok) bukan hasil akhir: prompt tetap terbuka.
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negative)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun createKey() {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Otentikasi untuk SETIAP operasi kripto, hanya biometrik kuat (tanpa PIN/pola layar).
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec.build()) }
            .generateKey()
    }

    private fun secretKey(): SecretKey =
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: throw IllegalStateException("Kunci biometrik tidak ada.")

    private fun deleteKeyOnly() {
        try {
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
            // diabaikan: dibuat ulang pada enable()
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "alvault_biometric_wrap_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** true bila perangkat punya biometrik KUAT yang sudah terdaftar. */
        fun isAvailable(context: Context): Boolean =
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
