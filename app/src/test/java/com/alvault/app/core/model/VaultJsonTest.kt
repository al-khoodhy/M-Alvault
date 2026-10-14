package com.alvault.app.core.model

import com.alvault.app.core.crypto.CryptoException
import com.alvault.app.core.crypto.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultJsonTest {

    @Test
    fun decode_parsesWebGeneratedJson() {
        val items = VaultJson.decode(Fixtures.bytes("web-sample.plain.json"))
        assertEquals(2, items.size)
        val github = items[0]
        assertEquals("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d", github.id)
        assertEquals("GitHub", github.title)
        assertEquals("SecretPassword123!", github.password)
        assertEquals("Kustom...", github.category)
        assertEquals("Token API backup", github.notes)
        assertEquals(1774945800000L, github.lastModified)

        val bank = items[1]
        assertEquals("Bank Éclair ✓", bank.title)
        assertEquals("p\"a\\ss/wörd+==", bank.password)
        assertNull(bank.url)
        assertNull(bank.notes)
    }

    @Test
    fun roundTrip_preservesAllFields() {
        val original = VaultJson.decode(Fixtures.bytes("web-sample.plain.json"))
        val again = VaultJson.decode(VaultJson.encode(original))
        assertEquals(original, again)
    }

    @Test
    fun encode_omitsNullOptionalFields_andUsesIntegerMillis() {
        val json = String(
            VaultJson.encode(listOf(VaultItem("id-1", "T", "u", "p", lastModified = 1700000000000L))),
            Charsets.UTF_8
        )
        assertFalse(json.contains("category"))
        assertFalse(json.contains("url"))
        assertFalse(json.contains("notes"))
        assertTrue(json.contains("\"lastModified\":1700000000000"))
    }

    @Test
    fun decode_isTolerantToMissingAndExtraFields() {
        val json = """
            [
              {"title":"A","username":"u","password":"p","lastModified":1.7E12,"extra":{"x":1},"category":null},
              {"id":"fixed","title":"B","username":"","password":"q"},
              {"title":"","username":"","password":""},
              "bukan objek"
            ]
        """.trimIndent()
        val items = VaultJson.decode(json.toByteArray())
        assertEquals(2, items.size)
        assertEquals(1_700_000_000_000L, items[0].lastModified)
        assertTrue(items[0].id.isNotBlank()) // id dibuat bila hilang
        assertNull(items[0].category)
        assertEquals("fixed", items[1].id)
        assertEquals(0L, items[1].lastModified)
    }

    @Test
    fun decode_rejectsNonArray() {
        assertThrows(CryptoException::class.java) { VaultJson.decode("{\"a\":1}".toByteArray()) }
        assertThrows(CryptoException::class.java) { VaultJson.decode("xyz".toByteArray()) }
    }

    @Test
    fun emptyVault_isEmptyJsonArray() {
        assertEquals("[]", String(VaultJson.encode(emptyList()), Charsets.UTF_8))
        assertTrue(VaultJson.decode("[]".toByteArray()).isEmpty())
    }
}
