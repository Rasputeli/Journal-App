package com.example.journal.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class EntryRepository(
    private val dao: EntryDao,
    private val auth: AuthManager,
    private val database: JournalDatabase,
    private val context: Context,
    private val attachments: AttachmentStore = AttachmentStore(context),
) {

    fun observeEntries(): Flow<List<JournalEntry>> = dao.observeAll().map(::decryptAll)

    private fun decryptAll(rows: List<Entry>): List<JournalEntry> {
        val key = auth.currentKeyOrNull() ?: return emptyList()
        return rows.mapNotNull { row ->
            runCatching {
                val payload = PayloadCodec.decode(
                    CryptoManager.decrypt(key, row.cipherText, row.iv)
                )
                JournalEntry(
                    id = row.id,
                    date = LocalDate.parse(row.entryDate),
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    text = payload.text,
                    mood = payload.mood,
                    locked = payload.locked,
                    attachments = payload.attachments,
                    tags = TagParser.tagsOf(payload.text),
                )
            }.getOrNull()
        }
    }

    suspend fun save(
        id: Long?,
        date: LocalDate,
        text: String,
        mood: String?,
        locked: Boolean,
        attachmentNames: List<String>,
    ): Long {
        val key = auth.currentKey()
        val payload = EntryPayload(text, mood, locked, attachmentNames)
        val encrypted = CryptoManager.encrypt(key, PayloadCodec.encode(payload))
        val now = System.currentTimeMillis()

        if (id == null) {
            return dao.insert(
                Entry(
                    entryDate = date.toString(),
                    createdAt = now,
                    updatedAt = now,
                    cipherText = encrypted.cipherText,
                    iv = encrypted.iv,
                )
            )
        }

        val existing = dao.getById(id) ?: return -1L
        val previous = runCatching {
            PayloadCodec.decode(CryptoManager.decrypt(key, existing.cipherText, existing.iv))
        }.getOrNull()

        dao.update(
            existing.copy(
                entryDate = date.toString(),
                updatedAt = now,
                cipherText = encrypted.cipherText,
                iv = encrypted.iv,
            )
        )

        // Forget the pictures the user removed while editing.
        previous?.attachments
            ?.minus(attachmentNames.toSet())
            ?.forEach { attachments.delete(it) }

        return id
    }

    suspend fun delete(id: Long) {
        val row = dao.getById(id) ?: return
        auth.currentKeyOrNull()?.let { key ->
            runCatching {
                PayloadCodec.decode(CryptoManager.decrypt(key, row.cipherText, row.iv))
            }.getOrNull()?.attachments?.forEach { attachments.delete(it) }
        }
        dao.deleteById(id)
    }

    suspend fun addAttachment(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val key = auth.currentKeyOrNull() ?: return@withContext null
        runCatching { attachments.save(key, bytes) }.getOrNull()
    }

    suspend fun loadAttachment(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val key = auth.currentKeyOrNull() ?: return@withContext null
        attachments.load(key, name)
    }

    /** Sweeps pictures no entry references any more. Safe to call repeatedly. */
    suspend fun cleanUpAttachments(): Int = withContext(Dispatchers.IO) {
        val key = auth.currentKeyOrNull() ?: return@withContext 0
        val referenced = dao.getAll().flatMapTo(mutableSetOf()) { row ->
            runCatching {
                PayloadCodec.decode(CryptoManager.decrypt(key, row.cipherText, row.iv))
            }.getOrDefault(EntryPayload("")).attachments
        }
        attachments.collectGarbage(referenced)
    }

    /**
     * Verifies [current], then decrypts every entry with the old key and
     * re-encrypts it with a key derived from [new]. All-or-nothing.
     */
    suspend fun changePassword(current: CharArray, new: CharArray): Boolean {
        if (!auth.unlock(current)) return false

        val oldKey = auth.currentKey()
        val rows = dao.getAll()

        val newSalt = CryptoManager.newSalt()
        val newKey = CryptoManager.deriveKey(new, newSalt)

        val reEncrypted = rows.map { row ->
            val plain = CryptoManager.decrypt(oldKey, row.cipherText, row.iv)
            val encrypted = CryptoManager.encrypt(newKey, plain)
            row.copy(cipherText = encrypted.cipherText, iv = encrypted.iv)
        }

        database.withTransaction { dao.updateAll(reEncrypted) }
        auth.replacePassword(newSalt, newKey)
        return true
    }

    // ------------------------------------------------------------ backup

    /** Writes a single self-contained encrypted backup. Returns the entry count. */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val key = auth.currentKey()
        val salt = auth.saltCopy() ?: error("The journal has no password set.")
        val rows = dao.getAll()

        val entriesJson = JSONArray()
        rows.forEach { row ->
            val payload = PayloadCodec.decode(
                CryptoManager.decrypt(key, row.cipherText, row.iv)
            )

            val photosJson = JSONArray()
            payload.attachments.forEach { name ->
                attachments.load(key, name)?.let { bytes ->
                    photosJson.put(
                        JSONObject().apply {
                            put("name", name)
                            put("data", CryptoManager.encode(bytes))
                        }
                    )
                }
            }

            entriesJson.put(
                JSONObject().apply {
                    put("date", row.entryDate)
                    put("createdAt", row.createdAt)
                    put("updatedAt", row.updatedAt)
                    put("text", payload.text)
                    payload.mood?.let { put("mood", it) }
                    if (payload.locked) put("locked", true)
                    if (photosJson.length() > 0) put("attachments", photosJson)
                }
            )
        }

        val inner = JSONObject().apply {
            put("version", 3)
            put("entries", entriesJson)
        }.toString()

        val encrypted = CryptoManager.encrypt(key, inner)

        val envelope = JSONObject().apply {
            put("app", "journal")
            put("version", 3)
            put("exportedAt", System.currentTimeMillis())
            put("kdf", JSONObject().apply {
                put("algo", CryptoManager.PBKDF2_ALGORITHM)
                put("iterations", CryptoManager.ITERATIONS)
                put("salt", CryptoManager.encode(salt))
            })
            put("cipher", JSONObject().apply {
                put("algo", CryptoManager.TRANSFORMATION)
                put("iv", encrypted.iv)
                put("data", encrypted.cipherText)
            })
        }.toString(2)

        context.contentResolver.openOutputStream(uri).use { stream ->
            if (stream == null) error("Could not open the destination file.")
            stream.write(envelope.toByteArray(Charsets.UTF_8))
        }
        rows.size
    }

    /**
     * Reads a backup, decrypting it with [password] against the salt stored
     * in the file, then re-encrypts everything under the current journal key.
     */
    suspend fun importFrom(uri: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val fileText = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: error("Could not read that file.")

        val envelope = runCatching { JSONObject(fileText) }
            .getOrElse { error("That file isn't a readable Journal backup.") }
        require(envelope.optString("app") == "journal") { "That file isn't a Journal backup." }

        val kdf = envelope.getJSONObject("kdf")
        val cipher = envelope.getJSONObject("cipher")
        val salt = CryptoManager.decode(kdf.getString("salt"))
        val iterations = kdf.optInt("iterations", CryptoManager.ITERATIONS)

        val fileKey = CryptoManager.deriveKey(password, salt, iterations)
        val inner = runCatching {
            CryptoManager.decrypt(fileKey, cipher.getString("data"), cipher.getString("iv"))
        }.getOrElse { error("Wrong password for this backup, or the file is damaged.") }

        val incoming = runCatching { JSONObject(inner).getJSONArray("entries") }
            .getOrElse { error("This backup looks corrupted inside.") }

        val currentKey = auth.currentKey()
        val now = System.currentTimeMillis()

        val rows = buildList {
            for (i in 0 until incoming.length()) {
                val item = incoming.getJSONObject(i)
                val date = runCatching { LocalDate.parse(item.getString("date")) }
                    .getOrNull() ?: continue
                val text = item.optString("text")
                if (text.isBlank()) continue
                val mood = if (item.isNull("mood")) null
                else item.optString("mood").ifBlank { null }

                val photos = item.optJSONArray("attachments")
                val names = buildList {
                    if (photos != null) {
                        for (p in 0 until photos.length()) {
                            val photo = photos.getJSONObject(p)
                            val bytes = runCatching {
                                CryptoManager.decode(photo.getString("data"))
                            }.getOrNull() ?: continue
                            runCatching { attachments.save(currentKey, bytes) }
                                .getOrNull()?.let { add(it) }
                        }
                    }
                }

                val encrypted = CryptoManager.encrypt(
                    currentKey,
                    PayloadCodec.encode(
                        EntryPayload(
                            text = text,
                            mood = mood,
                            locked = item.optBoolean("locked", false),
                            attachments = names,
                        )
                    ),
                )

                add(
                    Entry(
                        entryDate = date.toString(),
                        createdAt = item.optLong("createdAt", now),
                        updatedAt = item.optLong("updatedAt", now),
                        cipherText = encrypted.cipherText,
                        iv = encrypted.iv,
                    )
                )
            }
        }

        if (rows.isNotEmpty()) dao.insertAll(rows)
        rows.size
    }
}
