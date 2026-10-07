package com.example.journal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Entry::class], version = 1, exportSchema = false)
abstract class JournalDatabase : RoomDatabase() {

    abstract fun entryDao(): EntryDao

    companion object {
        fun build(context: Context): JournalDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                JournalDatabase::class.java,
                "journal.db",
            ).build()
    }
}
