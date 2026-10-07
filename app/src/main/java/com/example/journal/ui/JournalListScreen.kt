package com.example.journal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.journal.ReminderSettings
import com.example.journal.UiMessage
import com.example.journal.data.JournalEntry
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed interface ListItem {
    data class DateHeader(val date: LocalDate, val count: Int) : ListItem
    data class EntryRow(val entry: JournalEntry) : ListItem
}

private fun buildListItems(entries: List<JournalEntry>): List<ListItem> {
    val items = mutableListOf<ListItem>()
    var currentDate: LocalDate? = null
    var bucket = mutableListOf<JournalEntry>()

    fun flush() {
        val date = currentDate ?: return
        items += ListItem.DateHeader(date, bucket.size)
        bucket.forEach { items += ListItem.EntryRow(it) }
        bucket = mutableListOf()
    }

    entries.forEach { entry ->
        if (entry.date != currentDate) {
            flush()
            currentDate = entry.date
        }
        bucket += entry
    }
    flush()
    return items
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JournalListScreen(
    entries: List<JournalEntry>,
    stats: JournalStats,
    onThisDayHits: List<OnThisDayHit>,
    revealedEntryIds: Set<Long>,
    revealRecordedTimes: Boolean,
    reminder: ReminderSettings,
    transferInProgress: Boolean,
    message: UiMessage?,
    onMessageShown: () -> Unit,
    onToggleRevealRecordedTimes: () -> Unit,
    onNewEntry: () -> Unit,
    onNewEntryForDate: (LocalDate) -> Unit,
    onOpenEntry: (JournalEntry) -> Unit,
    onRevealEntry: (Long, String, (Boolean) -> Unit) -> Unit,
    onLock: () -> Unit,
    onChangePassword: (String, String, (String?) -> Unit) -> Unit,
    onSaveReminder: (Boolean, Int, Int) -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri, String) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var activeTag by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var reminderOpen by remember { mutableStateOf(false) }
    var changePasswordOpen by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var notificationsBlockedDialog by remember { mutableStateOf(false) }
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) notificationsBlockedDialog = true }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExport) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importUri = uri }

    val allTags = remember(entries) {
        entries.flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    val filtered = remember(entries, query, activeTag) {
        entries.asSequence()
            .filter { activeTag == null || activeTag in it.tags }
            .filter {
                query.isBlank() ||
                        it.text.contains(query, ignoreCase = true) ||
                        it.date.toString().contains(query) ||
                        it.date.shortLabel().contains(query, ignoreCase = true)
            }
            .toList()
    }
    val listItems = remember(filtered) { buildListItems(filtered) }

    Scaffold(
        topBar = {
            if (searchOpen) {
                TopAppBar(
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search entries") },
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
                                    text = {
                                        Text(
                                            if (revealRecordedTimes) "Hide recorded times"
                                            else "Show recorded times"
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onToggleRevealRecordedTimes()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (reminder.enabled)
                                                "Daily reminder · %02d:%02d".format(reminder.hour, reminder.minute)
                                            else "Daily reminder"
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        reminderOpen = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export journal") },
                                    enabled = !transferInProgress,
                                    onClick = {
                                        menuOpen = false
                                        exportLauncher.launch("journal-backup-${LocalDate.now()}.json")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Import journal") },
                                    enabled = !transferInProgress,
                                    onClick = {
                                        menuOpen = false
                                        importLauncher.launch(arrayOf("*/*"))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Change password") },
                                    onClick = {
                                        menuOpen = false
                                        changePasswordOpen = true
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
                onClick = onNewEntry,
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text("New entry") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (allTags.isNotEmpty()) {
                TagFilterRow(
                    tags = allTags,
                    activeTag = activeTag,
                    onSelect = { activeTag = it },
                )
            }

            if (transferInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    entries.isEmpty() -> EmptyState()

                    listItems.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                activeTag != null && query.isNotBlank() ->
                                    "Nothing tagged #$activeTag matches \"$query\""
                                activeTag != null -> "Nothing tagged #$activeTag yet"
                                else -> "No entries match \"$query\""
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        if (query.isBlank() && activeTag == null) {
                            item(key = "overview") {
                                OverviewCard(
                                    stats = stats,
                                    hits = onThisDayHits,
                                    revealedEntryIds = revealedEntryIds,
                                    onRequestOpen = ::requestOpen,
                                )
                            }
                        }

                        listItems.forEach { listItem ->
                            when (listItem) {
                                is ListItem.DateHeader -> stickyHeader(
                                    key = "header-${listItem.date}"
                                ) {
                                    DateHeaderRow(listItem.date, listItem.count)
                                }

                                is ListItem.EntryRow -> item(
                                    key = "entry-${listItem.entry.id}"
                                ) {
                                    EntryCard(
                                        entry = listItem.entry,
                                        hidden = listItem.entry.locked &&
                                                listItem.entry.id !in revealedEntryIds,
                                        query = query,
                                        revealRecordedTimes = revealRecordedTimes,
                                        onClick = { requestOpen(listItem.entry) },
                                    )
                                }
                            }
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
                    val index = listItems.indexOfFirst {
                        it is ListItem.DateHeader && it.date == picked
                    }
                    if (index >= 0) {
                        scope.launch { listState.animateScrollToItem(index) }
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
                    "There are no entries for ${date.shortLabel()}. " +
                            "Would you like to write one for that day?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dateWithNoEntries = null
                    onNewEntryForDate(date)
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

    if (reminderOpen) {
        ReminderDialog(
            initial = reminder,
            onDismiss = { reminderOpen = false },
            onSave = { enabled, hour, minute ->
                reminderOpen = false
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                if (enabled && needsPermission) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onSaveReminder(enabled, hour, minute)
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

    if (notificationsBlockedDialog) {
        AlertDialog(
            onDismissRequest = { notificationsBlockedDialog = false },
            title = { Text("Notifications are off") },
            text = {
                Text(
                    "Android is blocking notifications for this app, so the reminder " +
                            "won't appear. You can turn them back on in Settings, Apps, " +
                            "Journal, Notifications."
                )
            },
            confirmButton = {
                TextButton(onClick = { notificationsBlockedDialog = false }) { Text("OK") }
            },
        )
    }

    if (changePasswordOpen) {
        ChangePasswordDialog(
            onDismiss = { changePasswordOpen = false },
            onSubmit = onChangePassword,
        )
    }
}

@Composable
private fun TagFilterRow(
    tags: List<String>,
    activeTag: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = activeTag == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
            )
        }
        items(tags.size) { index ->
            val tag = tags[index]
            FilterChip(
                selected = activeTag == tag,
                onClick = { onSelect(if (activeTag == tag) null else tag) },
                label = { Text("#$tag") },
            )
        }
    }
}

@Composable
private fun OverviewCard(
    stats: JournalStats,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatBlock(stats.currentStreak.toString(), "day streak")
                StatBlock(stats.longestStreak.toString(), "best")
                StatBlock(stats.entries.toString(), "entries")
                StatBlock(stats.words.toString(), "words")
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
                                text = "Hidden entry — tap to unlock",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = MarkdownLite.render(hit.entry.text),
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
                "Tap New entry to write your first one. It will be stamped with " +
                        "today's date and the current time.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateHeaderRow(date: LocalDate, count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = date.prettyLabel(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (count > 1) {
                Text(
                    text = "$count entries",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: JournalEntry,
    hidden: Boolean,
    query: String,
    revealRecordedTimes: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (hidden) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(16.dp),
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = "  Hidden entry — tap to unlock",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                if (entry.mood != null) {
                    Text(text = entry.mood, fontSize = 22.sp)
                    Spacer(Modifier.height(4.dp))
                }

                Text(
                    text = if (entry.text.isBlank()) {
                        androidx.compose.ui.text.AnnotatedString("(empty)")
                    } else {
                        MarkdownLite.render(
                            source = entry.text,
                            highlight = query,
                            highlightStyle = SpanStyle(
                                background = MaterialTheme.colorScheme.primaryContainer,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )

                if (entry.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = entry.tags.joinToString(" ") { "#$it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (revealRecordedTimes) {
                Spacer(Modifier.height(8.dp))
                val edited = entry.updatedAt - entry.createdAt > 60_000L
                Text(
                    text = buildString {
                        append("Recorded ${entry.createdAt.stampLabel()}")
                        if (edited) append(" · edited ${entry.updatedAt.stampLabel()}")
                        if (entry.attachments.isNotEmpty()) {
                            append(" · ${entry.attachments.size} photo")
                            if (entry.attachments.size > 1) append("s")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
