package com.alvault.app.core.crypto

/** Fixture dibuat oleh implementasi independen (Python: hashlib + cryptography), lihat README. */
object Fixtures {
    /** Password fixture: sengaja memakai karakter non-ASCII untuk menguji encoding UTF-8. */
    val PASSWORD = "Tes!AlVault-2026 é✓".toCharArray()

    fun text(name: String): String = String(bytes(name), Charsets.UTF_8)

    fun bytes(name: String): ByteArray {
        val stream = Fixtures::class.java.classLoader!!.getResourceAsStream("fixtures/$name")
            ?: error("Fixture tidak ditemukan: $name")
        return stream.use { it.readBytes() }
    }
}
