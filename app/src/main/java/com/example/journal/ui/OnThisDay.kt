package com.example.journal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.journal.data.JournalEntry
import com.example.journal.data.MoodScale
import com.example.journal.data.NotebookSet
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * One remembered day, not one remembered section. A day with five sections used to
 * produce five cards; it now produces a single quiet line.
 */
data class OnThisDayHit(
    val label: String,
    val date: LocalDate,
    val blocks: List<JournalEntry>,
    val valence: Int?,
    val notebookId: Long,
    val hasLocked: Boolean,
)

private val WHITESPACE = Regex("\\s+")

/** How many rows show before the section folds the rest away. */
private const val COLLAPSED_ROWS = 3

/** Looks back at the same day a day, week, month or year ago. One hit per day. */
fun computeOnThisDay(entries: List<JournalEntry>, today: LocalDate): List<OnThisDayHit> {
    if (entries.isEmpty()) return emptyList()

    val byDate = entries.groupBy { it.date }
    val lookBacks = listOf(
        today.minusDays(1) to "Yesterday",
        today.minusWeeks(1) to "A week ago",
        today.minusMonths(1) to "A month ago",
        today.minusMonths(3) to "3 months ago",
        today.minusMonths(6) to "6 months ago",
        today.minusYears(1) to "A year ago",
    )

    return lookBacks.mapNotNull { (date, label) ->
        val blocks = byDate[date] ?: return@mapNotNull null
        val ordered = blocks.sortedWith(compareBy({ it.eventTime }, { it.recordedAt }))
        val valences = ordered.filterNot { it.locked }.mapNotNull { it.valence }
        OnThisDayHit(
            label = label,
            date = date,
            blocks = ordered,
            valence = if (valences.isEmpty()) null
            else valences.average().roundToInt().coerceIn(MoodScale.MIN, MoodScale.MAX),
            notebookId = ordered.firstOrNull()?.notebookId ?: NotebookSet.DEFAULT_ID,
            hasLocked = ordered.any { it.locked },
        )
    }
}

/** Hidden sections never contribute text, so the strip cannot leak what you hid. */
private fun previewOf(hit: OnThisDayHit): String {
    val pieces = hit.blocks
        .filterNot { it.locked }
        .map { it.text.replace(WHITESPACE, " ").trim() }
        .filter { it.isNotEmpty() }
    if (pieces.isEmpty()) return if (hit.hasLocked) "Hidden" else ""
    return pieces.joinToString("   ·   ")
}

/**
 * Deliberately quiet: a heading that folds away, then at most three single-line rows.
 * It occupies about the height of one old card however much you wrote that day.
 */
@Composable
fun OnThisDaySection(
    hits: List<OnThisDayHit>,
    moodScale: MoodScale,
    onOpenDay: (LocalDate, Long) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (hits.isEmpty()) return

    val shown = if (expanded) hits else hits.take(COLLAPSED_ROWS)
    val hidden = hits.size - shown.size
    val foldable = hits.size > COLLAPSED_ROWS

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = foldable) { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "On this day",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (hidden > 0) {
                Text(
                    text = hidden.toString() + " more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (foldable && expanded) {
                Text(
                    text = "Less",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        shown.forEach { hit ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDay(hit.date, hit.notebookId) }
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val mood = hit.valence?.let { moodScale.emojiFor(it) }
                Text(
                    text = buildString {
                        if (mood != null) {
                            append(mood)
                            append("  ")
                        }
                        append(hit.date.shortLabel())
                        append("  ·  ")
                        append(hit.label)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = previewOf(hit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                )
            }
        }
    }
}
