package com.example.journal.data

import android.content.Context
import androidx.core.content.edit
import javax.crypto.SecretKey

/**
 * Tasks live as one encrypted blob, exactly like notebooks. Nothing about a
 * task needs to be readable before unlock, because there are no reminders and
 * no widget text, so tasks add no plaintext to the database.
 */
class TaskStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("journal_tasks", Context.MODE_PRIVATE)

    fun load(key: SecretKey): List<Task> {
        val cipherText = prefs.getString(KEY_BLOB, null) ?: return emptyList()
        val iv = prefs.getString(KEY_IV, null) ?: return emptyList()
        val json = runCatching { CryptoManager.decrypt(key, cipherText, iv) }.getOrNull()
            ?: return emptyList()
        return TaskCodec.decode(json)
    }

    fun save(key: SecretKey, tasks: List<Task>) {
        val encrypted = CryptoManager.encrypt(key, TaskCodec.encode(tasks))
        prefs.edit {
            putString(KEY_BLOB, encrypted.cipherText)
            putString(KEY_IV, encrypted.iv)
        }
    }

    private companion object {
        const val KEY_BLOB = "blob"
        const val KEY_IV = "iv"
    }
}
