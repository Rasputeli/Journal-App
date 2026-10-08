package com.example.journal.data

import android.content.Context
import androidx.core.content.edit

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("journal_settings", Context.MODE_PRIVATE)

    var revealRecordedTimes: Boolean
        get() = prefs.getBoolean(KEY_REVEAL, false)
        set(value) = prefs.edit { putBoolean(KEY_REVEAL, value) }

    var collapseOldDays: Boolean
        get() = prefs.getBoolean(KEY_COLLAPSE, true)
        set(value) = prefs.edit { putBoolean(KEY_COLLAPSE, value) }

    /** Zero means the app never locks itself while it stays open. */
    var autoLockMillis: Long
        get() = prefs.getLong(KEY_AUTO_LOCK, 2 * 60 * 1000L)
        set(value) = prefs.edit { putLong(KEY_AUTO_LOCK, value) }

    var placeholder: String
        get() = prefs.getString(KEY_PLACEHOLDER, "") ?: ""
        set(value) = prefs.edit { putString(KEY_PLACEHOLDER, value) }

    var defaultNotebookId: Long
        get() = prefs.getLong(KEY_DEFAULT_NOTEBOOK, NotebookSet.DEFAULT_ID)
        set(value) = prefs.edit { putLong(KEY_DEFAULT_NOTEBOOK, value) }

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
        const val KEY_COLLAPSE = "collapse_old_days"
        const val KEY_AUTO_LOCK = "auto_lock_millis"
        const val KEY_PLACEHOLDER = "placeholder"
        const val KEY_DEFAULT_NOTEBOOK = "default_notebook"
        const val KEY_REMINDER_ENABLED = "reminder_enabled"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REMINDER_MINUTE = "reminder_minute"
    }
}
