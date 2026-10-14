package com.alvault.app.core.sync

import com.alvault.app.core.FakeAccountStore
import com.alvault.app.core.FakePostgrest
import com.alvault.app.core.FakeSyncSettings
import com.alvault.app.core.FakeVaultStore
import com.alvault.app.core.crypto.AesGcm
import com.alvault.app.core.model.VaultItem
import com.alvault.app.core.net.NetworkException
import com.alvault.app.core.vault.VaultService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import org.junit.Test

class SyncServiceTest {
    private val server = FakePostgrest()
    private val settings = FakeSyncSettings()
    private val accounts = FakeAccountStore().also { it.userEmail = "ani+vault@example.com" }
    private val store = FakeVaultStore()
    private val vault = VaultService(store) { 1_700_000_000_000L }
    private val key = AesGcm.randomBytes(32)
    private val sync = SyncService(settings, vault, accounts, server, clock = { 1_700_000_000_000L })

    private fun item(id: String, title: String = "T-$id", modified: Long = 1) =
        VaultItem(id = id, title = title, username = "u", password = "RahasiaBanget!$id", lastModified = modified)

    private fun newDevice(): Triple<SyncService, VaultService, FakeSyncSettings> {
        val s = FakeSyncSettings()
        val v = VaultService(FakeVaultStore()) { 1L }
        v.initialize(key)
        return Triple(SyncService(s, v, accounts, server), v, s)
    }

    // ---------- Push ----------

    @Test
    fun firstPush_createsRowWithRevision1_andSendsOnlyCiphertext() {
        vault.save(key, listOf(item("a")))
        val result = sync.push() as PushResult.Success

        assertEquals(1L, result.revision)
        assertEquals(1L, settings.lastSyncedRevision)
        assertEquals(1L, vault.peek()!!.revision)

        val row = server.rows.getValue("ani+vault@example.com")
        assertEquals(vault.peek()!!.blob, row.blob)
        assertEquals(1, row.itemCount)

        val post = server.requests.last()
        assertEquals("POST", post.method)
        assertEquals("https://abc.supabase.co/rest/v1/user_vaults?on_conflict=user_email", post.url)
        assertEquals("anon-key-123", post.headers["apikey"])
        assertEquals("Bearer anon-key-123", post.headers["Authorization"])
        assertEquals("application/json", post.headers["Content-Type"])
        assertTrue(post.headers["Prefer"]!!.contains("resolution=merge-duplicates"))

        val body = Json.parseToJsonElement(post.body!!) as JsonObject
        assertEquals(setOf("user_email", "encrypted_data", "item_count", "revision", "updated_at"), body.keys)
        assertEquals("ani+vault@example.com", (body["user_email"] as JsonPrimitive).content)
        assertEquals(1, (body["item_count"] as JsonPrimitive).content.toInt())
        assertEquals(1L, (body["revision"] as JsonPrimitive).long)
        assertEquals("2023-11-14T22:13:20Z", (body["updated_at"] as JsonPrimitive).content)
        assertFalse(post.body!!.contains("RahasiaBanget"))
        assertFalse(post.body!!.contains("T-a"))
    }

    @Test
    fun secondPush_upsertsInsteadOfFailingWith409() {
        vault.save(key, listOf(item("a")))
        sync.push()
        vault.save(key, listOf(item("a"), item("b")))
        val second = sync.push() as PushResult.Success

        assertEquals(2L, second.revision)
        assertEquals(1, server.rows.size)
        assertEquals(2, server.rows.getValue("ani+vault@example.com").itemCount)
    }

    @Test
    fun emailInQuery_isUrlEncoded() {
        vault.save(key, listOf(item("a")))
        sync.push()
        val get = server.requests.first { it.method == "GET" }
        assertTrue(get.url, get.url.contains("user_email=eq.ani%2Bvault%40example.com"))
        assertTrue(get.url.endsWith("&select=encrypted_data,revision"))
    }

