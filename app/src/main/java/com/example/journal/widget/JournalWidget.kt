package com.example.journal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.journal.MainActivity
import com.example.journal.R
import com.example.journal.ui.JournalStats

/**
 * The widget runs in the launcher's process, which never has the journal
 * key, so it only ever shows harmless totals (streak, entry count, words)
 * cached in SharedPreferences. No entry text reaches it.
 */
object JournalWidget {

    private const val PREFS = "journal_widget"
    private const val KEY_STREAK = "streak"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_WORDS = "words"

    fun push(context: Context, stats: JournalStats) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_STREAK, stats.currentStreak)
            .putInt(KEY_ENTRIES, stats.entries)
            .putInt(KEY_WORDS, stats.words)
            .apply()
        refreshAll(context)
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(
            ComponentName(context, JournalWidgetProvider::class.java)
        )
        ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
    }

    fun buildViews(context: Context): RemoteViews {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val streak = prefs.getInt(KEY_STREAK, 0)
        val entries = prefs.getInt(KEY_ENTRIES, 0)
        val words = prefs.getInt(KEY_WORDS, 0)

        val views = RemoteViews(context.packageName, R.layout.widget_journal)
        views.setTextViewText(
            R.id.widget_stats,
            if (entries == 0) context.getString(R.string.widget_empty)
            else context.getString(R.string.widget_stats, streak, entries, words),
        )

        views.setOnClickPendingIntent(R.id.widget_action, writeNow(context))
        views.setOnClickPendingIntent(R.id.widget_stats, openList(context))
        views.setOnClickPendingIntent(R.id.widget_title, openList(context))
        return views
    }

    private fun writeNow(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_NEW_ENTRY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openList(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
