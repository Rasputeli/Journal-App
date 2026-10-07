package com.example.journal.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Local calendar day the entry belongs to, ISO-8601 (yyyy-MM-dd). */
    @ColumnInfo(name = "entry_date")
    val entryDate: String,

    /** Wall-clock moment the text was first written. Hidden by default. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** Last edit time. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    /** AES-256-GCM ciphertext, Base64 (NO_WRAP). */
    @ColumnInfo(name = "cipher_text")
    val cipherText: String,

    /** 12-byte GCM nonce, Base64 (NO_WRAP). */
    @ColumnInfo(name = "iv")
    val iv: String,
)
