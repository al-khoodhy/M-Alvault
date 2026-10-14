package com.alvault.app.core

import com.alvault.app.core.auth.AccountStore
import com.alvault.app.core.crypto.Argon2Hasher
import com.alvault.app.core.net.HttpExecutor
import com.alvault.app.core.net.HttpRequest
import com.alvault.app.core.net.HttpResponse
import com.alvault.app.core.net.NetworkException
import com.alvault.app.core.sync.SyncSettingsStore
import com.alvault.app.core.vault.StoredVault
import com.alvault.app.core.vault.VaultBlobStore
import java.net.URLDecoder
import java.security.MessageDigest

/** Argon2id palsu yang deterministik (SHA-512 -> 64 byte) agar logika di atasnya diuji tanpa pustaka native. */
class FakeArgon2 : Argon2Hasher {
    var calls = 0
    override fun hash(
        password: ByteArray, salt: ByteArray, iterations: Int,
        memoryKib: Int, parallelism: Int, outputLength: Int
    ): ByteArray {
        calls++
        val md = MessageDigest.getInstance("SHA-512")
        md.update(password); md.update(0); md.update(salt)
        md.update("$iterations/$memoryKib/$parallelism".toByteArray())
        return md.digest().copyOf(outputLength)
    }
}

class FakeVaultStore : VaultBlobStore {
    var stored: StoredVault? = null
    override fun load() = stored
    override fun save(vault: StoredVault) { stored = vault }
    override fun clear() { stored = null }
}

class FakeAccountStore : AccountStore {
    override var userEmail: String? = null
    override var localSalt: String? = null
    override var kdfIterations: Int = 3
    override var storedVerifier: String? = null
    override var failedAttempts: Int = 0
    override var lockoutUntil: Long = 0L
    var saveShouldFail = false

    override fun saveAccount(email: String, localSalt: String, kdfIterations: Int, verifier: String): Boolean {
        if (saveShouldFail) return false
        this.userEmail = email; this.localSalt = localSalt
        this.kdfIterations = kdfIterations; this.storedVerifier = verifier
        return true
    }

    override fun clearAll(): Boolean {
        userEmail = null; localSalt = null; storedVerifier = null; failedAttempts = 0; lockoutUntil = 0L
        return true
    }
}

class FakeSyncSettings(
    override var supabaseEnabled: Boolean = true,
    override var supabaseUrl: String = "https://abc.supabase.co",
    override var supabaseKey: String = "anon-key-123",
    override var lastSyncedRevision: Long = 0L
) : SyncSettingsStore

/**
 * Meniru perilaku PostgREST/Supabase untuk tabel `user_vaults`, termasuk jebakan nyata:
 * POST tanpa `on_conflict=user_email` pada baris yang sudah ada menghasilkan 409 (unique violation).
 */
class FakePostgrest(private val apiKey: String = "anon-key-123") : HttpExecutor {
    class Row(var blob: String, var revision: Long, var itemCount: Int)

    val rows = LinkedHashMap<String, Row>()
    val requests = mutableListOf<HttpRequest>()
    var tableExists = true
    var offline = false

    override fun execute(request: HttpRequest): HttpResponse {
        requests += request
        if (offline) throw NetworkException("offline")
        if (request.headers["apikey"] != apiKey || request.headers["Authorization"] != "Bearer $apiKey") {
            return HttpResponse(401, """{"message":"Invalid API key"}""")
        }
        if (!tableExists) {
            return HttpResponse(404, """{"code":"PGRST205","message":"Could not find the table 'public.user_vaults'"}""")
        }
        val query = request.url.substringAfter('?', "").split('&').filter { it.isNotEmpty() }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }

        return when (request.method) {
            "GET" -> {
                val filter = query["user_email"]
                val matches = rows.filter { filter == null || "eq.${it.key}" == filter }
                val json = matches.entries.joinToString(",", "[", "]") { (_, r) ->
                    """{"encrypted_data":"${r.blob}","revision":${r.revision}}"""
                }
                HttpResponse(200, if (query["select"] == "id") "[]" else json)
            }
            "POST" -> {
                val body = kotlinx.serialization.json.Json.parseToJsonElement(request.body!!)
                    as kotlinx.serialization.json.JsonObject
                fun str(k: String) = (body[k] as kotlinx.serialization.json.JsonPrimitive).content
                val email = str("user_email")
                val existing = rows[email]
                val merge = request.headers["Prefer"].orEmpty().contains("resolution=merge-duplicates") &&
                    query["on_conflict"] == "user_email"
                if (existing != null && !merge) {
                    return HttpResponse(409, """{"code":"23505","message":"duplicate key value violates unique constraint"}""")
                }
                rows[email] = Row(str("encrypted_data"), str("revision").toLong(), str("item_count").toInt())
                HttpResponse(201, "")
            }
            else -> HttpResponse(405, "")
        }
    }
}
