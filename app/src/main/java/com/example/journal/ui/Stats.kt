package com.example.journal.ui

import com.example.journal.data.JournalEntry
import java.time.LocalDate

data class JournalStats(
    val blocks: Int = 0,
    val daysWritten: Int = 0,
    val words: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
)

data class MoodPoint(val date: LocalDate, val average: Double)

private val whitespace = Regex("\\s+")

fun countWords(text: String): Int =
    text.split(whitespace).count { word -> word.any { it.isLetterOrDigit() } }

fun computeStats(
    entries: List<JournalEntry>,
    today: LocalDate = LocalDate.now(),
): JournalStats {
    if (entries.isEmpty()) return JournalStats()

    val dates = entries.map { it.date }.distinct()
    val descending = dates.sortedDescending()
    val ascending = dates.sorted()

    // A streak may start today or yesterday, so writing at 1am doesn't break it.
    var current = 0
    val latest = descending.first()
    if (latest == today || latest == today.minusDays(1)) {
        var expected = latest
        for (date in descending) {
            if (date == expected) {
                current++
                expected = expected.minusDays(1)
            } else if (date.isBefore(expected)) {
                break
            }
        }
    }

    var longest = 0
    var run = 0
    var previousDate: LocalDate? = null
    for (date in ascending) {
        val previous = previousDate
        run = if (previous != null && previous.plusDays(1) == date) run + 1 else 1
        if (run > longest) longest = run
        previousDate = date
    }

    return JournalStats(
        blocks = entries.size,
        daysWritten = dates.size,
        words = entries.sumOf { countWords(it.text) },
        currentStreak = current,
        longestStreak = maxOf(longest, current),
    )
}

/**
 * Daily average valence over the last [days] days. Locked blocks are excluded
 * so a hidden entry never shows up as a mood on the chart.
 */
fun computeMoodSeries(
    entries: List<JournalEntry>,
    days: Int = 30,
    today: LocalDate = LocalDate.now(),
): List<MoodPoint> {
    if (entries.isEmpty()) return emptyList()
    val from = today.minusDays((days - 1).toLong())
    return entries
        .filter {
            !it.locked && it.valence != null &&
                !it.date.isBefore(from) && !it.date.isAfter(today)
        }
        .groupBy { it.date }
        .map { (date, dayBlocks) ->
            MoodPoint(date, dayBlocks.mapNotNull { it.valence }.average())
        }
        .sortedBy { it.date }
}
