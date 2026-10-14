package com.alvault.app.core.model

import com.alvault.app.core.crypto.CryptoException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * (De)serialisasi `VaultItem[]` <-> JSON UTF-8, kompatibel dengan aplikasi web.
 * Decoder toleran: field tak dikenal diabaikan, field opsional boleh hilang/null.
 */
object VaultJson {

    fun encode(items: List<VaultItem>): ByteArray {
        val array = JsonArray(items.map { it.toJson() })
        return array.toString().toByteArray(StandardCharsets.UTF_8)
    }

    /** @throws CryptoException jika bukan array JSON. */
    fun decode(bytes: ByteArray): List<VaultItem> {
        val text = String(bytes, StandardCharsets.UTF_8).removePrefix("﻿")
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw CryptoException("Isi brankas bukan JSON yang valid.", e)
        }
        val array = root as? JsonArray ?: throw CryptoException("Isi brankas harus berupa array JSON.")
        return array.mapNotNull { element -> (element as? JsonObject)?.toVaultItem() }
    }

    private fun VaultItem.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        put("username", username)
        put("password", password)
        category?.let { put("category", it) }
        url?.let { put("url", it) }
        notes?.let { put("notes", it) }
        put("lastModified", lastModified)
    }

    private fun JsonObject.toVaultItem(): VaultItem? {
        val title = string("title").orEmpty()
        val username = string("username").orEmpty()
        val password = string("password").orEmpty()
        if (title.isBlank() && username.isBlank() && password.isBlank()) return null
        return VaultItem(
            id = string("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            title = title,
            username = username,
            password = password,
            category = string("category"),
            url = string("url"),
            notes = string("notes"),
            lastModified = long("lastModified") ?: 0L
        )
    }

    private fun JsonObject.string(key: String): String? {
        val element = this[key]
        return if (element is JsonPrimitive && element !is JsonNull) element.content else null
    }

    private fun JsonObject.long(key: String): Long? {
        val element = this[key] as? JsonPrimitive ?: return null
        if (element is JsonNull) return null
        return element.longOrNull ?: element.doubleOrNull?.toLong()
    }
}
