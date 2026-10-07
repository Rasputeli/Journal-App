package com.example.journal.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy")
private val stamp = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

/** Material3's DatePicker speaks in UTC midnight millis. */
fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.utcToLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

fun LocalDate.prettyLabel(): String {
    val today = LocalDate.now()
    val base = format(longDate)
    return when (this) {
        today -> "Today · $base"
        today.minusDays(1) -> "Yesterday · $base"
        else -> base
    }
}

fun LocalDate.shortLabel(): String = format(shortDate)

fun Long.stampLabel(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(stamp)
