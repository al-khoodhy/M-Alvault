package com.alvault.app.core.session

import com.alvault.app.core.crypto.Bytes
import java.util.concurrent.CopyOnWriteArrayList

/** Dilempar bila operasi butuh EncryptionKey tetapi sesi sedang terkunci. */
class SessionLockedException : Exception("Sesi terkunci. Buka kunci brankas terlebih dahulu.")

/**
 * Satu-satunya pemegang `EncryptionKey` di RAM (ZKA: kunci tidak pernah ke disk/cloud).
 *
 * - [unlock] menyalin kunci; pemanggil tetap wajib meng-wipe salinannya sendiri.
 * - [withKey] memberi blok sebuah SALINAN kunci yang otomatis di-wipe setelah blok selesai,
 *   sehingga [lock] di tengah operasi tidak meninggalkan kunci hidup di tempat lain.
 * - Auto-lock memakai [AutoLockPolicy]; [tick] dipanggil berkala oleh lapisan Android.
 */
class SessionManager(
    private val clock: () -> Long = System::currentTimeMillis,
    val policy: AutoLockPolicy = AutoLockPolicy()
) {
    private val monitor = Any()
    private var key: ByteArray? = null
    private val listeners = CopyOnWriteArrayList<(unlocked: Boolean) -> Unit>()

    val isUnlocked: Boolean get() = synchronized(monitor) { key != null }

    fun addListener(listener: (unlocked: Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (unlocked: Boolean) -> Unit) {
        listeners.remove(listener)
    }

    fun unlock(encryptionKey: ByteArray) {
        synchronized(monitor) {
            Bytes.wipe(key)
            key = encryptionKey.copyOf()
        }
        policy.reset()
        policy.markActive(clock())
        listeners.forEach { it(true) }
    }

    /** Menghapus kunci dari RAM. Aman dipanggil berulang kali. */
    fun lock() {
        val wasUnlocked = synchronized(monitor) {
            val had = key != null
            Bytes.wipe(key)
            key = null
            had
        }
        policy.reset()
        if (wasUnlocked) listeners.forEach { it(false) }
    }

    /** @throws SessionLockedException bila sesi terkunci. */
    fun <T> withKey(block: (key: ByteArray) -> T): T {
        val copy = synchronized(monitor) { key?.copyOf() } ?: throw SessionLockedException()
        try {
            return block(copy)
        } finally {
            Bytes.wipe(copy)
        }
    }

    // ---- Auto-lock ----

    fun onUserInteraction() {
        if (isUnlocked) policy.markActive(clock())
    }

    fun onAppBackgrounded() {
        if (isUnlocked) policy.onBackground(clock())
    }

    fun onAppForegrounded() {
        if (!isUnlocked) return
        if (policy.onForeground(clock())) lock()
    }

    /** Dipanggil berkala (mis. tiap detik) selama sesi terbuka. @return true bila sesi baru saja dikunci. */
    fun tick(): Boolean {
        if (!isUnlocked) return false
        if (policy.isExpired(clock())) {
            lock()
            return true
        }
        return false
    }
}
