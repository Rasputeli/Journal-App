package com.example.journal.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import javax.crypto.SecretKey

/**
 * Holds the derived key in memory for the current session and stores the
 * salt plus an encrypted verifier blob used to check a password.
 */
class AuthManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var sessionKey: SecretKey? = null

    val hasPassword: Boolean
        get() = prefs.contains(KEY_SALT) &&
                prefs.contains(KEY_VERIFIER) &&
                prefs.contains(KEY_VERIFIER_IV)

    fun currentKey(): SecretKey = sessionKey ?: error("Journal is locked")
    fun currentKeyOrNull(): SecretKey? = sessionKey

    /** The PBKDF2 salt. Backups include it so a file can be opened elsewhere. */
    fun saltCopy(): ByteArray? =
        prefs.getString(KEY_SALT, null)?.let(CryptoManager::decode)

    fun setupPassword(password: CharArray): Boolean {
        if (hasPassword) return false
        val salt = CryptoManager.newSalt()
        val key = CryptoManager.deriveKey(password, salt)
        writeVerifier(salt, key)
        sessionKey = key
        return true
    }

    fun unlock(password: CharArray): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.let(CryptoManager::decode) ?: return false
        val verifier = prefs.getString(KEY_VERIFIER, null) ?: return false
        val iv = prefs.getString(KEY_VERIFIER_IV, null) ?: return false

        val candidate = CryptoManager.deriveKey(password, salt)
        val ok = try {
            CryptoManager.decrypt(candidate, verifier, iv) == VERIFIER_TEXT
        } catch (t: Throwable) {
            false
        }
        if (ok) sessionKey = candidate
        return ok
    }

    /** Used after a password change, when every entry has been re-encrypted. */
    fun replacePassword(newSalt: ByteArray, newKey: SecretKey) {
        writeVerifier(newSalt, newKey)
        sessionKey = newKey
    }

    fun lock() {
        sessionKey = null
    }

    private fun writeVerifier(salt: ByteArray, key: SecretKey) {
        val verifier = CryptoManager.encrypt(key, VERIFIER_TEXT)
        prefs.edit(commit = true) {
            putString(KEY_SALT, CryptoManager.encode(salt))
            putString(KEY_VERIFIER, verifier.cipherText)
            putString(KEY_VERIFIER_IV, verifier.iv)
        }
    }

    private companion object {
        const val PREFS_NAME = "journal_auth"
        const val KEY_SALT = "salt"
        const val KEY_VERIFIER = "verifier"
        const val KEY_VERIFIER_IV = "verifier_iv"
        const val VERIFIER_TEXT = "journal-unlock-check-v1"
    }
}
