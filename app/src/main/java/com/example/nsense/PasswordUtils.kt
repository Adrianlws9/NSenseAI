package com.example.nsense

import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordUtils {

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    // SHA-256 variant needs Android 8+. Older phones use the SHA-1 variant.
    private val algorithm: String
        get() = if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"

    // A random value added to each password, so equal passwords give different hashes
    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            Base64.decode(salt, Base64.NO_WRAP),
            ITERATIONS,
            KEY_BITS
        )
        val bytes = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun verify(password: String, salt: String, expectedHash: String): Boolean {
        val actual = hash(password, salt).toByteArray()
        return MessageDigest.isEqual(actual, expectedHash.toByteArray())
    }
}