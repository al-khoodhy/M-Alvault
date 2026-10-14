package com.alvault.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.alvault.app.core.vault.StoredVault
import com.alvault.app.core.vault.VaultBlobStore

/** Baris tunggal berisi vault blob terenkripsi (`Base64(IV + Ciphertext + Tag)`). Tidak ada data mentah di sini. */
@Entity(tableName = "vault_blob")
data class VaultBlobEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "encrypted_data") val encryptedData: String,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_blob WHERE id = 1")
    fun get(): VaultBlobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: VaultBlobEntity)

    @Query("DELETE FROM vault_blob")
    fun clear()
}

/**
 * Sengaja TANPA `fallbackToDestructiveMigration()`: brankas pengguna tidak boleh terhapus diam-diam
 * hanya karena skema berubah. Perubahan skema di masa depan wajib menyertakan Migration eksplisit.
 */
@Database(entities = [VaultBlobEntity::class], version = 1, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}

/** Implementasi [VaultBlobStore] di atas Room. Blocking: panggil dari dispatcher IO. */
class RoomVaultBlobStore(private val dao: VaultDao) : VaultBlobStore {
    override fun load(): StoredVault? = dao.get()?.let {
        StoredVault(it.encryptedData, it.itemCount, it.revision, it.updatedAt)
    }

    override fun save(vault: StoredVault) {
        dao.upsert(VaultBlobEntity(0 + VaultBlobEntity.SINGLE_ROW_ID, vault.blob, vault.itemCount, vault.revision, vault.updatedAt))
    }

    override fun clear() = dao.clear()
}
