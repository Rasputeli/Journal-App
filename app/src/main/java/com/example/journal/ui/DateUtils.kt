package com.example.journal.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy")
private val stamp = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
private val clock = DateTimeFormatter.ofPattern("HH:mm")

fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.utcToLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

fun LocalDate.prettyLabel(): String {
    val today = LocalDate.now()
    val base = format(longDate)
    return when (this) {
        today -> "Today · " + base
        today.minusDays(1) -> "Yesterday · " + base
        else -> base
    }
}

fun LocalDate.shortLabel(): String = format(shortDate)

fun Long.stampLabel(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(stamp)

fun Long.timeLabel(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(clock)

fun Long.hourOfDay(): Int =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).hour

fun Long.minuteOfHour(): Int =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).minute

/** Combines a day with a time of day into the instant a block stores. */
fun LocalDate.atTimeOfDay(hour: Int, minute: Int): Long =
    atTime(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
