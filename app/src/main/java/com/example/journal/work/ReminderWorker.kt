package com.example.journal.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.journal.data.SettingsStore

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        if (settings.reminderEnabled) {
            Notifications.showReminder(applicationContext)
        }
        return Result.success()
    }
}
