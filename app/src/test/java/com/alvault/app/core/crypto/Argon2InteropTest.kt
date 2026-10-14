package com.alvault.app.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Memverifikasi pemisahan kunci + konvensi salt `"{email}:{localSalt}"` terhadap implementasi Argon2id
 * INDEPENDEN: vektor dibuat oleh Python `argon2-cffi` (libargon2 referensi, v1.3), sedangkan hasher di sini
 * memakai Argon2BytesGenerator milik BouncyCastle. Produksi memakai `argon2kt` (libargon2 referensi via JNI),
 * yang menghasilkan keluaran identik untuk parameter yang sama.
 */
class Argon2InteropTest {

    private class BouncyCastleArgon2 : Argon2Hasher {
        override fun hash(
            password: ByteArray, salt: ByteArray, iterations: Int,
            memoryKib: Int, parallelism: Int, outputLength: Int
        ): ByteArray {
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKib)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build()
            val out = ByteArray(outputLength)
            Argon2BytesGenerator().apply { init(params) }.generateBytes(password, out)
            return out
        }
    }

    private val password = "Rahasia-Utama#2026 é✓".toCharArray()
    private val localSalt = "EBESExQVFhcYGRobHB0eHw==" // Base64(0x10..0x1F)
    private val expectedEncryptionKeyHex = "4769b705579facd41f6e753547780d4378121250760e8c8342b0b6e305262b98"
    private val expectedAuthHash = "ci1uxpRqDvkmN84TDBIKROK+YnVCNEo4nFGJzYQH/DU="

    @Test
    fun `salt is UTF-8 of normalized email colon localSalt`() {
        val salt = MasterKeyDeriver.argonSalt("  Alice@Example.COM ", localSalt)
        assertEquals("alice@example.com:$localSalt", String(salt, Charsets.UTF_8))
    }

    @Test
    fun `argon2id split matches independent known answer`() {
        val keys = MasterKeyDeriver.derive(BouncyCastleArgon2(), " Alice@Example.com", password, localSalt)
        assertEquals(expectedEncryptionKeyHex, Bytes.toHex(keys.encryptionKey))
        assertEquals(expectedAuthHash, keys.localAuthHash)
        assertEquals(32, keys.encryptionKey.size)
        assertEquals(32, Base64Codec.decode(keys.localAuthHash).size)
    }

    @Test
    fun `encryption key and auth hash are disjoint halves of the 64 byte output`() {
        val full = BouncyCastleArgon2().hash(
            Bytes.utf8(password), MasterKeyDeriver.argonSalt("alice@example.com", localSalt),
            MasterKeyDeriver.ARGON2_ITERATIONS, MasterKeyDeriver.ARGON2_MEMORY_KIB, MasterKeyDeriver.ARGON2_PARALLELISM, 64
        )
        val keys = MasterKeyDeriver.derive(BouncyCastleArgon2(), "alice@example.com", password, localSalt)
        assertArrayEquals(full.copyOfRange(0, 32), keys.encryptionKey)
        assertEquals(Base64Codec.encode(full.copyOfRange(32, 64)), keys.localAuthHash)
        assertTrue(!keys.encryptionKey.contentEquals(full.copyOfRange(32, 64)))
    }

    @Test
    fun `spec parameters are 64 MiB, 3 iterations, parallelism 1, 64 byte output`() {
        assertEquals(65_536, MasterKeyDeriver.ARGON2_MEMORY_KIB)
        assertEquals(3, MasterKeyDeriver.ARGON2_ITERATIONS)
        assertEquals(1, MasterKeyDeriver.ARGON2_PARALLELISM)
        assertEquals(64, MasterKeyDeriver.OUTPUT_LENGTH)
    }
}
