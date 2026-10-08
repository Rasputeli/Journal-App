package com.example.journal.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class MoodLevel(val emoji: String, val label: String)

/**
 * Five fixed levels from -2 to +2. Only the emoji and the label are yours to
 * change. Entries store the number, so re-skinning a level instantly restyles
 * every past entry and keeps trend maths valid.
 */
data class MoodScale(val levels: List<MoodLevel>) {

    fun emojiFor(valence: Int): String? = levels.getOrNull(valence + OFFSET)?.emoji

    fun labelFor(valence: Int): String? = levels.getOrNull(valence + OFFSET)?.label

    fun withEmoji(index: Int, emoji: String): MoodScale = copy(
        levels = levels.mapIndexed { i, level ->
            if (i == index) level.copy(emoji = emoji) else level
        }
    )

    fun withLabel(index: Int, label: String): MoodScale = copy(
        levels = levels.mapIndexed { i, level ->
            if (i == index) level.copy(label = label) else level
        }
    )

    companion object {
        const val OFFSET = 2
        const val MIN = -2
        const val MAX = 2

        val DEFAULT = MoodScale(
            listOf(
                MoodLevel("\uD83D\uDE23", "Bad"),
                MoodLevel("\uD83D\uDE14", "Low"),
                MoodLevel("\uD83D\uDE10", "Neutral"),
                MoodLevel("\uD83D\uDE42", "Good"),
                MoodLevel("\uD83D\uDE04", "Great"),
            )
        )

        /** Emoji used before moods carried a number. Mapped once, at read time. */
        private val LEGACY = mapOf(
            "\uD83D\uDE04" to 2, "\uD83E\uDD29" to 2,
            "\uD83D\uDE42" to 1,
            "\uD83D\uDE10" to 0, "\uD83D\uDE34" to 0, "\uD83E\uDD14" to 0,
            "\uD83D\uDE14" to -1, "\uD83E\uDD72" to -1,
            "\uD83D\uDE23" to -2, "\uD83D\uDE20" to -2,
        )

        fun legacyValence(emoji: String): Int? = LEGACY[emoji]
    }
}

class MoodScaleStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("journal_mood_scale", Context.MODE_PRIVATE)

    fun load(): MoodScale {
        val raw = prefs.getString(KEY, null) ?: return MoodScale.DEFAULT
        return runCatching {
            val array = JSONArray(raw)
            val levels = (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val emoji = item.optString("e")
                if (emoji.isBlank()) return@mapNotNull null
                MoodLevel(emoji, item.optString("l"))
            }
            if (levels.size == 5) MoodScale(levels) else MoodScale.DEFAULT
        }.getOrDefault(MoodScale.DEFAULT)
    }

    fun save(scale: MoodScale) {
        val array = JSONArray()
        scale.levels.forEach { level ->
            val item = JSONObject()
            item.put("e", level.emoji)
            item.put("l", level.label)
            array.put(item)
        }
        prefs.edit { putString(KEY, array.toString()) }
    }

    fun reset() {
        prefs.edit { remove(KEY) }
    }

    private companion object {
        const val KEY = "scale"
    }
}
