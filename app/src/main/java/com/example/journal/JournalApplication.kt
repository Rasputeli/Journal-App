package com.example.journal

import android.app.Application
import com.example.journal.data.AuthManager
import com.example.journal.data.EntryRepository
import com.example.journal.data.JournalDatabase
import com.example.journal.data.SettingsStore
import com.example.journal.work.Notifications

class JournalApplication : Application() {

    val authManager: AuthManager by lazy { AuthManager(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val database: JournalDatabase by lazy { JournalDatabase.build(this) }
    val repository: EntryRepository by lazy {
        EntryRepository(database.entryDao(), authManager, database, this)
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
    }
}
