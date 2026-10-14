package com.alvault.app.data.net

import com.alvault.app.core.net.HttpExecutor
import com.alvault.app.core.net.HttpRequest
import com.alvault.app.core.net.HttpResponse
import com.alvault.app.core.net.NetworkException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Implementasi [HttpExecutor] dengan OkHttp. Hanya HTTPS (cleartext dimatikan di manifest),
 * tanpa logging interceptor agar API key/respons tidak pernah masuk logcat.
 */
class OkHttpExecutor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false) // jangan ikuti redirect https -> http
        .build()
) : HttpExecutor {

    override fun execute(request: HttpRequest): HttpResponse {
        val body = request.body?.toRequestBody(request.headers["Content-Type"]?.toMediaTypeOrNull())
        val okRequest = try {
            Request.Builder()
                .url(request.url)
                .apply { request.headers.forEach { (name, value) -> header(name, value) } }
                .method(request.method, body)
                .build()
        } catch (e: IllegalArgumentException) {
            throw NetworkException("URL atau header permintaan tidak valid.", e)
        }

        try {
            client.newCall(okRequest).execute().use { response ->
                return HttpResponse(response.code, response.body?.string().orEmpty())
            }
        } catch (e: UnknownHostException) {
            throw NetworkException("Tidak ada koneksi internet atau server tidak ditemukan.", e)
        } catch (e: SocketTimeoutException) {
            throw NetworkException("Koneksi ke server timeout. Coba lagi.", e)
        } catch (e: SSLException) {
            throw NetworkException("Gagal membuat koneksi aman (TLS) ke server.", e)
        } catch (e: IOException) {
            throw NetworkException("Gagal terhubung ke server. Periksa koneksi internet.", e)
        }
    }
}
