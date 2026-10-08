package com.example.journal.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.journal.UiMessage
import com.example.journal.data.JournalEntry
import com.example.journal.data.MoodScale
import com.example.journal.data.NotebookSet
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

private const val COLLAPSE_AFTER_DAYS = 7L
private const val ALL_NOTEBOOKS = -1L

data class DayGroup(val date: LocalDate, val blocks: List<JournalEntry>)

private fun buildDayGroups(entries: List<JournalEntry>): List<DayGroup> =
    entries.groupBy { it.date }
        .entries
        .sortedByDescending { it.key }
        .map { (date, blocks) ->
            DayGroup(
                date,
                blocks.sortedWith(compareBy({ it.eventTime }, { it.recordedAt })),
            )
        }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JournalListScreen(
    entries: List<JournalEntry>,
    notebooks: NotebookSet,
    stats: JournalStats,
    moodSeries: List<MoodPoint>,
    moodScale: MoodScale,
    onThisDayHits: List<OnThisDayHit>,
    revealedEntryIds: Set<Long>,
    revealRecordedTimes: Boolean,
    collapseOldDays: Boolean,
    expansion: Map<LocalDate, Boolean>,
    message: UiMessage?,
    transferInProgress: Boolean,
    onMessageShown: () -> Unit,
    onNewBlock: () -> Unit,
    onNewBlockForDate: (LocalDate) -> Unit,
    onOpenEntry: (JournalEntry) -> Unit,
    onRevealEntry: (Long, String, (Boolean) -> Unit) -> Unit,
    onToggleDay: (LocalDate, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri, String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var selectedNotebook by rememberSaveable { mutableStateOf(ALL_NOTEBOOKS) }
    var menuOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var dateWithNoEntries by remember { mutableStateOf<LocalDate?>(null) }
    var pendingLockedEntry by remember { mutableStateOf<JournalEntry?>(null) }

    fun requestOpen(entry: JournalEntry) {
        if (entry.locked && entry.id !in revealedEntryIds) {
            pendingLockedEntry = entry
        } else {
            onOpenEntry(entry)
        }
    }

    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current.text)
        onMessageShown()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importUri = uri }

    val filtered = remember(entries, query, selectedNotebook) {
        entries.asSequence()
            .filter { selectedNotebook == ALL_NOTEBOOKS || it.notebookId == selectedNotebook }
            .filter {
                query.isBlank() ||
                    it.text.contains(query, ignoreCase = true) ||
                    it.date.toString().contains(query) ||
                    it.date.shortLabel().contains(query, ignoreCase = true)
            }
            .toList()
    }
    val dayGroups = remember(filtered) { buildDayGroups(filtered) }
    val forceExpand = query.isNotBlank() || selectedNotebook != ALL_NOTEBOOKS
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
                            placeholder = { Text("Search blocks") },
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
                        IconButton(onClick = { pickerOpen = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Jump to a date")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        menuOpen = false
                                        onOpenSettings()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Lock now") },
                                    onClick = {
                                        menuOpen = false
                                        onLock()
                                    },
                                )
                            }
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
                text = { Text("New block") },
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
                                "No blocks match \"" + query + "\""
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
                                    revealedEntryIds = revealedEntryIds,
                                    onRequestOpen = { requestOpen(it) },
                                )
                            }
                        }

                        items(
                            dayGroups.size,
                            key = { index -> dayGroups[index].date.toString() },
                        ) { index ->
                            val group = dayGroups[index]
                            val auto = !collapseOldDays ||
                                group.date == today ||
                                group.date.isAfter(today.minusDays(COLLAPSE_AFTER_DAYS))
                            val expanded = expansion[group.date] ?: auto

                            DayCard(
                                group = group,
                                expanded = expanded || forceExpand,
                                interactive = !forceExpand,
                                scale = moodScale,
                                revealRecordedTimes = revealRecordedTimes,
                                query = query,
                                revealedEntryIds = revealedEntryIds,
                                showNotebook = selectedNotebook == ALL_NOTEBOOKS,
                                notebookName = { notebooks.nameOf(it) },
                                onToggle = {
                                    onToggleDay(group.date, expanded)
                                },
                                onOpen = { requestOpen(it) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.now().toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerOpen = false
                    val picked = pickerState.selectedDateMillis?.utcToLocalDate()
                        ?: return@TextButton
                    val index = dayGroups.indexOfFirst { it.date == picked }
                    if (index >= 0) {
                        val target = index + if (overviewShown) 1 else 0
                        scope.launch { listState.animateScrollToItem(target) }
                    } else {
                        dateWithNoEntries = picked
                    }
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    dateWithNoEntries?.let { date ->
        AlertDialog(
            onDismissRequest = { dateWithNoEntries = null },
            title = { Text("Nothing written yet") },
            text = {
                Text(
                    "There are no blocks for " + date.shortLabel() + ". Would you " +
                        "like to write one for that day?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dateWithNoEntries = null
                    onNewBlockForDate(date)
                }) { Text("Write it") }
            },
            dismissButton = {
                TextButton(onClick = { dateWithNoEntries = null }) { Text("Cancel") }
            },
        )
    }

    pendingLockedEntry?.let { entry ->
        UnlockEntryDialog(
            onDismiss = { pendingLockedEntry = null },
            onConfirm = { password, done ->
                onRevealEntry(entry.id, password) { ok ->
                    done(ok)
                    if (ok) {
                        pendingLockedEntry = null
                        onOpenEntry(entry)
                    }
                }
            },
        )
    }

    importUri?.let { uri ->
        ImportPasswordDialog(
            onDismiss = { importUri = null },
            onConfirm = { password ->
                importUri = null
                onImport(uri, password)
            },
        )
    }
}

@Composable
private fun OverviewCard(
    stats: JournalStats,
    moodSeries: List<MoodPoint>,
    moodScale: MoodScale,
    hits: List<OnThisDayHit>,
    revealedEntryIds: Set<Long>,
    onRequestOpen: (JournalEntry) -> Unit,
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
                    StatBlock(stats.blocks.toString(), "blocks")
                    StatBlock(stats.words.toString(), "words")
                }

                if (moodSeries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    val average = moodSeries.map { it.average }.average()
                    val rounded = average.roundToInt().coerceIn(MoodScale.MIN, MoodScale.MAX)
                    val emoji = moodScale.emojiFor(rounded)
                    Text(
                        text = "Last 30 days · " + (emoji ?: "") + " " +
                            "%+.1f".format(average) + " average across " +
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
                val hidden = hit.entry.locked && hit.entry.id !in revealedEntryIds
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable { onRequestOpen(hit.entry) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = hit.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        if (hidden) {
                            Text(
                                text = "Hidden block — tap to unlock",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = hit.entry.text,
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
                text = "Tap New block to write your first one. It is stamped with " +
                    "today's date and the current time.",
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
    expanded: Boolean,
    interactive: Boolean,
    scale: MoodScale,
    revealRecordedTimes: Boolean,
    query: String,
    revealedEntryIds: Set<Long>,
    showNotebook: Boolean,
    notebookName: (Long) -> String?,
    onToggle: () -> Unit,
    onOpen: (JournalEntry) -> Unit,
) {
    val visible = group.blocks.filterNot { it.locked && it.id !in revealedEntryIds }
    val words = visible.sumOf { countWords(it.text) }
    val valences = visible.mapNotNull { it.valence }
    val mood = if (valences.isEmpty()) null
    else scale.emojiFor(valences.average().roundToInt().coerceIn(MoodScale.MIN, MoodScale.MAX))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = interactive, onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (interactive) {
                    Text(
                        text = if (expanded) "\u25BE" else "\u25B8",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(0.dp))
                }
                Text(
                    text = "  " + group.date.prettyLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (mood != null) {
                    Text(text = mood, style = MaterialTheme.typography.titleSmall)
                }
            }

            if (visible.isNotEmpty()) {
                Text(
                    text = "  " + visible.size +
                        (if (visible.size == 1) " block · " else " blocks · ") +
                        words + (if (words == 1) " word" else " words"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
            }

            if (expanded) {
                visible.forEach { entry ->
                    BlockSection(
                        entry = entry,
                        scale = scale,
                        revealRecordedTimes = revealRecordedTimes,
                        query = query,
                        showNotebook = showNotebook,
                        notebookLabel = notebookName(entry.notebookId),
                        onClick = { onOpen(entry) },
                    )
                }
                group.blocks
                    .filter { it.locked && it.id !in revealedEntryIds }
                    .forEach { entry ->
                        HiddenSection(entry = entry, onClick = { onOpen(entry) })
                    }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun BlockSection(
    entry: JournalEntry,
    scale: MoodScale,
    revealRecordedTimes: Boolean,
    query: String,
    showNotebook: Boolean,
    notebookLabel: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.eventTime.timeLabel(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            val mood = entry.valence?.let { scale.emojiFor(it) } ?: entry.customMood
            if (mood != null) {
                Text(text = "  " + mood, style = MaterialTheme.typography.labelMedium)
            }
            if (showNotebook && notebookLabel != null) {
                Text(
                    text = "  ·  " + notebookLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = TextRender.render(
                source = entry.text,
                highlight = query,
                highlightStyle = SpanStyle(
                    background = MaterialTheme.colorScheme.primaryContainer,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                ),
            ),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 12,
            overflow = TextOverflow.Ellipsis,
        )

        if (entry.tags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.tags.joinToString(" ") { "#" + it },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (entry.attachments.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.attachments.size.toString() +
                    (if (entry.attachments.size == 1) " photo" else " photos"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (revealRecordedTimes) {
            Spacer(Modifier.height(4.dp))
            val edited = entry.updatedAt - entry.recordedAt > 60_000L
            Text(
                text = buildString {
                    append("Recorded ")
                    append(entry.recordedAt.stampLabel())
                    if (edited) {
                        append(" · edited ")
                        append(entry.updatedAt.stampLabel())
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HiddenSection(entry: JournalEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.eventTime.timeLabel(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp).height(14.dp),
        )
        Text(
            text = "  Hidden block — tap to unlock",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
