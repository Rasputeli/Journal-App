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

fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

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

fun LocalDate.atTimeOfDay(hour: Int, minute: Int): Long =
    atTime(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

/** The moment "now" on a given day, used when backfilling a block. */
fun LocalDate.withCurrentTimeOfDay(): Long {
    val now = LocalTime.now()
    return atTimeOfDay(now.hour, now.minute)
}

fun minutesToLabel(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)
