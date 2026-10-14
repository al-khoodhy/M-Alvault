package com.alvault.app.core.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode

/** Argon2id lewat pustaka native `argon2kt` (Argon2 reference implementation via JNI). */
class Argon2ktHasher : Argon2Hasher {
    private val argon2 by lazy { Argon2Kt() }

    override fun hash(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        memoryKib: Int,
        parallelism: Int,
        outputLength: Int
    ): ByteArray {
        val result = argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password,
            salt = salt,
            tCostInIterations = iterations,
            mCostInKibibyte = memoryKib,
            parallelism = parallelism,
            hashLengthInBytes = outputLength
        )
        return result.rawHashAsByteArray()
    }
}
