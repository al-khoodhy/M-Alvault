package com.alvault.app.core.model

enum class MergeMode {
    /** Gabungkan berdasarkan id; entri dengan lastModified lebih baru menang. */
    MERGE,

    /** Ganti seluruh isi brankas lokal dengan data masuk. */
    REPLACE
}

data class MergeResult(val added: Int, val updated: Int, val skipped: Int)

data class MergeOutcome(val items: List<VaultItem>, val result: MergeResult)

object VaultMerge {

    fun apply(mode: MergeMode, local: List<VaultItem>, incoming: List<VaultItem>): MergeOutcome =
        when (mode) {
            MergeMode.MERGE -> merge(local, incoming)
            MergeMode.REPLACE -> replace(incoming)
        }

    fun merge(local: List<VaultItem>, incoming: List<VaultItem>): MergeOutcome {
        val byId = LinkedHashMap<String, VaultItem>()
        local.forEach { byId[it.id] = it }
        var added = 0
        var updated = 0
        var skipped = 0
        for (item in incoming) {
            val existing = byId[item.id]
            when {
                existing == null -> { byId[item.id] = item; added++ }
                item.lastModified > existing.lastModified -> { byId[item.id] = item; updated++ }
                else -> skipped++
            }
        }
        return MergeOutcome(byId.values.toList(), MergeResult(added, updated, skipped))
    }

    fun replace(incoming: List<VaultItem>): MergeOutcome {
        val unique = LinkedHashMap<String, VaultItem>()
        incoming.forEach { unique[it.id] = it }
        return MergeOutcome(unique.values.toList(), MergeResult(unique.size, 0, incoming.size - unique.size))
    }
}