    @Test
    fun push_stopsOnConflict_whenCloudHasUnseenRevision_thenForceOverrides() {
        val (otherSync, otherVault, _) = newDevice()
        otherVault.save(key, listOf(item("x")))
        assertTrue(otherSync.push() is PushResult.Success) // cloud revision 1 dari perangkat lain

        vault.save(key, listOf(item("a")))
        val conflict = sync.push() as PushResult.Conflict
        assertEquals(1L, conflict.remoteRevision)
        assertEquals(1, server.rows.getValue("ani+vault@example.com").itemCount) // tidak ditimpa
        assertEquals(0L, settings.lastSyncedRevision)

        val forced = sync.push(force = true) as PushResult.Success
        assertEquals(2L, forced.revision)
        assertEquals(1, server.rows.getValue("ani+vault@example.com").itemCount)
        assertEquals(vault.peek()!!.blob, server.rows.getValue("ani+vault@example.com").blob)
    }

    // ---------- Pull ----------

    @Test
    fun pull_withoutRemote_reportsNoRemote() {
        vault.initialize(key)
        assertSame(PullResult.NoRemote, sync.pull(key))
    }

    @Test
    fun pull_onFreshDevice_replacesEmptyLocalVault() {
        val (otherSync, otherVault, _) = newDevice()
        otherVault.save(key, listOf(item("a"), item("b")))
        otherSync.push()

        vault.initialize(key) // perangkat baru: brankas kosong, belum pernah sync
        val result = sync.pull(key) as PullResult.Applied
        assertEquals(PullStrategy.REPLACE, result.strategy)
        assertEquals(2, result.itemCount)
        assertEquals(listOf("a", "b"), vault.load(key).items.map { it.id })
        assertEquals(1L, settings.lastSyncedRevision)
        assertEquals(1L, vault.peek()!!.revision)
    }

    @Test
    fun pull_whenNothingChanged_isUpToDate_andLocalAheadIsDetected() {
        vault.save(key, listOf(item("a")))
        sync.push()
        assertSame(PullResult.UpToDate, sync.pull(key))

        vault.save(key, listOf(item("a"), item("b"))) // perubahan lokal belum di-push
        assertSame(PullResult.LocalAhead, sync.pull(key))
        assertEquals(2, vault.load(key).items.size) // tidak tertimpa
    }

    @Test
    fun pull_conflict_requiresChoice_mergeKeepsBothSides_thenPushSucceeds() {
        // Perangkat lain mendorong revisi baru
        val (otherSync, otherVault, _) = newDevice()
        otherVault.save(key, listOf(item("shared", "dari-cloud", modified = 200), item("cloud-only")))
        otherSync.push()

        // Perangkat ini punya perubahan lokal sendiri
        vault.save(key, listOf(item("shared", "dari-lokal", modified = 100), item("local-only")))

        val conflict = sync.pull(key) as PullResult.Conflict
        assertEquals(1L, conflict.remoteRevision)
        assertEquals(2, conflict.remoteItems)
        assertEquals(2, conflict.localItems)
        // Belum ada yang berubah
        assertEquals(setOf("shared", "local-only"), vault.load(key).items.map { it.id }.toSet())

        val applied = sync.pull(key, PullStrategy.MERGE) as PullResult.Applied
        assertEquals(3, applied.itemCount)
        val merged = vault.load(key).items.associateBy { it.id }
        assertEquals(setOf("shared", "cloud-only", "local-only"), merged.keys)
        assertEquals("dari-cloud", merged.getValue("shared").title) // lastModified lebih baru menang

        // Hasil gabungan tercatat sebagai perubahan lokal -> push berikutnya diizinkan (bukan konflik)
        assertEquals(2L, vault.peek()!!.revision)
        assertEquals(1L, settings.lastSyncedRevision)
        val pushed = sync.push() as PushResult.Success
        assertEquals(2L, pushed.revision)
        assertEquals(3, server.rows.getValue("ani+vault@example.com").itemCount)
    }

