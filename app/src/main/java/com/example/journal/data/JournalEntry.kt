package com.example.journal.data

import java.time.LocalDate

/** Decrypted, in-memory representation of an entry. */
data class JournalEntry(
    val id: Long,
    val date: LocalDate,
    val createdAt: Long,
    val updatedAt: Long,
    val text: String,
    val mood: String? = null,
    val locked: Boolean = false,
    val attachments: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)
