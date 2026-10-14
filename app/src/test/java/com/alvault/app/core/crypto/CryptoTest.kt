package com.alvault.app.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoTest {

    private fun hex(bytes: ByteArray) = Bytes.toHex(bytes)

    // ---------- PBKDF2-HMAC-SHA256 ----------

    @Test
    fun pbkdf2_matchesKnownVectors() {
        val pw = "password".toCharArray()
        val salt = "salt".toByteArray()
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            hex(Pbkdf2.deriveKey(pw, salt, 1))
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            hex(Pbkdf2.deriveKey(pw, salt, 2))
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            hex(Pbkdf2.deriveKey(pw, salt, 4096))
        )
    }

    // ---------- AES-256-GCM ----------

    @Test
    fun aesGcm_roundTrip_andTamperDetection() {
        val key = AesGcm.randomBytes(32)
        val iv = AesGcm.randomBytes(12)
        val plain = "rahasia ✓".toByteArray()
        val sealed = AesGcm.encrypt(key, iv, plain)
        assertEquals(plain.size + 16, sealed.size) // ciphertext + tag 16 byte
        assertArrayEquals(plain, AesGcm.decrypt(key, iv, sealed))

        val tampered = sealed.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(CryptoException::class.java) { AesGcm.decrypt(key, iv, tampered) }

        val wrongKey = AesGcm.randomBytes(32)
        assertThrows(CryptoException::class.java) { AesGcm.decrypt(wrongKey, iv, sealed) }
    }

    // ---------- .aegis ----------

    @Test
    fun aegis_decryptsFileFromIndependentImplementation() {
        val payload = AegisFile.parse(Fixtures.text("web-sample.aegis"))
        assertEquals(1, payload.version)
        assertEquals(16, payload.salt.size)
        assertEquals(12, payload.iv.size)
        val plain = AegisCrypto.decrypt(payload, Fixtures.PASSWORD)
        assertArrayEquals(Fixtures.bytes("web-sample.plain.json"), plain)
    }

    @Test
    fun aegis_encryptIsBitIdenticalToIndependentImplementation() {
        // Salt & IV sama dengan fixture -> keluaran harus identik byte-per-byte.
        val expected = AegisFile.parse(Fixtures.text("web-sample.aegis"))
        val actual = AegisCrypto.encrypt(
            plaintext = Fixtures.bytes("web-sample.plain.json"),
            password = Fixtures.PASSWORD,
            salt = ByteArray(16) { it.toByte() },
            iv = ByteArray(12) { (0xA0 + it).toByte() }
        )
        assertArrayEquals(expected.ciphertext, actual.ciphertext)
        assertEquals(
            Fixtures.text("web-sample.aegis").trim(),
            AegisFile.toJson(actual)
        )
    }

    @Test
    fun aegis_roundTrip_andWrongPassword() {
        val plain = """[{"id":"1","title":"x"}]""".toByteArray()
        val payload = AegisCrypto.encrypt(plain, "kata-sandi-1".toCharArray())
        val file = AegisFile.toJson(payload)
        val parsed = AegisFile.parse(file)
        assertArrayEquals(plain, AegisCrypto.decrypt(parsed, "kata-sandi-1".toCharArray()))
        assertThrows(CryptoException::class.java) {
            AegisCrypto.decrypt(parsed, "kata-sandi-2".toCharArray())
        }
    }

    @Test
    fun aegisFile_rejectsInvalidInput() {
        assertThrows(CryptoException::class.java) { AegisFile.parse("bukan json") }
        assertThrows(CryptoException::class.java) { AegisFile.parse("[]") }
        assertThrows(CryptoException::class.java) { AegisFile.parse("""{"v":2,"s":"AAAA","i":"AAAAAAAAAAAAAAAA","c":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}""") }
        assertThrows(CryptoException::class.java) { AegisFile.parse("""{"v":1,"s":"!!","i":"x","c":"y"}""") }
        // IV bukan 12 byte
        assertThrows(CryptoException::class.java) { AegisFile.parse("""{"v":1,"s":"AAAAAAAAAAAAAAAAAAAAAA==","i":"AAAA","c":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}""") }
    }

    @Test
    fun aegisFile_toleratesBomAndWhitespace() {
        val content = "﻿\n" + Fixtures.text("web-sample.aegis") + "\n"
        val payload = AegisFile.parse(content)
        assertArrayEquals(
            Fixtures.bytes("web-sample.plain.json"),
            AegisCrypto.decrypt(payload, Fixtures.PASSWORD)
        )
    }

    // ---------- Vault blob ----------

    @Test
    fun vaultBlob_formatAndRoundTrip() {
        val key = AesGcm.randomBytes(32)
        val plain = "[]".toByteArray()
        val blob = VaultBlob.seal(key, plain)
        assertTrue(Base64Codec.isValid(blob))
        val raw = Base64Codec.decode(blob)
        assertEquals(12 + plain.size + 16, raw.size) // IV + ciphertext + tag
        assertArrayEquals(plain, VaultBlob.open(key, blob))

        // IV acak: dua blob dari plaintext sama harus berbeda
        assertFalse(blob == VaultBlob.seal(key, plain))
    }

    @Test
    fun vaultBlob_rejectsBadInput() {
        val key = AesGcm.randomBytes(32)
        assertThrows(CryptoException::class.java) { VaultBlob.open(key, "bukan base64!") }
        assertThrows(CryptoException::class.java) { VaultBlob.open(key, "QUJD") }
        val blob = VaultBlob.seal(key, "data".toByteArray())
        assertThrows(CryptoException::class.java) { VaultBlob.open(AesGcm.randomBytes(32), blob) }
    }

    @Test
    fun base64Codec_acceptsLineBreaksAndValidatesPadding() {
        val data = ByteArray(100) { it.toByte() }
        val encoded = Base64Codec.encode(data)
        val wrapped = encoded.chunked(40).joinToString("\r\n")
        assertArrayEquals(data, Base64Codec.decode(wrapped))
        assertFalse(Base64Codec.isValid("abc==="))
        assertFalse(Base64Codec.isValid("ab-_"))
    }

    // ---------- Pemisahan kunci master (Argon2id 64B) ----------

    @Test
    fun masterKeyDeriver_splitsOutputIntoEncryptionKeyAndAuthHash() {
        var capturedSalt = ""
        var capturedParams = ""
        val fake = object : Argon2Hasher {
            override fun hash(
                password: ByteArray, salt: ByteArray, iterations: Int,
                memoryKib: Int, parallelism: Int, outputLength: Int
            ): ByteArray {
                capturedSalt = String(salt, Charsets.UTF_8)
                capturedParams = "$iterations/$memoryKib/$parallelism/$outputLength"
                return ByteArray(outputLength) { it.toByte() }
            }
        }
        val keys = MasterKeyDeriver.derive(fake, "  User@Example.COM ", "pw".toCharArray(), "SALTB64==")
        assertEquals("user@example.com:SALTB64==", capturedSalt)
        assertEquals("3/65536/1/64", capturedParams)
        assertArrayEquals(ByteArray(32) { it.toByte() }, keys.encryptionKey)
        assertEquals(Base64Codec.encode(ByteArray(32) { (32 + it).toByte() }), keys.localAuthHash)
    }
}
