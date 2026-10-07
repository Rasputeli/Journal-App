package com.example.journal.data

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class Encrypted(val cipherText: String, val iv: String)

object CryptoManager {

    const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val ITERATIONS = 210_000
    const val IV_BYTES = 12

    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val GCM_TAG_BITS = 128

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    private fun newIv(): ByteArray = ByteArray(IV_BYTES).also(random::nextBytes)

    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = ITERATIONS,
    ): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val bytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(key: SecretKey, plaintext: String): Encrypted {
        val iv = newIv()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val body = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Encrypted(encode(body), encode(iv))
    }

    fun decrypt(key: SecretKey, cipherText: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, decode(iv)))
        return String(cipher.doFinal(decode(cipherText)), Charsets.UTF_8)
    }

    /** Blob layout for attachments: [12-byte IV][GCM ciphertext]. */
    fun encryptBytes(key: SecretKey, plain: ByteArray): ByteArray {
        val iv = newIv()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val body = cipher.doFinal(plain)
        return ByteArrayOutputStream(iv.size + body.size).apply {
            write(iv)
            write(body)
        }.toByteArray()
    }

    fun decryptBytes(key: SecretKey, blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "Attachment is truncated." }
        val iv = blob.copyOfRange(0, IV_BYTES)
        val body = blob.copyOfRange(IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
}