    @Test
    fun pull_conflict_replaceDiscardsLocalChanges() {
        val (otherSync, otherVault, _) = newDevice()
        otherVault.save(key, listOf(item("cloud-only")))
        otherSync.push()
        vault.save(key, listOf(item("local-only")))

        val applied = sync.pull(key, PullStrategy.REPLACE) as PullResult.Applied
        assertEquals(PullStrategy.REPLACE, applied.strategy)
        assertEquals(listOf("cloud-only"), vault.load(key).items.map { it.id })
        assertEquals(1L, settings.lastSyncedRevision)
        assertEquals(1L, vault.peek()!!.revision)
    }

    @Test
    fun pull_withWrongKey_failsClearly_andLeavesLocalVaultUntouched() {
        val otherKey = AesGcm.randomBytes(32)
        val foreign = VaultService(FakeVaultStore()) { 1L }
        foreign.save(otherKey, listOf(item("x")))
        server.rows["ani+vault@example.com"] = FakePostgrest.Row(foreign.peek()!!.blob, 5, 1)

        vault.initialize(key)
        val before = vault.peek()
        val ex = assertThrows(RemoteDecryptException::class.java) { sync.pull(key) }
        assertTrue(ex.message!!.contains("Impor .aegis"))
        assertEquals(before, vault.peek())
        assertEquals(0L, settings.lastSyncedRevision)
    }

    // ---------- Koneksi & kesalahan ----------

    @Test
    fun testConnection_ok_tableMissing_andBadKey() {
        assertSame(PingResult.Ok, sync.testConnection("https://abc.supabase.co/", " anon-key-123 \n"))

        server.tableExists = false
        assertSame(PingResult.TableMissing, sync.testConnection("https://abc.supabase.co", "anon-key-123"))
        server.tableExists = true

        val ex = assertThrows(SupabaseException::class.java) { sync.testConnection("https://abc.supabase.co", "salah") }
        assertTrue(ex.message!!.contains("401"))
        assertFalse(ex.message!!.contains("salah")) // pesan tidak membocorkan key
    }

    @Test
    fun configValidation_rejectsInsecureOrMalformedInput() {
        assertTrue(SupabaseConfig("", "k").validate() != null)
        assertTrue(SupabaseConfig("http://abc.supabase.co", "k").validate()!!.contains("https://"))
        assertTrue(SupabaseConfig("https://", "k").validate() != null)
        assertTrue(SupabaseConfig("https://a b.co", "k").validate() != null)
        assertTrue(SupabaseConfig("https://abc.supabase.co", "").validate() != null)
        assertTrue(SupabaseConfig("https://abc.supabase.co", "ke y").validate() != null)
        assertEquals(null, SupabaseConfig(" https://abc.supabase.co/ ", " k123 ").validate())
        assertEquals("https://abc.supabase.co", SupabaseConfig(" https://abc.supabase.co/ ", "k").baseUrl)
    }

    @Test
    fun push_withoutNetwork_surfacesNetworkException_andChangesNothing() {
        vault.save(key, listOf(item("a")))
        server.offline = true
        assertThrows(NetworkException::class.java) { sync.push() }
        assertEquals(0L, settings.lastSyncedRevision)
        assertEquals(1L, vault.peek()!!.revision)
    }

    @Test
    fun serverErrors_areMappedToReadableMessages() {
        settings.supabaseKey = "kunci-salah"
        vault.save(key, listOf(item("a")))
        val ex = assertThrows(SupabaseException::class.java) { sync.push() }
        assertTrue(ex.message!!.contains("401"))
        assertFalse(ex.message!!.contains("kunci-salah"))
    }

    @Test
    fun unconfiguredSync_failsBeforeAnyRequest() {
        settings.supabaseUrl = ""
        assertThrows(SupabaseException::class.java) { sync.push() }
        assertThrows(SupabaseException::class.java) { sync.pull(key) }
        assertTrue(server.requests.isEmpty())
    }
}
