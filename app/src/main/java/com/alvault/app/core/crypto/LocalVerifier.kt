package com.alvault.app.core.crypto

import org.bouncycastle.crypto.digests.SHA3Digest
import java.security.MessageDigest

/**
 * Verifier login lokal: `SHA3-512(LocalAuthHash + email + localPepper)`, disimpan sebagai Base64.
 * Memakai kelas BouncyCastle langsung (bukan lewat provider) karena provider "BC" bawaan Android
 * tidak lengkap dan tidak boleh ditimpa.
 */
object LocalVerifier {
    /** Pepper statis aplikasi (bagian dari spesifikasi `localPepper`). */
    const val PEPPER = "alvault.local.pepper.v1"

    fun compute(localAuthHash: String, email: String, pepper: String = PEPPER): String {
        val input = Bytes.utf8(localAuthHash + MasterKeyDeriver.normalizeEmail(email) + pepper)
        val digest = SHA3Digest(512)
        digest.update(input, 0, input.size)
        val output = ByteArray(digest.digestSize)
        digest.doFinal(output, 0)
        Bytes.wipe(input)
        return Base64Codec.encode(output)
    }

    /** Perbandingan waktu-konstan. */
    fun matches(stored: String, computed: String): Boolean =
        MessageDigest.isEqual(Bytes.utf8(stored), Bytes.utf8(computed))
}
