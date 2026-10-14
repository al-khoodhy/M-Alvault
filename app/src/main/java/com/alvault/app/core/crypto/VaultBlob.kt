package com.alvault.app.core.crypto

/**
 * Vault blob (penyimpanan lokal & sinkronisasi cloud):
 * `Base64( IV[12B] + Ciphertext + GCM_Tag[16B] )`, AES-256-GCM dengan EncryptionKey.
 */
object VaultBlob {

    fun seal(encryptionKey: ByteArray, plaintext: ByteArray): String {
        val iv = AesGcm.randomBytes(AesGcm.IV_SIZE)
        val encrypted = AesGcm.encrypt(encryptionKey, iv, plaintext)
        return Base64Codec.encode(iv + encrypted)
    }

    /** @throws CryptoException jika format salah, kunci salah, atau data dimodifikasi. */
    fun open(encryptionKey: ByteArray, blob: String): ByteArray {
        val raw = Base64Codec.decode(blob.trim())
        if (raw.size < AesGcm.IV_SIZE + AesGcm.TAG_SIZE) {
            throw CryptoException("Blob brankas terlalu pendek.")
        }
        val iv = raw.copyOfRange(0, AesGcm.IV_SIZE)
        val ciphertext = raw.copyOfRange(AesGcm.IV_SIZE, raw.size)
        return AesGcm.decrypt(encryptionKey, iv, ciphertext)
    }
}
