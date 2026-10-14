package com.alvault.app.platform

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import com.alvault.app.core.session.Hardening

/**
 * Menyalin teks sensitif dan membersihkannya otomatis setelah [Hardening.CLIPBOARD_CLEAR_MS] (30 detik).
 *
 * - Android 13+: klip ditandai `EXTRA_IS_SENSITIVE` agar tidak tampil di pratinjau clipboard sistem.
 * - Pembersihan hanya dilakukan bila klip aktif masih milik AlVault (label cocok) sehingga isi salinan
 *   dari aplikasi lain tidak ikut terhapus. Jika label tidak bisa dibaca (null), klip dibersihkan.
 */
class ClipboardCleaner(context: Context) {
    private val clipboard = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /** Salin teks biasa (mis. username/URL) tanpa penghapusan otomatis. */
    fun copyPlain(label: String, text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** Salin password lalu jadwalkan pembersihan. */
    fun copySensitive(text: String, clearAfterMs: Long = Hardening.CLIPBOARD_CLEAR_MS) {
        val clip = ClipData.newPlainText(CLIP_LABEL, text)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)

        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable { clearIfOurs() }
        pending = task
        handler.postDelayed(task, clearAfterMs)
    }

    private fun clearIfOurs() {
        pending = null
        try {
            val label = clipboard.primaryClipDescription?.label
            if (label == null || label == CLIP_LABEL) clipboard.clearPrimaryClip()
        } catch (_: Exception) {
            // Beberapa ROM membatasi akses clipboard dari background; tidak ada yang bisa dilakukan.
        }
    }

    private companion object {
        const val CLIP_LABEL = "AlVault"
    }
}
