package com.alvault.app.core.sync

import com.alvault.app.core.crypto.Base64Codec
import com.alvault.app.core.net.HttpExecutor
import com.alvault.app.core.net.HttpRequest
import com.alvault.app.core.net.HttpResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder

/** Kesalahan dari/ke Supabase dengan pesan yang aman ditampilkan (tidak pernah memuat API key). */
class SupabaseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** DDL tabel sesuai dokumentasi; ditampilkan di UI agar pengguna bisa menyalinnya ke SQL Editor Supabase. */
object SupabaseSchema {
    const val TABLE = "user_vaults"

    val DDL = """
        CREATE TABLE IF NOT EXISTS user_vaults (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            user_email TEXT UNIQUE NOT NULL,
            encrypted_data TEXT NOT NULL,      -- Base64(IV[12B] + Ciphertext + GCM_Tag[16B])
            item_count INT DEFAULT 0,
            revision BIGINT DEFAULT 1,
            updated_at TIMESTAMPTZ DEFAULT NOW()
        );

        CREATE INDEX IF NOT EXISTS idx_user_vaults_email ON user_vaults(user_email);
    """.trimIndent()
}

/** Konfigurasi yang dimasukkan manual lewat UI. */
data class SupabaseConfig(val url: String, val key: String) {
    val baseUrl: String get() = url.trim().trimEnd('/')
    val apiKey: String get() = key.trim()

    /** @return pesan kesalahan untuk pengguna, atau null bila konfigurasi valid. */
    fun validate(): String? {
        if (baseUrl.isEmpty()) return "URL proyek Supabase belum diisi."
        if (!baseUrl.startsWith("https://", ignoreCase = true)) {
            return "URL harus diawali https:// (contoh: https://xyz.supabase.co)."
        }
        val host = baseUrl.substring("https://".length).substringBefore('/')
        if (host.isEmpty() || host.any { it.isWhitespace() }) return "URL proyek Supabase tidak valid."
        if (baseUrl.any { it.isWhitespace() }) return "URL proyek Supabase tidak boleh mengandung spasi."
        if (apiKey.isEmpty()) return "API key Supabase belum diisi."
        if (apiKey.any { it.isWhitespace() || it.code < 33 || it.code > 126 }) {
            return "API key mengandung karakter yang tidak valid."
        }
        return null
    }
}

data class RemoteVault(val encryptedData: String, val revision: Long)

sealed interface PingResult {
    /** Koneksi, key, dan tabel `user_vaults` semuanya OK. */
    object Ok : PingResult

    /** Koneksi & key OK, tetapi tabel belum dibuat (jalankan [SupabaseSchema.DDL]). */
    object TableMissing : PingResult
}

/**
 * Klien REST murni untuk tabel `user_vaults` (tanpa OAuth, hanya `apikey` + `Authorization: Bearer`).
 * Hanya ciphertext (`encrypted_data`) yang pernah dikirim.
 */
class SupabaseClient(private val http: HttpExecutor, config: SupabaseConfig) {

    private val base = config.baseUrl
    private val apiKey = config.apiKey

    private fun authHeaders(): Map<String, String> =
        mapOf("apikey" to apiKey, "Authorization" to "Bearer $apiKey", "Accept" to "application/json")

    /** Test Connection: GET ke REST API. Memvalidasi URL, key, sekaligus keberadaan tabel. */
    fun ping(): PingResult {
        val response = http.execute(
            HttpRequest("GET", "$base/rest/v1/${SupabaseSchema.TABLE}?select=id&limit=1", authHeaders())
        )
        if (response.code in 200..299) return PingResult.Ok
        if (response.code == 404 && errorCode(response.body) in TABLE_MISSING_CODES) return PingResult.TableMissing
        throw failure(response)
    }

    /** Pull: `GET /rest/v1/user_vaults?user_email=eq.{email}&select=encrypted_data,revision`. */
    fun fetch(email: String): RemoteVault? {
        val url = "$base/rest/v1/${SupabaseSchema.TABLE}" +
            "?user_email=eq.${encode(email)}&select=encrypted_data,revision"
        val response = http.execute(HttpRequest("GET", url, authHeaders()))
        if (response.code !in 200..299) throw failure(response)

        val rows = try {
            Json.parseToJsonElement(response.body) as? JsonArray
        } catch (e: Exception) {
            null
        } ?: throw SupabaseException("Respons Supabase tidak dikenali.")
        val row = rows.firstOrNull() as? JsonObject ?: return null

        val blob = (row["encrypted_data"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw SupabaseException("Data di cloud tidak memiliki kolom encrypted_data.")
        if (blob.isEmpty() || !Base64Codec.isValid(blob)) {
            throw SupabaseException("Data di cloud bukan vault blob yang valid.")
        }
        val revision = (row["revision"] as? JsonPrimitive)?.longOrNull ?: 1L
        return RemoteVault(blob, revision)
    }

    /**
     * Push: `POST /rest/v1/user_vaults?on_conflict=user_email` dengan `Prefer: resolution=merge-duplicates`.
     * `on_conflict` wajib karena kolom unik adalah `user_email`, bukan primary key `id`;
     * tanpa itu upsert kedua akan gagal 409 (duplikat).
     */
    fun upsert(email: String, blob: String, itemCount: Int, revision: Long, updatedAtIso: String) {
        val body = buildJsonObject {
            put("user_email", email)
            put("encrypted_data", blob)
            put("item_count", itemCount)
            put("revision", revision)
            put("updated_at", updatedAtIso)
        }.toString()
        val headers = authHeaders() + mapOf(
            "Content-Type" to "application/json",
            "Prefer" to "resolution=merge-duplicates,return=minimal"
        )
        val response = http.execute(
            HttpRequest("POST", "$base/rest/v1/${SupabaseSchema.TABLE}?on_conflict=user_email", headers, body)
        )
        if (response.code !in 200..299) throw failure(response)
    }

    private fun failure(response: HttpResponse): SupabaseException {
        val detail = errorMessage(response.body)?.let { " ($it)" }.orEmpty()
        val text = when (response.code) {
            401 -> "Ditolak (401): API key tidak valid.$detail"
            403 -> "Akses ditolak (403): periksa API key atau kebijakan RLS tabel user_vaults.$detail"
            404 -> "Tidak ditemukan (404): periksa URL proyek dan pastikan tabel user_vaults sudah dibuat.$detail"
            409 -> "Konflik data (409).$detail"
            in 500..599 -> "Server Supabase bermasalah (${response.code}). Coba lagi nanti.$detail"
            else -> "Supabase menolak permintaan (HTTP ${response.code}).$detail"
        }
        return SupabaseException(text)
    }

    private fun parseError(body: String): JsonObject? =
        try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        }

    private fun errorCode(body: String): String? = (parseError(body)?.get("code") as? JsonPrimitive)?.contentOrNull

    private fun errorMessage(body: String): String? =
        (parseError(body)?.get("message") as? JsonPrimitive)?.contentOrNull?.take(200)

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        /** PGRST205 = tabel tidak ada di schema cache (PostgREST baru); 42P01 = undefined_table. */
        val TABLE_MISSING_CODES = setOf("PGRST205", "42P01")
    }
}
