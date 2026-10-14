package com.alvault.app.core.vault

import com.alvault.app.core.model.VaultCategories
import com.alvault.app.core.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultQueryTest {

    private fun item(id: String, title: String, user: String = "u", cat: String? = null, url: String? = null, pw: String = "pw", notes: String? = null) =
        VaultItem(id, title, user, pw, cat, url, notes, 1L)

    private val items = listOf(
        item("1", "github", "dev@example.com", "Pekerjaan", "https://github.com"),
        item("2", "Bank BCA", "ani", "Bank / Finansial"),
        item("3", "Instagram", "ani_toko", "Instagram", notes = "rahasia-catatan", pw = "kata-sandi-unik"),
        item("4", "Zoom", "dev@example.com", "Kantor Kustom")
    )

    @Test
    fun `empty query returns everything sorted by title ignoring case`() {
        assertEquals(listOf("2", "1", "3", "4"), VaultQuery.filter(items, "", null).map { it.id })
    }

    @Test
    fun `search matches title username url and category case-insensitively`() {
        assertEquals(listOf("1"), VaultQuery.filter(items, "GITHUB.COM", null).map { it.id })
        assertEquals(listOf("2", "3"), VaultQuery.filter(items, "ani", null).map { it.id })
        assertEquals(listOf("2"), VaultQuery.filter(items, "finansial", null).map { it.id })
        assertEquals(listOf("1", "4"), VaultQuery.filter(items, "  dev@example ", null).map { it.id })
    }

    @Test
    fun `password and notes are never searched`() {
        assertEquals(emptyList<VaultItem>(), VaultQuery.filter(items, "kata-sandi-unik", null))
        assertEquals(emptyList<VaultItem>(), VaultQuery.filter(items, "rahasia-catatan", null))
    }

    @Test
    fun `category filter combines with query`() {
        assertEquals(listOf("2", "3"), VaultQuery.filter(items, "ani", null).map { it.id })
        assertEquals(listOf("3"), VaultQuery.filter(items, "ani", "Instagram").map { it.id })
        assertEquals(emptyList<VaultItem>(), VaultQuery.filter(items, "github", "Instagram"))
    }

    @Test
    fun `used categories are distinct sorted and ignore blanks`() {
        val withBlank = items + item("5", "x", cat = " ") + item("6", "y", cat = null) + item("7", "w", cat = "Pekerjaan")
        assertEquals(listOf("Bank / Finansial", "Instagram", "Kantor Kustom", "Pekerjaan"), VaultQuery.usedCategories(withBlank))
    }

    @Test
    fun `category options are defaults plus new custom ones once`() {
        val options = VaultQuery.categoryOptions(items)
        assertEquals(VaultCategories.DEFAULTS, options.take(VaultCategories.DEFAULTS.size))
        assertEquals(listOf("Kantor Kustom"), options.drop(VaultCategories.DEFAULTS.size))
    }

    @Test
    fun `upsert replaces by id or appends`() {
        val replaced = VaultQuery.upsert(items, item("2", "Bank BCA baru"))
        assertEquals(4, replaced.size)
        assertEquals("Bank BCA baru", VaultQuery.find(replaced, "2")!!.title)
        assertEquals(listOf("1", "2", "3", "4"), replaced.map { it.id })

        val appended = VaultQuery.upsert(items, item("9", "Baru"))
        assertEquals(5, appended.size)
        assertEquals("9", appended.last().id)
    }

    @Test
    fun `delete removes only matching id`() {
        assertEquals(listOf("1", "3", "4"), VaultQuery.delete(items, "2").map { it.id })
        assertEquals(4, VaultQuery.delete(items, "tidak-ada").size)
        assertNull(VaultQuery.find(items, "tidak-ada"))
    }
}
