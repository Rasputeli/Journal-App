package com.example.journal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.journal.UiMessage
import com.example.journal.data.JournalEntry
import com.example.journal.data.MoodScale
import com.example.journal.data.NotebookSet
import java.time.LocalDate
import kotlin.math.roundToInt

private const val COLLAPSE_AFTER_DAYS = 7L
private const val ALL_NOTEBOOKS = -1L

data class DayGroup(
    val date: LocalDate,
    val blocks: List<JournalEntry>,
    val notebookIds: List<Long>,
)

private fun buildDayGroups(entries: List<JournalEntry>): List<DayGroup> =
    entries.groupBy { it.date }
        .entries
        .sortedByDescending { it.key }
        .map { (date, blocks) ->
            DayGroup(
                date = date,
                blocks = blocks.sortedWith(compareBy({ it.eventTime }, { it.recordedAt })),
                notebookIds = blocks.map { it.notebookId }.distinct(),
            )
        }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalListScreen(
    entries: List<JournalEntry>,
    notebooks: NotebookSet,
    stats: JournalStats,
    moodSeries: List<MoodPoint>,
    moodScale: MoodScale,
    onThisDayHits: List<OnThisDayHit>,
    revealRecordedTimes: Boolean,
    collapseOldDays: Boolean,
    message: UiMessage?,
    transferInProgress: Boolean,
    onMessageShown: () -> Unit,
    onNewBlock: () -> Unit,
    onOpenDay: (LocalDate, Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var selectedNotebook by rememberSaveable { mutableStateOf(ALL_NOTEBOOKS) }

    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current.text)
        onMessageShown()
    }

    val filtered = remember(entries, query, selectedNotebook) {
        entries.asSequence()
            .filter {
                selectedNotebook == ALL_NOTEBOOKS || it.notebookId == selectedNotebook
            }
            .filter {
                query.isBlank() ||
                    it.text.contains(query, ignoreCase = true) ||
                    it.date.toString().contains(query) ||
                    it.date.shortLabel().contains(query, ignoreCase = true)
            }
            .toList()
    }
    val dayGroups = remember(filtered) { buildDayGroups(filtered) }
    val today = LocalDate.now()
    val overviewShown = query.isBlank() && selectedNotebook == ALL_NOTEBOOKS

    Scaffold(
        topBar = {
            if (searchOpen) {
                TopAppBar(
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search your journal") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            searchOpen = false
                            query = ""
                        }) { Icon(Icons.Default.Close, contentDescription = "Close search") }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Journal") },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Edit, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewBlock,
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text("Write") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = selectedNotebook == ALL_NOTEBOOKS,
                        onClick = { selectedNotebook = ALL_NOTEBOOKS },
                        label = { Text("All") },
                    )
                }
                items(notebooks.displayOrder.size) { index ->
                    val notebook = notebooks.displayOrder[index]
                    FilterChip(
                        selected = selectedNotebook == notebook.id,
                        onClick = {
                            selectedNotebook =
                                if (selectedNotebook == notebook.id) ALL_NOTEBOOKS
                                else notebook.id
                        },
                        label = { Text(notebook.name) },
                    )
                }
            }

            if (transferInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    entries.isEmpty() -> EmptyState()

                    dayGroups.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (query.isNotBlank())
                                "Nothing matches \"" + query + "\""
                            else "Nothing in this notebook yet",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        if (overviewShown) {
                            item(key = "overview") {
                                OverviewCard(
                                    stats = stats,
                                    moodSeries = moodSeries,
                                    moodScale = moodScale,
                                    hits = onThisDayHits,
                                    onOpenDay = onOpenDay,
                                )
                            }
                        }

                        items(dayGroups.size) { index ->
                            val group = dayGroups[index]
                            DayCard(
                                group = group,
                                scale = moodScale,
                                notebooks = notebooks,
                                query = query,
                                collapsed = collapseOldDays &&
                                    group.date != today &&
                                    group.date.isBefore(
                                        today.minusDays(COLLAPSE_AFTER_DAYS)
                                    ),
                                showNotebooks = selectedNotebook == ALL_NOTEBOOKS,
                                onOpen = {
                                    val notebookId = group.notebookIds.firstOrNull()
                                        ?: notebooks.firstId
                                        ?: NotebookSet.DEFAULT_ID
                                    onOpenDay(group.date, notebookId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(
    stats: JournalStats,
    moodSeries: List<MoodPoint>,
    moodScale: MoodScale,
    hits: List<OnThisDayHit>,
    onOpenDay: (LocalDate, Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatBlock(stats.currentStreak.toString(), "day streak")
                    StatBlock(stats.longestStreak.toString(), "best")
                    StatBlock(stats.blocks.toString(), "sections")
                    StatBlock(stats.words.toString(), "words")
                }

                if (moodSeries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    val average = moodSeries.map { it.average }.average()
                    val rounded = average.roundToInt()
                        .coerceIn(MoodScale.MIN, MoodScale.MAX)
                    val emoji = moodScale.emojiFor(rounded) ?: ""
                    Text(
                        text = "Last 30 days · " + emoji + " " +
                            "%+.1f".format(average) + " across " +
                            moodSeries.size + " " +
                            (if (moodSeries.size == 1) "day" else "days"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (hits.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "On this day",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            hits.forEach { hit ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable {
                            onOpenDay(hit.entry.date, hit.entry.notebookId)
                        },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = hit.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (hit.entry.locked) "Hidden section"
                            else hit.entry.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap Write to start today's page. It is stamped with the " +
                    "current time, and every time you come back later a new " +
                    "timestamped section is added below.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayCard(
    group: DayGroup,
    scale: MoodScale,
    notebooks: NotebookSet,
    query: String,
    collapsed: Boolean,
    showNotebooks: Boolean,
    onOpen: () -> Unit,
) {
    val words = group.blocks.sumOf { countWords(it.text) }
    val valences = group.blocks.filterNot { it.locked }.mapNotNull { it.valence }
    val mood = if (valences.isEmpty()) null
    else scale.emojiFor(
        valences.average().roundToInt().coerceIn(MoodScale.MIN, MoodScale.MAX)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.date.prettyLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (mood != null) {
                    Text(text = mood, fontSize = 18.sp)
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = group.blocks.size.toString() +
                    (if (group.blocks.size == 1) " section · " else " sections · ") +
                    words + (if (words == 1) " word" else " words"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (showNotebooks && group.notebookIds.size > 1) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = group.notebookIds
                        .mapNotNull { notebooks.nameOf(it) }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (!collapsed) {
                val preview = group.blocks.firstOrNull { !it.locked }
                if (preview != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = TextRender.render(
                            source = preview.text,
                            highlight = query,
                            highlightStyle = SpanStyle(
                                background = MaterialTheme.colorScheme.primaryContainer,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
