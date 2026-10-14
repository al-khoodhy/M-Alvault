package com.alvault.app.core.sync

import com.alvault.app.core.crypto.CryptoException
import com.alvault.app.core.crypto.VaultBlob
import com.alvault.app.core.crypto.Bytes
import com.alvault.app.core.auth.AccountStore
import com.alvault.app.core.model.MergeMode
import com.alvault.app.core.model.VaultJson
import com.alvault.app.core.model.VaultMerge
import com.alvault.app.core.net.HttpExecutor
import com.alvault.app.core.vault.VaultService
import java.time.Instant

/** Pengaturan sinkronisasi (produksi: EncryptedSharedPreferences). */
interface SyncSettingsStore {
    var supabaseEnabled: Boolean
    var supabaseUrl: String
    var supabaseKey: String

    /** Revisi cloud terakhir yang sudah diselaraskan (push/pull) oleh perangkat ini. */
    var lastSyncedRevision: Long
}

sealed interface PushResult {
    data class Success(val revision: Long, val itemCount: Int) : PushResult

    /** Cloud berisi revisi yang belum pernah ditarik perangkat ini; push akan menimpanya. */
    data class Conflict(val remoteRevision: Long) : PushResult
}

enum class PullStrategy {
    /** Ganti isi lokal dengan isi cloud. */
    REPLACE,

    /** Gabungkan berdasarkan id; `lastModified` yang lebih baru menang. */
    MERGE
}

sealed interface PullResult {
    object NoRemote : PullResult

    object UpToDate : PullResult

    /** Cloud tidak lebih baru, tetapi ada perubahan lokal yang belum di-push. */
    object LocalAhead : PullResult

    data class Applied(val strategy: PullStrategy, val itemCount: Int, val revision: Long) : PullResult

    /** Lokal dan cloud sama-sama berubah; pengguna harus memilih [PullStrategy]. */
    data class Conflict(val remoteRevision: Long, val remoteItems: Int, val localItems: Int) : PullResult
}

/** Cloud tidak bisa didekripsi dengan kunci di RAM (kunci/salt berbeda, atau data rusak). */
class RemoteDecryptException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Orkestrasi Push/Pull ke Supabase. Blob lokal sudah berformat vault blob sehingga di-push apa adanya;
 * server hanya melihat ciphertext. Semua metode blocking.
 */
class SyncService(
    private val settings: SyncSettingsStore,
    private val vault: VaultService,
    private val accounts: AccountStore,
    private val http: HttpExecutor,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private fun config() = SupabaseConfig(settings.supabaseUrl, settings.supabaseKey)

    private fun client(): SupabaseClient {
        val config = config()
        config.validate()?.let { throw SupabaseException(it) }
        return SupabaseClient(http, config)
    }

    private fun email(): String =
        accounts.userEmail ?: throw SupabaseException("Belum ada akun lokal.")

    fun testConnection(url: String, key: String): PingResult {
        val config = SupabaseConfig(url, key)
        config.validate()?.let { throw SupabaseException(it) }
        return SupabaseClient(http, config).ping()
    }

    /** Push (Upload). Tanpa [force], push dihentikan bila cloud punya revisi yang belum ditarik. */
    fun push(force: Boolean = false): PushResult {
        val client = client()
        val email = email()
        val local = vault.peek() ?: throw SupabaseException("Brankas lokal belum ada.")

        val remote = client.fetch(email)
        if (remote != null && remote.revision > settings.lastSyncedRevision && !force) {
            return PushResult.Conflict(remote.revision)
        }
        val newRevision = (remote?.revision ?: 0L) + 1L
        client.upsert(email, local.blob, local.itemCount, newRevision, Instant.ofEpochMilli(clock()).toString())

        vault.setRevision(newRevision)
        settings.lastSyncedRevision = newRevision
        return PushResult.Success(newRevision, local.itemCount)
    }

    /**
     * Pull (Download). [encryptionKey] adalah salinan milik pemanggil.
     * Dekripsi dilakukan dulu: blob yang tidak bisa didekripsi tidak pernah menyentuh penyimpanan lokal.
     * @param strategy dipakai hanya bila terjadi konflik; null = kembalikan [PullResult.Conflict].
     */
    fun pull(encryptionKey: ByteArray, strategy: PullStrategy? = null): PullResult {
        val client = client()
        val remote = client.fetch(email()) ?: return PullResult.NoRemote
        val local = vault.peek()
        val lastSynced = settings.lastSyncedRevision
        val localChanged = local != null && local.itemCount > 0 && local.revision != lastSynced

        if (remote.revision <= lastSynced) {
            return if (localChanged) PullResult.LocalAhead else PullResult.UpToDate
        }
        if (localChanged && strategy == null) {
            return PullResult.Conflict(remote.revision, remoteItemCount(remote.encryptedData, encryptionKey), local!!.itemCount)
        }

        val remoteItems = decryptRemote(remote.encryptedData, encryptionKey)
        val chosen = if (localChanged) strategy!! else PullStrategy.REPLACE
        return when (chosen) {
            PullStrategy.REPLACE -> {
                vault.replaceWithBlob(remote.encryptedData, remoteItems.size, remote.revision)
                settings.lastSyncedRevision = remote.revision
                PullResult.Applied(PullStrategy.REPLACE, remoteItems.size, remote.revision)
            }
            PullStrategy.MERGE -> {
                val localItems = vault.load(encryptionKey).items
                val merged = VaultMerge.apply(MergeMode.MERGE, localItems, remoteItems).items
                // Hasil gabungan bukan salinan cloud: revisi lokal = revisi cloud + 1 supaya terdeteksi
                // sebagai perubahan yang belum di-push.
                vault.save(encryptionKey, merged, revision = remote.revision + 1L)
                settings.lastSyncedRevision = remote.revision
                PullResult.Applied(PullStrategy.MERGE, merged.size, remote.revision + 1L)
            }
        }
    }

    private fun remoteItemCount(blob: String, key: ByteArray): Int = decryptRemote(blob, key).size

    private fun decryptRemote(blob: String, key: ByteArray): List<com.alvault.app.core.model.VaultItem> {
        val plain = try {
            VaultBlob.open(key, blob)
        } catch (e: CryptoException) {
            throw RemoteDecryptException(
                "Data di cloud tidak bisa didekripsi dengan kunci perangkat ini. Kemungkinan dienkripsi dengan " +
                    "kunci/salt lain (mis. dari perangkat atau aplikasi web berbeda). Gunakan Impor .aegis untuk migrasi.",
                e
            )
        }
        try {
            return VaultJson.decode(plain)
        } catch (e: CryptoException) {
            throw RemoteDecryptException("Isi data cloud bukan brankas yang valid.", e)
        } finally {
            Bytes.wipe(plain)
        }
    }
}
