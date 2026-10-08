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
    private val notebookStore: NotebookStore = NotebookStore(context),
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
                    eventTime = payload.eventTime ?: row.createdAt,
                    recordedAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    text = payload.text,
                    valence = payload.valence,
                    customMood = payload.customMood,
                    notebookId = payload.notebookId ?: NotebookSet.DEFAULT_ID,
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
        eventTime: Long,
        text: String,
        valence: Int?,
        customMood: String?,
        notebookId: Long,
        locked: Boolean,
        attachmentNames: List<String>,
    ): Long {
        val key = auth.currentKey()
        val payload = EntryPayload(
            text = text,
            eventTime = eventTime,
            valence = valence,
            customMood = customMood,
            notebookId = notebookId,
            locked = locked,
            attachments = attachmentNames,
        )
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
            PayloadCodec.decode(
                CryptoManager.decrypt(key, existing.cipherText, existing.iv)
            )
        }.getOrNull()

        dao.update(
            existing.copy(
                entryDate = date.toString(),
                updatedAt = now,
                cipherText = encrypted.cipherText,
                iv = encrypted.iv,
            )
        )

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

    suspend fun cleanUpAttachments(): Int = withContext(Dispatchers.IO) {
        val key = auth.currentKeyOrNull() ?: return@withContext 0
        val referenced = dao.getAll().flatMapTo(mutableSetOf()) { row ->
            runCatching {
                PayloadCodec.decode(CryptoManager.decrypt(key, row.cipherText, row.iv))
            }.getOrDefault(EntryPayload("")).attachments
        }
        attachments.collectGarbage(referenced)
    }

    // ------------------------------------------------------------- notebooks

    suspend fun notebooks(): NotebookSet = withContext(Dispatchers.IO) {
        notebookStore.load(auth.currentKey())
    }

    suspend fun saveNotebooks(set: NotebookSet) = withContext(Dispatchers.IO) {
        notebookStore.save(auth.currentKey(), set)
    }

    /** Moves every block out of [notebookId] into [fallbackId]. Returns how many moved. */
    suspend fun reassignNotebook(notebookId: Long, fallbackId: Long): Int =
        withContext(Dispatchers.IO) {
            val key = auth.currentKey()
            val rows = dao.getAll()
            val changed = mutableListOf<Entry>()

            rows.forEach { row ->
                val payload = runCatching {
                    PayloadCodec.decode(
                        CryptoManager.decrypt(key, row.cipherText, row.iv)
                    )
                }.getOrNull() ?: return@forEach

                val current = payload.notebookId ?: NotebookSet.DEFAULT_ID
                if (current != notebookId) return@forEach

                val encrypted = CryptoManager.encrypt(
                    key,
                    PayloadCodec.encode(payload.copy(notebookId = fallbackId)),
                )
                changed += row.copy(cipherText = encrypted.cipherText, iv = encrypted.iv)
            }

            if (changed.isNotEmpty()) dao.updateAll(changed)
            changed.size
        }

    // ---------------------------------------------------------- password

    suspend fun changePassword(current: CharArray, new: CharArray): Boolean {
        if (!auth.unlock(current)) return false

        val oldKey = auth.currentKey()
        val rows = dao.getAll()
        val notebooks = notebookStore.load(oldKey)

        val newSalt = CryptoManager.newSalt()
        val newKey = CryptoManager.deriveKey(new, newSalt)

        val reEncrypted = rows.map { row ->
            val plain = CryptoManager.decrypt(oldKey, row.cipherText, row.iv)
            val encrypted = CryptoManager.encrypt(newKey, plain)
            row.copy(cipherText = encrypted.cipherText, iv = encrypted.iv)
        }

        database.withTransaction { dao.updateAll(reEncrypted) }
        notebookStore.save(newKey, notebooks)
        auth.replacePassword(newSalt, newKey)
        return true
    }

    // ------------------------------------------------------------- backup

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
                    val photo = JSONObject()
                    photo.put("name", name)
                    photo.put("data", CryptoManager.encode(bytes))
                    photosJson.put(photo)
                }
            }

            val item = JSONObject()
            item.put("date", row.entryDate)
            item.put("recordedAt", row.createdAt)
            item.put("eventTime", payload.eventTime ?: row.createdAt)
            item.put("updatedAt", row.updatedAt)
            item.put("text", payload.text)
            payload.valence?.let { item.put("valence", it) }
            payload.customMood?.let { item.put("customMood", it) }
            payload.notebookId?.let { item.put("notebook", it) }
            if (payload.locked) item.put("locked", true)
            if (photosJson.length() > 0) item.put("attachments", photosJson)
            entriesJson.put(item)
        }

        val inner = JSONObject()
        inner.put("version", 4)
        inner.put("notebooks", encodeNotebooks(notebookStore.load(key)))
        inner.put("entries", entriesJson)

        val encrypted = CryptoManager.encrypt(key, inner.toString())

        val envelope = JSONObject()
        envelope.put("app", "journal")
        envelope.put("version", 4)
        envelope.put("exportedAt", System.currentTimeMillis())

        val kdf = JSONObject()
        kdf.put("algo", CryptoManager.PBKDF2_ALGORITHM)
        kdf.put("iterations", CryptoManager.ITERATIONS)
        kdf.put("salt", CryptoManager.encode(salt))
        envelope.put("kdf", kdf)

        val cipher = JSONObject()
        cipher.put("algo", CryptoManager.TRANSFORMATION)
        cipher.put("iv", encrypted.iv)
        cipher.put("data", encrypted.cipherText)
        envelope.put("cipher", cipher)

        context.contentResolver.openOutputStream(uri).use { stream ->
            if (stream == null) error("Could not open the destination file.")
            stream.write(envelope.toString(2).toByteArray(Charsets.UTF_8))
        }
        rows.size
    }

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
            CryptoManager.decrypt(
                fileKey,
                cipher.getString("data"),
                cipher.getString("iv"),
            )
        }.getOrElse { error("Wrong password for this backup, or the file is damaged.") }

        val root = runCatching { JSONObject(inner) }
            .getOrElse { error("This backup looks corrupted inside.") }
        val incoming = runCatching { root.getJSONArray("entries") }
            .getOrElse { error("This backup has no entries in it.") }

        val key = auth.currentKey()
        val merged = mergeNotebooks(notebookStore.load(key), root.optJSONArray("notebooks"))
        notebookStore.save(key, merged)
        val known = merged.notebooks.map { it.id }.toSet()
        val fallback = merged.displayOrder.firstOrNull()?.id ?: NotebookSet.DEFAULT_ID

        val now = System.currentTimeMillis()
        val rows = buildList {
            for (i in 0 until incoming.length()) {
                val item = incoming.optJSONObject(i) ?: continue
                val date = runCatching { LocalDate.parse(item.getString("date")) }
                    .getOrNull() ?: continue
                val text = item.optString("text")
                if (text.isBlank()) continue

                val valence = if (item.has("valence") && !item.isNull("valence"))
                    item.optInt("valence").coerceIn(MoodScale.MIN, MoodScale.MAX) else null
                val custom = item.optString("customMood")
                    .takeIf { it.isNotBlank() && !item.isNull("customMood") }
                val recordedAt = item.optLong("recordedAt", now)
                val eventTime = item.optLong("eventTime", recordedAt)
                val notebook = item.optLong("notebook", 0L).takeIf { it in known }

                val photos = item.optJSONArray("attachments")
                val names = buildList {
                    if (photos != null) {
                        for (p in 0 until photos.length()) {
                            val photo = photos.optJSONObject(p) ?: continue
                            val bytes = runCatching {
                                CryptoManager.decode(photo.getString("data"))
                            }.getOrNull() ?: continue
                            runCatching { attachments.save(key, bytes) }
                                .getOrNull()?.let { add(it) }
                        }
                    }
                }

                val encrypted = CryptoManager.encrypt(
                    key,
                    PayloadCodec.encode(
                        EntryPayload(
                            text = text,
                            eventTime = eventTime,
                            valence = valence,
                            customMood = custom,
                            notebookId = notebook ?: fallback,
                            locked = item.optBoolean("locked", false),
                            attachments = names,
                        )
                    ),
                )

                add(
                    Entry(
                        entryDate = date.toString(),
                        createdAt = recordedAt,
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

    private fun encodeNotebooks(set: NotebookSet): JSONArray {
        val array = JSONArray()
        set.notebooks.forEach { notebook ->
            val item = JSONObject()
            item.put("id", notebook.id)
            item.put("name", notebook.name)
            item.put("pinned", notebook.pinned)
            item.put("order", notebook.order)
            array.put(item)
        }
        return array
    }

    private fun mergeNotebooks(local: NotebookSet, incoming: JSONArray?): NotebookSet {
        if (incoming == null || incoming.length() == 0) return local
        val byId = local.notebooks.associateBy { it.id }.toMutableMap()
        for (i in 0 until incoming.length()) {
            val item = incoming.optJSONObject(i) ?: continue
            val id = item.optLong("id", 0L)
            val name = item.optString("name")
            if (id <= 0L || name.isBlank() || byId.containsKey(id)) continue
            byId[id] = Notebook(
                id = id,
                name = name,
                pinned = item.optBoolean("pinned", false),
                order = item.optInt("order", byId.size),
            )
        }
        return NotebookSet(byId.values.sortedBy { it.order })
    }
}
