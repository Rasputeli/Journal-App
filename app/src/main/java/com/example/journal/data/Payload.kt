package com.example.journal.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything encrypted for one block: the text, the time it claims, its mood,
 * its notebook, the hidden flag and its photo names. None of it is plaintext.
 */
data class EntryPayload(
    val text: String,
    val eventTime: Long? = null,
    val valence: Int? = null,
    val customMood: String? = null,
    val notebookId: Long? = null,
    val locked: Boolean = false,
    val attachments: List<String> = emptyList(),
)

object PayloadCodec {

    fun encode(payload: EntryPayload): String {
        val json = JSONObject()
        json.put("v", 4)
        json.put("text", payload.text)
        payload.eventTime?.let { json.put("eventTime", it) }
        payload.valence?.let { json.put("valence", it) }
        payload.customMood?.let { json.put("customMood", it) }
        payload.notebookId?.let { json.put("notebook", it) }
        if (payload.locked) json.put("locked", true)
        if (payload.attachments.isNotEmpty()) {
            json.put("attachments", JSONArray(payload.attachments))
        }
        return json.toString()
    }

    /**
     * Reads every version we have ever written. Blocks from before moods
     * carried a number still hold an emoji under "mood"; those are translated
     * to a valence here rather than in a migration pass.
     */
    fun decode(raw: String): EntryPayload {
        if (!raw.trimStart().startsWith("{")) return EntryPayload(raw)
        return try {
            val json = JSONObject(raw)
            if (!json.has("text")) return EntryPayload(raw)

            val attachments = json.optJSONArray("attachments")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    val name = array.optString(i)
                    if (name.isBlank()) null else name
                }
            } ?: emptyList()

            var valence: Int? = null
            var customMood: String? = null

            if (json.has("valence") && !json.isNull("valence")) {
                valence = json.optInt("valence").coerceIn(MoodScale.MIN, MoodScale.MAX)
            }

            val legacy = json.optString("mood")
            if (valence == null && legacy.isNotBlank() && !json.isNull("mood")) {
                val mapped = MoodScale.legacyValence(legacy)
                if (mapped != null) valence = mapped else customMood = legacy
            }

            val explicitCustom = json.optString("customMood")
            if (explicitCustom.isNotBlank() && !json.isNull("customMood")) {
                customMood = explicitCustom
            }

            var eventTime: Long? = null
            if (json.has("eventTime") && !json.isNull("eventTime")) {
                val value = json.optLong("eventTime", 0L)
                if (value > 0L) eventTime = value
            }

            var notebookId: Long? = null
            if (json.has("notebook") && !json.isNull("notebook")) {
                val value = json.optLong("notebook", 0L)
                if (value > 0L) notebookId = value
            }

            EntryPayload(
                text = json.optString("text"),
                eventTime = eventTime,
                valence = valence,
                customMood = customMood,
                notebookId = notebookId,
                locked = json.optBoolean("locked", false),
                attachments = attachments,
            )
        } catch (t: Throwable) {
            EntryPayload(raw)
        }
    }
}
