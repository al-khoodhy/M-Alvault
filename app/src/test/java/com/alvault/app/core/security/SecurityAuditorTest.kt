package com.alvault.app.core.security

import com.alvault.app.core.net.HttpExecutor
import com.alvault.app.core.net.HttpRequest
import com.alvault.app.core.net.HttpResponse
import com.alvault.app.core.net.NetworkException
import com.alvault.app.core.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAuditorTest {

    /** HIBP palsu: hanya mengenal "password" (5BAA6 / 1E4C...) sebagai bocor 9.545.824 kali. */
    private class FakeHibp(val failFor: Set<String> = emptySet()) : HibpApi {
        val requestedPrefixes = mutableListOf<String>()
        override fun range(prefix: String): String {
            requestedPrefixes += prefix
            if (prefix in failFor) throw NetworkException("offline")
            return if (prefix == "5BAA6") {
                "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" +
                    "1E4C9B93F3F0682250B6CF8331B7EE68FD8:9545824\r\n" +
                    "2DC183F740EE76F27B78EB39C8AD972A757:52579\r\n"
            } else {
                "0000000000000000000000000000000000A:0\r\n"
            }
        }
    }

    private fun item(id: String, title: String, password: String) =
        VaultItem(id = id, title = title, username = "user-$id", password = password)

    private val strong = "Xk9#mP2\$vL7@qR4!"

    @Test
    fun sendsOnlyFiveCharPrefix_andNeverPasswordOrSuffix() {
        val hibp = FakeHibp()
        SecurityAuditor(hibp).run(listOf(item("1", "A", "password"), item("2", "B", strong)))

        assertEquals(2, hibp.requestedPrefixes.size)
        assertTrue(hibp.requestedPrefixes.all { it.length == 5 && it.all { c -> c in "0123456789ABCDEF" } })
        assertTrue("5BAA6" in hibp.requestedPrefixes)
        assertFalse(hibp.requestedPrefixes.any { it.contains("password", ignoreCase = true) })
    }

    @Test
    fun classifiesCompromisedWeakAndSafe_sortedBySeverity() {
        val report = SecurityAuditor(FakeHibp()).run(
            listOf(
                item("safe", "Aman", strong),
                item("weak", "Lemah", "abc"),
                item("pwn", "Bocor", "password")
            )
        )
        assertEquals(listOf("pwn", "weak", "safe"), report.entries.map { it.itemId })

        val pwn = report.entries[0]
        assertEquals(AuditStatus.COMPROMISED, pwn.status)
        assertEquals(9_545_824, pwn.breachCount)
        assertTrue(pwn.isWeak) // "password" juga lemah secara lokal
        assertEquals(AuditStatus.WEAK, report.entries[1].status)
        assertEquals(AuditStatus.SAFE, report.entries[2].status)

        assertEquals(1, report.compromised)
        assertEquals(2, report.weak)
        assertEquals(1, report.safe)
        assertEquals(0, report.unchecked)
    }

    @Test
    fun identicalPasswords_shareOneRequest() {
        val hibp = FakeHibp()
        val report = SecurityAuditor(hibp).run(
            listOf(item("1", "A", "password"), item("2", "B", "password"), item("3", "C", "password"))
        )
        assertEquals(1, hibp.requestedPrefixes.size)
        assertEquals(3, report.compromised)
    }

    @Test
    fun networkFailure_marksOnlyAffectedEntriesAsUnchecked_andKeepsLocalWeakResult() {
        val hibp = FakeHibp(failFor = setOf("5BAA6"))
        val report = SecurityAuditor(hibp).run(
            listOf(item("1", "Lemah-bocor", "password"), item("2", "Kuat", strong))
        )
        val byId = report.entries.associateBy { it.itemId }
        assertEquals(AuditStatus.WEAK, byId.getValue("1").status) // lemah tetap terdeteksi lokal
        assertTrue(byId.getValue("1").breachCheckFailed)
        assertEquals(AuditStatus.SAFE, byId.getValue("2").status)
        // Status "1" tetap WEAK (prioritas lebih tinggi), tetapi ia memang belum diperiksa ke HIBP,
        // jadi ringkasan harus jujur: 1 entri tidak sempat dicek.
        assertEquals(1, report.unchecked)
    }

    @Test
    fun strongButUncheckable_isReportedAsUnchecked_notSafe() {
        val report = SecurityAuditor(FakeHibp(failFor = setOf(Hibp.prefix(Hibp.sha1Hex(strong)))))
            .run(listOf(item("1", "Kuat", strong)))
        assertEquals(AuditStatus.UNCHECKED, report.entries.single().status)
        assertEquals(1, report.unchecked)
        assertEquals(0, report.safe)
    }

    @Test
    fun emptyPasswordsAreSkipped_andCounted() {
        val report = SecurityAuditor(FakeHibp()).run(listOf(item("1", "Kosong", ""), item("2", "Ada", strong)))
        assertEquals(1, report.entries.size)
        assertEquals(1, report.itemsWithoutPassword)
    }

    @Test
    fun cancellation_stopsRequests_andReportsRemainingAsUnchecked() {
        val hibp = FakeHibp()
        var allowed = 1
        val report = SecurityAuditor(hibp).run(
            items = listOf(item("1", "A", "password"), item("2", "B", strong), item("3", "C", "Zq8!wKm3#Tn5")),
            shouldContinue = { allowed-- > 0 }
        )
        assertEquals(1, hibp.requestedPrefixes.size)
        assertEquals(3, report.entries.size)
        assertTrue(report.entries.count { it.breachCheckFailed } >= 2)
    }

    @Test
    fun progressIsReportedPerPrefix() {
        val seen = mutableListOf<Pair<Int, Int>>()
        SecurityAuditor(FakeHibp()).run(
            listOf(item("1", "A", "password"), item("2", "B", strong)),
            onProgress = { done, total -> seen += done to total }
        )
        assertEquals(listOf(0 to 2, 1 to 2, 2 to 2), seen)
    }

    // ---------- HibpClient ----------

    private class RecordingHttp(val response: HttpResponse) : HttpExecutor {
        var last: HttpRequest? = null
        override fun execute(request: HttpRequest): HttpResponse { last = request; return response }
    }

    @Test
    fun hibpClient_buildsRangeRequest_withPadding() {
        val http = RecordingHttp(HttpResponse(200, "ABC:1"))
        assertEquals("ABC:1", HibpClient(http).range("5BAA6"))
        assertEquals("GET", http.last!!.method)
        assertEquals("https://api.pwnedpasswords.com/range/5BAA6", http.last!!.url)
        assertEquals("true", http.last!!.headers["Add-Padding"])
        assertEquals(null, http.last!!.body)
    }

    @Test
    fun hibpClient_rejectsMalformedPrefix_andServerErrors() {
        val ok = HibpClient(RecordingHttp(HttpResponse(200, "")))
        assertThrows(IllegalArgumentException::class.java) { ok.range("5baa6") }
        assertThrows(IllegalArgumentException::class.java) { ok.range("5BAA61E4C9B9") }
        assertThrows(IllegalArgumentException::class.java) { ok.range("../..") }
        assertThrows(NetworkException::class.java) { HibpClient(RecordingHttp(HttpResponse(429, ""))).range("5BAA6") }
    }
}
