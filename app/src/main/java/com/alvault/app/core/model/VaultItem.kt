package com.alvault.app.core.model

import java.util.UUID

/** Entri brankas (struktur sama dengan aplikasi web). Hanya ada di RAM saat terdekripsi. */
data class VaultItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val username: String,
    val password: String,
    val category: String? = null,
    val url: String? = null,
    val notes: String? = null,
    /** Epoch millis. */
    val lastModified: Long = System.currentTimeMillis()
)

/** Kategori bawaan; "Kustom..." membuka input teks bebas di UI. */
object VaultCategories {
    const val CUSTOM_LABEL = "Kustom..."

    val DEFAULTS = listOf(
        "Instagram",
        "Facebook",
        "X / Twitter",
        "TikTok",
        "Google",
        "Email",
        "Bank / Finansial",
        "E-Commerce",
        "Game",
        "Pekerjaan",
        "Lainnya"
    )
}
