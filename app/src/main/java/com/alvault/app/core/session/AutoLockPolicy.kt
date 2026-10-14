package com.alvault.app.core.session

/** Konstanta hardening dari spesifikasi (bagian 6C). */
object Hardening {
    /** Auto-lock: EncryptionKey dihapus dari RAM bila aplikasi idle/background selama 60 detik. */
    const val AUTO_LOCK_MS = 60_000L

    /** Clipboard cleaner: clipboard dibersihkan 30 detik setelah password disalin. */
    const val CLIPBOARD_CLEAR_MS = 30_000L
}

/**
 * Logika waktu auto-lock yang murni (tanpa Android) agar bisa diuji di JVM.
 * Waktu dikirim sebagai parameter; di Android gunakan jam monoton `SystemClock.elapsedRealtime()`
 * (ikut berjalan saat perangkat tidur, tidak terpengaruh perubahan jam sistem).
 *
 * Dua kondisi yang mengunci sesi:
 * - **background**: aplikasi tidak terlihat selama [timeoutMs];
 * - **idle**: aplikasi terlihat tetapi tidak ada interaksi pengguna selama [timeoutMs].
 */
class AutoLockPolicy(val timeoutMs: Long = Hardening.AUTO_LOCK_MS) {
    private var lastActivityAt: Long = 0L
    private var backgroundedAt: Long? = null

    /** Dipanggil saat sesi dibuka atau pengguna berinteraksi. */
    @Synchronized
    fun markActive(now: Long) {
        lastActivityAt = now
    }

    @Synchronized
    fun onBackground(now: Long) {
        if (backgroundedAt == null) backgroundedAt = now
    }

    /**
     * Dipanggil saat aplikasi kembali terlihat.
     * @return true bila waktu di background sudah melewati batas dan sesi harus dikunci.
     */
    @Synchronized
    fun onForeground(now: Long): Boolean {
        val since = backgroundedAt ?: return false
        backgroundedAt = null
        val expired = now - since >= timeoutMs
        if (!expired) lastActivityAt = now
        return expired
    }

    /** true bila batas waktu terlampaui, baik karena background maupun idle di foreground. */
    @Synchronized
    fun isExpired(now: Long): Boolean {
        val since = backgroundedAt
        return if (since != null) now - since >= timeoutMs else now - lastActivityAt >= timeoutMs
    }

    /** Membersihkan status (setelah sesi terkunci). */
    @Synchronized
    fun reset() {
        lastActivityAt = 0L
        backgroundedAt = null
    }
}
