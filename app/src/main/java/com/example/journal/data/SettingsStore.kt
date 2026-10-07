package com.example.journal.data

import android.content.Context
import androidx.core.content.edit

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("journal_settings", Context.MODE_PRIVATE)

    /** Whether the "Recorded <timestamp>" metadata is visible in the list. */
    var revealRecordedTimes: Boolean
        get() = prefs.getBoolean(KEY_REVEAL, false)
        set(value) = prefs.edit { putBoolean(KEY_REVEAL, value) }

    var reminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_REMINDER_ENABLED, value) }

    var reminderHour: Int
        get() = prefs.getInt(KEY_REMINDER_HOUR, 20)
        set(value) = prefs.edit { putInt(KEY_REMINDER_HOUR, value) }

    var reminderMinute: Int
        get() = prefs.getInt(KEY_REMINDER_MINUTE, 30)
        set(value) = prefs.edit { putInt(KEY_REMINDER_MINUTE, value) }

    private companion object {
        const val KEY_REVEAL = "reveal_recorded_times"
        const val KEY_REMINDER_ENABLED = "reminder_enabled"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REMINDER_MINUTE = "reminder_minute"
    }
}
