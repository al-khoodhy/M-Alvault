package com.alvault.app.core.vault

import com.alvault.app.core.crypto.AesGcm
import com.alvault.app.core.crypto.Bytes
import com.alvault.app.core.crypto.CryptoException
import com.alvault.app.core.crypto.VaultBlob
import com.alvault.app.core.model.VaultItem
import com.alvault.app.core.model.VaultJson

/**
 * Satu-satunya record brankas di penyimpanan lokal. [blob] berformat
 * `Base64( IV[12B] + Ciphertext + GCM_Tag[16B] )` (identik dengan kolom `encrypted_data` di Supabase).
 */
data class StoredVault(
    val blob: String,
    val itemCount: Int,
    /** Naik 1 setiap perubahan lokal; setelah sync disamakan dengan revisi cloud. */
    val revision: Long,
    val updatedAt: Long
)

/** Penyimpanan blob terenkripsi. Implementasi produksi memakai Room. Semua panggilan bersifat blocking. */
interface VaultBlobStore {
    fun load(): StoredVault?
    fun save(vault: StoredVault)
    fun clear()
}

class LoadedVault(val items: List<VaultItem>, val revision: Long)

/**
 * Baca/tulis brankas terenkripsi. Layanan ini TIDAK memegang kunci: pemanggil memberikan
 * `EncryptionKey` per operasi (salinan yang dihapus sendiri setelah dipakai).
 */
class VaultService(
    private val store: VaultBlobStore,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun peek(): StoredVault? = store.load()

    /** Membuat brankas kosong awal (`[]`) terenkripsi dengan [key]. */
    fun initialize(key: ByteArray): StoredVault = save(key, emptyList(), revision = 1L)

    /** @throws CryptoException bila kunci salah atau blob rusak. */
    fun load(key: ByteArray): LoadedVault {
        val stored = store.load() ?: return LoadedVault(emptyList(), 0L)
        val plain = VaultBlob.open(key, stored.blob)
        try {
            return LoadedVault(VaultJson.decode(plain), stored.revision)
        } finally {
            Bytes.wipe(plain)
        }
    }

    /** Enkripsi [items] dengan IV baru lalu simpan. [revision] null = revisi sekarang + 1. */
    fun save(key: ByteArray, items: List<VaultItem>, revision: Long? = null): StoredVault {
        requireUsableKey(key)
        val plain = VaultJson.encode(items)
        try {
            val blob = VaultBlob.seal(key, plain)
            val next = revision ?: ((store.load()?.revision ?: 0L) + 1L)
            val stored = StoredVault(blob, items.size, next, clock())
            store.save(stored)
            return stored
        } finally {
            Bytes.wipe(plain)
        }
    }

    /** Mengganti isi lokal dengan blob yang SUDAH terverifikasi bisa didekripsi (dipakai pull dari cloud). */
    fun replaceWithBlob(blob: String, itemCount: Int, revision: Long): StoredVault {
        val stored = StoredVault(blob, itemCount, revision, clock())
        store.save(stored)
        return stored
    }

    fun setRevision(revision: Long) {
        val current = store.load() ?: return
        store.save(current.copy(revision = revision))
    }

    fun clear() = store.clear()

    /**
     * Mencegah brankas terenkripsi dengan kunci semu: kunci harus 32 byte dan tidak boleh nol semua.
     * Kunci nol semua berarti sesi sudah dikunci (kunci di-wipe) saat operasi simpan masih berjalan;
     * menyimpan dengan kunci itu akan membuat brankas tidak bisa dibuka lagi selamanya.
     */
    private fun requireUsableKey(key: ByteArray) {
        if (key.size != AesGcm.KEY_SIZE || key.all { it == 0.toByte() }) {
            throw CryptoException("Sesi terkunci; perubahan tidak disimpan.")
        }
    }
}
