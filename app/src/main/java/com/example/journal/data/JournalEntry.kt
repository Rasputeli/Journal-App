package com.example.journal.data

import java.time.LocalDate

/** Decrypted, in-memory view of one block inside a day. */
data class JournalEntry(
    val id: Long,
    val date: LocalDate,
    val eventTime: Long,
    val recordedAt: Long,
    val updatedAt: Long,
    val text: String,
    val valence: Int? = null,
    val customMood: String? = null,
    val notebookId: Long = NotebookSet.DEFAULT_ID,
    val locked: Boolean = false,
    val attachments: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)
