package com.example.journal.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything encrypted for one entry. Mood, the hidden flag and the list of
 * encrypted photo filenames all live in here, so none of them is plaintext
 * on disk.
 */
data class EntryPayload(
    val text: String,
    val mood: String? = null,
    val locked: Boolean = false,
    val attachments: List<String> = emptyList(),
)

object PayloadCodec {

    fun encode(payload: EntryPayload): String = JSONObject().apply {
        put("v", 3)
        put("text", payload.text)
        payload.mood?.let { put("mood", it) }
        if (payload.locked) put("locked", true)
        if (payload.attachments.isNotEmpty()) {
            put("attachments", JSONArray(payload.attachments))
        }
    }.toString()

    /** Tolerates rows written before this format existed (raw text). */
    fun decode(raw: String): EntryPayload {
        if (!raw.trimStart().startsWith("{")) return EntryPayload(raw)
        return try {
            val json = JSONObject(raw)
            if (!json.has("text")) return EntryPayload(raw)
            val attachments = json.optJSONArray("attachments")?.let { array ->
                (0 until array.length())
                    .mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()
            EntryPayload(
                text = json.optString("text"),
                mood = json.optString("mood").takeIf { it.isNotBlank() && !json.isNull("mood") },
                locked = json.optBoolean("locked", false),
                attachments = attachments,
            )
        } catch (t: Throwable) {
            EntryPayload(raw)
        }
    }
}
