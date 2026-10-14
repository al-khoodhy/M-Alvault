package com.alvault.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultMergeTest {
    private fun item(id: String, title: String, modified: Long) =
        VaultItem(id = id, title = title, username = "u", password = "p", lastModified = modified)

    @Test
    fun merge_addsNewUpdatesNewerSkipsOlder() {
        val local = listOf(item("a", "A-lokal", 100), item("b", "B-lokal", 200))
        val incoming = listOf(
            item("a", "A-baru", 150),   // lebih baru -> update
            item("b", "B-lama", 100),   // lebih lama -> skip
            item("c", "C-baru", 50)     // belum ada -> tambah
        )
        val outcome = VaultMerge.merge(local, incoming)
        assertEquals(MergeResult(added = 1, updated = 1, skipped = 1), outcome.result)
        assertEquals(listOf("A-baru", "B-lokal", "C-baru"), outcome.items.map { it.title })
    }

    @Test
    fun merge_isIdempotent() {
        val local = listOf(item("a", "A", 100))
        val once = VaultMerge.merge(local, local)
        assertEquals(MergeResult(0, 0, 1), once.result)
        assertEquals(local, once.items)
    }

    @Test
    fun replace_dropsLocalItems() {
        val outcome = VaultMerge.apply(MergeMode.REPLACE, listOf(item("a", "A", 1)), listOf(item("z", "Z", 2)))
        assertEquals(listOf("Z"), outcome.items.map { it.title })
        assertEquals(1, outcome.result.added)
    }
}
