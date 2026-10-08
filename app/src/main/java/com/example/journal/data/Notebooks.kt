package com.example.journal.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey

data class Notebook(
    val id: Long,
    val name: String,
    val pinned: Boolean,
    val order: Int,
)

data class NotebookSet(val notebooks: List<Notebook>) {

    /** Pinned first, then by order, then by name: the order the chips appear in. */
    val displayOrder: List<Notebook>
        get() = notebooks.sortedWith(
            compareByDescending<Notebook> { it.pinned }
                .thenBy { it.order }
                .thenBy { it.name.lowercase() }
        )

    fun byId(id: Long): Notebook? = notebooks.firstOrNull { it.id == id }

    fun nameOf(id: Long): String? = byId(id)?.name

    val firstId: Long? get() = displayOrder.firstOrNull()?.id

    companion object {
        const val DEFAULT_ID = 1L
        val DEFAULT = NotebookSet(
            listOf(Notebook(DEFAULT_ID, "Journal", pinned = true, order = 0))
        )
    }
}

/**
 * Notebook names are personal, so the whole set lives as one encrypted blob
 * rather than a plaintext table. There are only ever a handful, so decrypting
 * them all at unlock costs nothing.
 */
class NotebookStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("journal_notebooks", Context.MODE_PRIVATE)

    fun load(key: SecretKey): NotebookSet {
        val cipherText = prefs.getString(KEY_BLOB, null) ?: return NotebookSet.DEFAULT
        val iv = prefs.getString(KEY_IV, null) ?: return NotebookSet.DEFAULT
        val json = runCatching { CryptoManager.decrypt(key, cipherText, iv) }.getOrNull()
            ?: return NotebookSet.DEFAULT
        return decode(json)
    }

    fun save(key: SecretKey, set: NotebookSet) {
        val encrypted = CryptoManager.encrypt(key, encode(set))
        prefs.edit {
            putString(KEY_BLOB, encrypted.cipherText)
            putString(KEY_IV, encrypted.iv)
        }
    }

    private fun encode(set: NotebookSet): String {
        val array = JSONArray()
        set.notebooks.forEach { notebook ->
            val item = JSONObject()
            item.put("id", notebook.id)
            item.put("name", notebook.name)
            item.put("pinned", notebook.pinned)
            item.put("order", notebook.order)
            array.put(item)
        }
        return array.toString()
    }

    private fun decode(json: String): NotebookSet = runCatching {
        val array = JSONArray(json)
        val notebooks = (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val name = item.optString("name")
            if (name.isBlank()) return@mapNotNull null
            Notebook(
                id = item.optLong("id", 0L),
                name = name,
                pinned = item.optBoolean("pinned", false),
                order = item.optInt("order", 0),
            )
        }.filter { it.id > 0L }
        if (notebooks.isEmpty()) NotebookSet.DEFAULT else NotebookSet(notebooks)
    }.getOrDefault(NotebookSet.DEFAULT)

    private companion object {
        const val KEY_BLOB = "blob"
        const val KEY_IV = "iv"
    }
}
