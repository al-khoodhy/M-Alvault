package com.alvault.app.core.security

import com.alvault.app.core.model.VaultItem
import com.alvault.app.core.net.HttpExecutor
import com.alvault.app.core.net.HttpRequest
import com.alvault.app.core.net.NetworkException

/** Sumber data rentang HIBP: mengembalikan body `{SUFFIX}:{COUNT}` per baris untuk sebuah prefix 5 karakter. */
interface HibpApi {
    /** @throws NetworkException bila gagal terhubung / server menolak. */
    fun range(prefix: String): String
}

/** `GET https://api.pwnedpasswords.com/range/{Prefix}` — hanya 5 karakter pertama SHA-1 yang dikirim. */
class HibpClient(private val http: HttpExecutor) : HibpApi {
    override fun range(prefix: String): String {
        require(PREFIX_REGEX.matches(prefix)) { "Prefix HIBP harus 5 karakter heksadesimal huruf besar." }
        val response = http.execute(
            HttpRequest(
                method = "GET",
                url = "https://api.pwnedpasswords.com/range/$prefix",
                // Add-Padding: respons diberi baris palsu agar panjangnya tidak membocorkan prefix.
                headers = mapOf("Add-Padding" to "true", "User-Agent" to "AlVault-Android")
            )
        )
        if (response.code != 200) throw NetworkException("Layanan HIBP menolak permintaan (HTTP ${response.code}).")
        return response.body
    }

    private companion object {
        val PREFIX_REGEX = Regex("^[0-9A-F]{5}$")
    }
}

enum class AuditStatus { COMPROMISED, WEAK, UNCHECKED, SAFE }

/** Hasil audit satu entri. Sengaja TIDAK menyimpan password, hanya penanda untuk ditampilkan. */
data class AuditEntry(
    val itemId: String,
    val title: String,
    val username: String,
    val isWeak: Boolean,
    /** Jumlah kemunculan di kebocoran data (0 = tidak ditemukan atau tidak sempat diperiksa). */
    val breachCount: Int,
    val breachCheckFailed: Boolean
) {
    val isCompromised: Boolean get() = breachCount > 0

    val status: AuditStatus
        get() = when {
            isCompromised -> AuditStatus.COMPROMISED
            isWeak -> AuditStatus.WEAK
            breachCheckFailed -> AuditStatus.UNCHECKED
            else -> AuditStatus.SAFE
        }
}

data class AuditReport(val entries: List<AuditEntry>, val itemsWithoutPassword: Int) {
    val compromised: Int get() = entries.count { it.isCompromised }
    val weak: Int get() = entries.count { it.isWeak }
    val unchecked: Int get() = entries.count { it.breachCheckFailed && !it.isCompromised }
    val safe: Int get() = entries.count { it.status == AuditStatus.SAFE }
}

/**
 * Audit keamanan: (1) cek lemah lokal (< 8 karakter / pola umum), (2) HIBP k-anonymity.
 * Entri yang berbagi prefix SHA-1 yang sama dilayani dengan satu permintaan.
 */
class SecurityAuditor(private val hibp: HibpApi) {

    fun run(
        items: List<VaultItem>,
        shouldContinue: () -> Boolean = { true },
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): AuditReport {
        val withPassword = items.filter { it.password.isNotEmpty() }
        val hashes = withPassword.associate { it.id to Hibp.sha1Hex(it.password) }

        val prefixes = hashes.values.map { Hibp.prefix(it) }.distinct()
        val bodies = HashMap<String, String?>() // null = gagal diambil
        var done = 0
        onProgress(0, prefixes.size)
        for (prefix in prefixes) {
            if (!shouldContinue()) break
            bodies[prefix] = try {
                hibp.range(prefix)
            } catch (e: NetworkException) {
                null
            }
            onProgress(++done, prefixes.size)
        }

        val entries = withPassword.map { item ->
            val hash = hashes.getValue(item.id)
            val prefix = Hibp.prefix(hash)
            val checked = bodies.containsKey(prefix)
            val body = bodies[prefix]
            AuditEntry(
                itemId = item.id,
                title = item.title,
                username = item.username,
                isWeak = PasswordStrength.isWeakLocal(item.password),
                breachCount = if (body != null) Hibp.breachCount(body, Hibp.suffix(hash)) else 0,
                breachCheckFailed = !checked || body == null
            )
        }.sortedWith(compareBy<AuditEntry> { it.status.ordinal }.thenBy { it.title.lowercase() })

        return AuditReport(entries, items.size - withPassword.size)
    }
}
