package com.example.journal.data

import android.content.Context
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Encrypted photo files live in filesDir/attachments as [IV][ciphertext].
 * Nothing writes a decrypted copy to disk: pictures are decrypted in memory
 * only, at the moment they are displayed.
 */
class AttachmentStore(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, "attachments").apply { if (!exists()) mkdirs() }

    fun save(key: SecretKey, bytes: ByteArray): String {
        val name = UUID.randomUUID().toString().replace("-", "") + ".bin"
        File(directory, name).writeBytes(CryptoManager.encryptBytes(key, bytes))
        return name
    }

    fun load(key: SecretKey, name: String): ByteArray? {
        val file = File(directory, name)
        if (!file.isFile) return null
        return runCatching { CryptoManager.decryptBytes(key, file.readBytes()) }.getOrNull()
    }

    fun delete(name: String) {
        File(directory, name).delete()
    }

    /**
     * Deletes files no entry points at any more. Files younger than
     * [minAgeMillis] are left alone, so a picture attached to an entry that
     * is still open in the editor can never be swept away.
     */
    fun collectGarbage(referenced: Set<String>, minAgeMillis: Long = 60L * 60L * 1000L): Int {
        val cutoff = System.currentTimeMillis() - minAgeMillis
        var deleted = 0
        directory.listFiles()?.forEach { file ->
            if (file.name !in referenced && file.lastModified() < cutoff) {
                if (file.delete()) deleted++
            }
        }
        return deleted
    }
}
