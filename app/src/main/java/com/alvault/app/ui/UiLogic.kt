package com.alvault.app.ui

import com.alvault.app.core.auth.AccountService
import com.alvault.app.core.model.MergeMode
import com.alvault.app.core.model.MergeResult
import com.alvault.app.core.model.VaultCategories
import com.alvault.app.core.model.VaultItem
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Logika tampilan yang murni (tanpa Android/Compose) supaya bisa diuji di JVM:
 * validasi formulir, format teks, dan pembacaan berkas dengan batas ukuran.
 */
object UiLogic {

    const val MIN_BACKUP_PASSWORD_LENGTH = 8

    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** `alice@example.com` -> `a•••e@example.com`. Email tanpa `@` dikembalikan apa adanya. */
    fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 0) return email
        val local = email.substring(0, at)
        val domain = email.substring(at)
        val masked = when {
            local.length <= 2 -> local.take(1) + "•••"
            else -> local.first() + "•••" + local.last()
        }
        return masked + domain
    }

    /** 45_000 -> "45 dtk"; 61_000 -> "1 mnt 1 dtk"; 120_000 -> "2 mnt". Dibulatkan ke atas ke detik. */
    fun formatRemaining(millis: Long): String {
        val totalSeconds = ((millis.coerceAtLeast(0L) + 999L) / 1000L).toInt()
        if (totalSeconds < 60) return "$totalSeconds dtk"
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (seconds == 0) "$minutes mnt" else "$minutes mnt $seconds dtk"
    }

    /** @return pesan kesalahan pertama, atau null bila isian registrasi valid. */
    fun registrationError(email: String, password: String, confirm: String): String? {
        if (!EMAIL_REGEX.matches(email.trim())) return "Format email tidak valid."
        if (password.length < AccountService.MIN_MASTER_PASSWORD_LENGTH) {
            return "Master password minimal ${AccountService.MIN_MASTER_PASSWORD_LENGTH} karakter."
        }
        if (password != confirm) return "Konfirmasi password tidak sama."
        return null
    }

    /** Password backup .aegis. [confirm] null = mode impor (tanpa konfirmasi & tanpa syarat panjang). */
    fun backupPasswordError(password: String, confirm: String?): String? {
        if (password.isEmpty()) return "Password backup tidak boleh kosong."
        if (confirm == null) return null
        if (password.length < MIN_BACKUP_PASSWORD_LENGTH) {
            return "Password backup minimal $MIN_BACKUP_PASSWORD_LENGTH karakter."
        }
        if (password != confirm) return "Konfirmasi password tidak sama."
        return null
    }

    fun itemError(title: String): String? = if (title.isBlank()) "Judul wajib diisi." else null

    /** Pilihan kategori di formulir -> nilai yang disimpan. "Kustom..." memakai teks bebas. */
    fun resolveCategory(selected: String?, custom: String): String? = when {
        selected == null || selected.isBlank() -> null
        selected == VaultCategories.CUSTOM_LABEL -> custom.trim().ifEmpty { null }
        else -> selected
    }

    /**
     * Membuat entri dari isian formulir. Password TIDAK di-trim (spasi bisa jadi bagian password);
     * kolom opsional yang kosong disimpan sebagai null. Id dipertahankan saat mengedit.
     */
    fun buildItem(
        existing: VaultItem?,
        title: String,
        username: String,
        password: String,
        category: String?,
        url: String,
        notes: String,
        now: Long
    ): VaultItem = VaultItem(
        id = existing?.id ?: UUID.randomUUID().toString(),
        title = title.trim(),
        username = username.trim(),
        password = password,
        category = category,
        url = url.trim().ifEmpty { null },
        notes = notes.trim().ifEmpty { null },
        lastModified = now
    )

    /** Huruf/angka pertama judul (kapital) untuk avatar; "•" bila tidak ada. */
    fun initialOf(title: String): String =
        title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"

    /** URL yang aman dibuka di browser (hanya http/https; tanpa skema ditambah https://), atau null. */
    fun browsableUrl(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val withScheme = if (text.contains("://")) text else "https://$text"
        val lower = withScheme.lowercase()
        return if (lower.startsWith("https://") || lower.startsWith("http://")) withScheme else null
    }

    /** Ringkasan hasil impor untuk pengguna. */
    fun describeImport(mode: MergeMode, result: MergeResult): String = when (mode) {
        MergeMode.MERGE ->
            "Impor selesai: ${result.added} ditambahkan, ${result.updated} diperbarui, ${result.skipped} dilewati."
        MergeMode.REPLACE ->
            "Brankas diganti dengan ${result.added} entri dari backup."
    }

    /**
     * Membaca seluruh [input] dengan batas [maxBytes] (tanpa `readNBytes`, yang baru ada di Android 13+).
     * @return null bila ukuran melebihi batas.
     */
    fun readLimited(input: InputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
