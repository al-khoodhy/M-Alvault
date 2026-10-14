package com.alvault.app.core.backup

import com.alvault.app.core.crypto.AegisCrypto
import com.alvault.app.core.crypto.AegisFile
import com.alvault.app.core.crypto.Bytes
import com.alvault.app.core.crypto.CryptoException
import com.alvault.app.core.model.VaultItem
import com.alvault.app.core.model.VaultJson
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Impor/ekspor berkas backup `.aegis` (interoperabel dengan aplikasi web):
 * `VaultItem[]` -> JSON UTF-8 -> PBKDF2-HMAC-SHA256 (600.000) -> AES-256-GCM -> `{v,s,i,c}`.
 * Password backup bersifat terpisah dari master password.
 *
 * Semua metode bersifat CPU-berat (PBKDF2 600.000 iterasi): panggil dari dispatcher Default/IO.
 */
object BackupService {
    const val MIME_TYPE = "application/json"
    const val EXTENSION = "aegis"

    /** Batas ukuran berkas impor (byte). Brankas ribuan entri masih jauh di bawah ini. */
    const val MAX_IMPORT_BYTES = 20 * 1024 * 1024

    fun suggestedFileName(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(zone).format(Instant.ofEpochMilli(nowMillis))
        return "alvault-backup-$date.$EXTENSION"
    }

    /** @return isi berkas `.aegis` (JSON string) yang siap ditulis ke penyimpanan. */
    fun export(
        items: List<VaultItem>,
        password: CharArray,
        iterations: Int = AegisCrypto.KDF_ITERATIONS
    ): String {
        require(password.isNotEmpty()) { "Password backup tidak boleh kosong." }
        val plain = VaultJson.encode(items)
        try {
            return AegisFile.toJson(AegisCrypto.encrypt(plain, password, iterations))
        } finally {
            Bytes.wipe(plain)
        }
    }

    /**
     * Membuka berkas `.aegis` (dari web maupun Android).
     * @throws CryptoException bila format salah, password salah, atau berkas rusak.
     */
    fun decrypt(
        content: String,
        password: CharArray,
        iterations: Int = AegisCrypto.KDF_ITERATIONS
    ): List<VaultItem> {
        val payload = AegisFile.parse(content)
        val plain = AegisCrypto.decrypt(payload, password, iterations)
        try {
            return VaultJson.decode(plain)
        } finally {
            Bytes.wipe(plain)
        }
    }
}
