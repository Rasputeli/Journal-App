package com.example.journal.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM entries ORDER BY entry_date DESC, created_at ASC")
    fun observeAll(): Flow<List<Entry>>

    @Query("SELECT * FROM entries ORDER BY entry_date DESC, created_at ASC")
    suspend fun getAll(): List<Entry>

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Entry?

    @Insert
    suspend fun insert(entry: Entry): Long

    @Insert
    suspend fun insertAll(entries: List<Entry>): List<Long>

    @Update
    suspend fun update(entry: Entry)

    @Update
    suspend fun updateAll(entries: List<Entry>)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
