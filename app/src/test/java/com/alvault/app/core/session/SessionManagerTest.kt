package com.alvault.app.core.session

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    private var now = 1_000L
    private fun manager(timeout: Long = 60_000L) =
        SessionManager(clock = { now }, policy = AutoLockPolicy(timeout))

    private fun key(fill: Int = 7) = ByteArray(32) { fill.toByte() }

    @Test
    fun `constants match spec`() {
        assertEquals(60_000L, Hardening.AUTO_LOCK_MS)
        assertEquals(30_000L, Hardening.CLIPBOARD_CLEAR_MS)
    }

    @Test
    fun `unlock stores a copy and withKey hands out wiped copies`() {
        val s = manager()
        val original = key()
        s.unlock(original)
        original.fill(0) // pemanggil me-wipe salinannya; sesi tetap memegang kunci
        assertTrue(s.isUnlocked)

        var leaked: ByteArray? = null
        s.withKey { k ->
            leaked = k
            assertArrayEquals(key(), k)
        }
        assertTrue("salinan kunci harus di-wipe setelah blok", leaked!!.all { it == 0.toByte() })
        s.withKey { k -> assertArrayEquals(key(), k) } // kunci asli di sesi tidak ikut terhapus
    }

    @Test
    fun `withKey passes distinct copies each call`() {
        val s = manager()
        s.unlock(key())
        var first: ByteArray? = null
        var second: ByteArray? = null
        s.withKey { first = it }
        s.withKey { second = it }
        assertNotSame(first, second)
    }

    @Test
    fun `lock wipes the key and withKey throws`() {
        val s = manager()
        s.unlock(key())
        s.lock()
        assertFalse(s.isUnlocked)
        assertThrows(SessionLockedException::class.java) { s.withKey { } }
    }

    @Test
    fun `lock is idempotent and notifies listeners only on change`() {
        val s = manager()
        val events = mutableListOf<Boolean>()
        s.addListener { events += it }
        s.lock() // belum terbuka: tidak ada event
        s.unlock(key())
        s.lock()
        s.lock()
        assertEquals(listOf(true, false), events)
    }

    @Test
    fun `lock during withKey does not corrupt the running block's key`() {
        val s = manager()
        s.unlock(key())
        s.withKey { k ->
            s.lock() // sesi dikunci di tengah operasi
            assertArrayEquals("blok berjalan tetap memegang salinannya", key(), k)
        }
        assertFalse(s.isUnlocked)
    }

    @Test
    fun `idle in foreground locks after timeout`() {
        val s = manager()
        s.unlock(key())
        now += 59_999
        assertFalse(s.tick())
        assertTrue(s.isUnlocked)
        now += 1
        assertTrue(s.tick())
        assertFalse(s.isUnlocked)
    }

    @Test
    fun `user interaction postpones idle lock`() {
        val s = manager()
        s.unlock(key())
        now += 50_000
        s.onUserInteraction()
        now += 50_000
        assertFalse(s.tick())
        now += 10_000
        assertTrue(s.tick())
    }

    @Test
    fun `background shorter than timeout keeps session and resets idle clock`() {
        val s = manager()
        s.unlock(key())
        now += 10_000
        s.onAppBackgrounded()
        now += 30_000
        s.onAppForegrounded()
        assertTrue(s.isUnlocked)
        now += 59_000 // dihitung dari kembali ke foreground, bukan dari unlock
        assertFalse(s.tick())
    }

    @Test
    fun `background for 60 seconds locks on return even if no tick ran`() {
        val s = manager()
        s.unlock(key())
        s.onAppBackgrounded()
        now += 60_000
        s.onAppForegrounded()
        assertFalse(s.isUnlocked)
    }

    @Test
    fun `background expiry is detected by tick while process is alive`() {
        val s = manager()
        s.unlock(key())
        s.onAppBackgrounded()
        now += 59_999
        assertFalse(s.tick())
        now += 1
        assertTrue(s.tick())
        assertFalse(s.isUnlocked)
    }

    @Test
    fun `repeated background calls keep the first timestamp`() {
        val p = AutoLockPolicy(60_000)
        p.markActive(0)
        p.onBackground(1_000)
        p.onBackground(50_000)
        assertTrue(p.onForeground(61_000))
    }

    @Test
    fun `unlock resets policy so stale background state cannot lock a fresh session`() {
        val s = manager()
        s.unlock(key())
        s.onAppBackgrounded()
        now += 100_000
        s.lock()
        s.unlock(key(9))
        assertFalse(s.tick())
        assertTrue(s.isUnlocked)
    }

    @Test
    fun `no lock events while locked`() {
        val s = manager()
        s.onAppBackgrounded()
        now += 1_000_000
        s.onAppForegrounded()
        assertFalse(s.tick())
    }
}
