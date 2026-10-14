package com.alvault.app.core.net

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

data class HttpResponse(val code: Int, val body: String)

/** Kegagalan jaringan (tidak ada koneksi, timeout, TLS). Pesan aman ditampilkan ke pengguna. */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Abstraksi HTTP sinkron (blocking; panggil dari dispatcher IO). Implementasi produksi memakai OkHttp;
 * pengujian memakai fake sehingga logika Supabase/HIBP bisa diuji di JVM.
 */
interface HttpExecutor {
    /** @throws NetworkException bila permintaan tidak sampai ke server. */
    fun execute(request: HttpRequest): HttpResponse
}
