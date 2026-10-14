package com.alvault.app.platform

import android.os.Handler
import android.os.Looper
import com.alvault.app.core.session.SessionManager

/**
 * Memanggil [SessionManager.tick] tiap detik selama sesi terbuka (berhenti sendiri setelah terkunci).
 * Jika proses dibekukan sistem di background, pemeriksaan tetap terjadi saat aplikasi kembali terlihat
 * (lihat `SessionManager.onAppForegrounded`), jadi batas 60 detik tidak bisa dilewati lewat pembekuan.
 */
class AutoLockTicker(private val session: SessionManager) {
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (!session.isUnlocked) return
            session.tick()
            if (session.isUnlocked) handler.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
    }

    fun stop() {
        handler.removeCallbacks(tick)
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
