package com.alvault.app.core.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Serialisasi berkas `.aegis`:
 * `{"v":1,"s":"<b64 salt 16B>","i":"<b64 iv 12B>","c":"<b64 ciphertext+tag>"}`
 */
object AegisFile {

    fun toJson(payload: AegisPayload): String = buildJsonObject {
        put("v", payload.version)
        put("s", Base64Codec.encode(payload.salt))
        put("i", Base64Codec.encode(payload.iv))
        put("c", Base64Codec.encode(payload.ciphertext))
    }.toString()

    /** @throws CryptoException jika berkas bukan format `.aegis` yang valid. */
    fun parse(content: String): AegisPayload {
        val text = content.removePrefix("﻿").trim()
        val obj = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: throw CryptoException("Berkas bukan format .aegis yang valid.")

        val version = (obj["v"] as? JsonPrimitive)?.intOrNull
            ?: throw CryptoException("Berkas .aegis tidak memiliki field versi (v).")
        if (version != AegisCrypto.VERSION) {
            throw CryptoException("Versi berkas .aegis ($version) tidak didukung.")
        }

        val salt = Base64Codec.decode(stringField(obj, "s"))
        val iv = Base64Codec.decode(stringField(obj, "i"))
        val ciphertext = Base64Codec.decode(stringField(obj, "c"))

        if (salt.isEmpty()) throw CryptoException("Salt pada berkas .aegis kosong.")
        if (iv.size != AegisCrypto.IV_SIZE) throw CryptoException("IV pada berkas .aegis tidak valid.")
        if (ciphertext.size < AesGcm.TAG_SIZE) throw CryptoException("Ciphertext pada berkas .aegis terlalu pendek.")

        return AegisPayload(version, salt, iv, ciphertext)
    }

    private fun stringField(obj: JsonObject, key: String): String {
        val primitive = obj[key] as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            throw CryptoException("Berkas .aegis tidak memiliki field '$key'.")
        }
        return primitive.content
    }
}
