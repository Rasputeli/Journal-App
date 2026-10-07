package com.example.journal.ui

import com.example.journal.data.JournalEntry
import java.time.LocalDate

data class OnThisDayHit(val label: String, val entry: JournalEntry)

/** Looks back at the same day a day, week, month or year ago. */
fun computeOnThisDay(entries: List<JournalEntry>, today: LocalDate): List<OnThisDayHit> {
    if (entries.isEmpty()) return emptyList()

    val byDate = entries.groupBy { it.date }
    val lookBacks = listOf(
        today.minusDays(1) to "Yesterday",
        today.minusWeeks(1) to "A week ago",
        today.minusMonths(1) to "A month ago",
        today.minusMonths(3) to "Three months ago",
        today.minusMonths(6) to "Six months ago",
        today.minusYears(1) to "A year ago",
    )

    return buildList {
        lookBacks.forEach { (date, label) ->
            byDate[date]?.forEach { add(OnThisDayHit(label, it)) }
        }
    }
}
