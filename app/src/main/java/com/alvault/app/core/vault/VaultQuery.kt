package com.alvault.app.core.vault

import com.alvault.app.core.model.VaultCategories
import com.alvault.app.core.model.VaultItem
import java.util.Locale

/** Operasi murni atas daftar entri di RAM: pencarian, filter kategori, tambah/ubah/hapus. */
object VaultQuery {

    /**
     * Mencari pada judul, username, URL, dan kategori (tanpa memedulikan huruf besar/kecil).
     * Password dan catatan sengaja tidak ikut dicari. Hasil diurutkan menurut judul.
     */
    fun filter(items: List<VaultItem>, query: String, category: String?): List<VaultItem> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return items
            .asSequence()
            .filter { category == null || it.category == category }
            .filter { item ->
                needle.isEmpty() || listOfNotNull(item.title, item.username, item.url, item.category)
                    .any { it.lowercase(Locale.ROOT).contains(needle) }
            }
            .sortedWith(compareBy<VaultItem> { it.title.lowercase(Locale.ROOT) }.thenBy { it.username.lowercase(Locale.ROOT) })
            .toList()
    }

    /** Kategori yang dipakai entri saat ini (untuk chip filter), terurut. */
    fun usedCategories(items: List<VaultItem>): List<String> =
        items.mapNotNull { it.category?.takeIf(String::isNotBlank) }
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }

    /** Pilihan kategori di formulir: bawaan + kategori kustom yang sudah ada. "Kustom..." ditambahkan UI. */
    fun categoryOptions(items: List<VaultItem>): List<String> =
        VaultCategories.DEFAULTS + usedCategories(items).filter { it !in VaultCategories.DEFAULTS }

    /** Mengganti entri ber-id sama, atau menambahkannya di akhir bila belum ada. */
    fun upsert(items: List<VaultItem>, item: VaultItem): List<VaultItem> =
        if (items.any { it.id == item.id }) items.map { if (it.id == item.id) item else it } else items + item

    fun delete(items: List<VaultItem>, id: String): List<VaultItem> = items.filterNot { it.id == id }

    fun find(items: List<VaultItem>, id: String): VaultItem? = items.firstOrNull { it.id == id }
}
