package com.alvault.app.core.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM (`AES/GCM/NoPadding`), tag 128 bit.
 * Keluaran enkripsi = ciphertext || tag(16B), sama dengan WebCrypto `subtle.encrypt`.
 */
object AesGcm {
    const val KEY_SIZE = 32
    const val IV_SIZE = 12
    const val TAG_SIZE = 16
    private const val TAG_BITS = TAG_SIZE * 8

    private val secureRandom = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

    fun encrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        requireKey(key)
        require(iv.size == IV_SIZE) { "IV harus $IV_SIZE byte" }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            return cipher.doFinal(plaintext)
        } catch (e: GeneralSecurityException) {
            throw CryptoException("Enkripsi gagal.", e)
        }
    }

    /** @throws CryptoException jika kunci salah atau data rusak/dimodifikasi (tag GCM tidak cocok). */
    fun decrypt(key: ByteArray, iv: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
        requireKey(key)
        if (iv.size != IV_SIZE) throw CryptoException("IV tidak valid.")
        if (ciphertextWithTag.size < TAG_SIZE) throw CryptoException("Data terenkripsi terlalu pendek.")
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            return cipher.doFinal(ciphertextWithTag)
        } catch (e: GeneralSecurityException) {
            throw CryptoException("Dekripsi gagal: kunci/password salah atau data rusak.", e)
        }
    }

    private fun requireKey(key: ByteArray) {
        require(key.size == KEY_SIZE) { "Kunci AES-256 harus $KEY_SIZE byte" }
    }
}
