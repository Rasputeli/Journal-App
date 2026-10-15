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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.journal.UiMessage
import com.example.journal.data.NotebookSet
import com.example.journal.data.Task
import java.time.LocalDate

private const val ALL_NOTEBOOKS = -1L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    tasks: List<Task>,
    notebooks: NotebookSet,
    message: UiMessage?,
    onMessageShown: () -> Unit,
    onOpenSettings: () -> Unit,
    onAdd: (String, String, LocalDate?, Int?, Long) -> Unit,
    onUpdate: (Task) -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<Task?>(null) }
    var adding by remember { mutableStateOf(false) }
    var selectedNotebook by remember { mutableStateOf(ALL_NOTEBOOKS) }

    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current.text)
        onMessageShown()
    }

    val today = LocalDate.now()
    val scoped = tasks.filter {
        selectedNotebook == ALL_NOTEBOOKS || it.notebookId == selectedNotebook
    }
    val open = scoped.filterNot { it.done }
    val overdue = open.filter { it.dueDate != null && it.dueDate.isBefore(today) }
    val dueToday = open.filter { it.dueDate == today }
    val upcoming = open
        .filter { it.dueDate != null && it.dueDate.isAfter(today) }
        .sortedBy { it.dueDate }
    val someday = open.filter { it.dueDate == null }
    val done = scoped
        .filter { it.done }
        .sortedByDescending { it.completedAt ?: 0L }
        .take(25)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Edit, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add task") },
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

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No tasks yet. Tap Add task to start.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    taskSection(
                        title = "Overdue",
                        tasks = overdue,
                        today = today,
                        onToggle = onToggle,
                        onEdit = { editing = it },
                    )
                    taskSection(
                        title = "Today",
                        tasks = dueToday,
                        today = today,
                        onToggle = onToggle,
                        onEdit = { editing = it },
                    )
                    taskSection(
                        title = "Upcoming",
                        tasks = upcoming,
                        today = today,
                        onToggle = onToggle,
                        onEdit = { editing = it },
                    )
                    taskSection(
                        title = "Someday",
                        tasks = someday,
                        today = today,
                        onToggle = onToggle,
                        onEdit = { editing = it },
                    )
                    taskSection(
                        title = "Done",
                        tasks = done,
                        today = today,
                        onToggle = onToggle,
                        onEdit = { editing = it },
                    )
                }
            }
        }
    }

    if (adding) {
        TaskDialog(
            task = null,
            notebooks = notebooks,
            onDismiss = { adding = false },
            onSave = { title, note, due, time, notebook ->
                adding = false
                onAdd(title, note, due, time, notebook)
            },
            onDelete = null,
        )
    }

    editing?.let { task ->
        TaskDialog(
            task = task,
            notebooks = notebooks,
            onDismiss = { editing = null },
            onSave = { title, note, due, time, notebook ->
                editing = null
                onUpdate(
                    task.copy(
                        title = title,
                        note = note,
                        dueDate = due,
                        dueTimeMinutes = time,
                        notebookId = notebook,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            },
            onDelete = {
                editing = null
                onDelete(task.id)
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.taskSection(
    title: String,
    tasks: List<Task>,
    today: LocalDate,
    onToggle: (Long) -> Unit,
    onEdit: (Task) -> Unit,
) {
    if (tasks.isEmpty()) return
    item(key = "head-" + title) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
        )
    }
    tasks.forEach { task ->
        item(key = "task-" + task.id) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp)
                    .clickable { onEdit(task) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = task.done,
                        onCheckedChange = { onToggle(task.id) },
                    )
                    Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (task.done)
                                TextDecoration.LineThrough else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val due = task.dueDate
                        val time = task.dueTimeMinutes
                        if (due != null || time != null) {
                            val overdue = due != null && due.isBefore(today) && !task.done
                            Text(
                                text = buildString {
                                    if (due != null) {
                                        append(
                                            if (due == today) "Today"
                                            else if (due == today.minusDays(1)) "Yesterday"
                                            else due.shortLabel()
                                        )
                                    }
                                    if (time != null) {
                                        if (due != null) append(" · ")
                                        append(minutesToLabel(time))
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (overdue)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (task.note.isNotBlank()) {
                            Text(
                                text = task.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDialog(
    task: Task?,
    notebooks: NotebookSet,
    onDismiss: () -> Unit,
    onSave: (String, String, LocalDate?, Int?, Long) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var note by remember { mutableStateOf(task?.note ?: "") }
    var due by remember { mutableStateOf(task?.dueDate) }
    var time by remember { mutableStateOf(task?.dueTimeMinutes) }
    var notebookId by remember { mutableStateOf(task?.notebookId ?: notebooks.firstId ?: NotebookSet.DEFAULT_ID) }

    var datePickerOpen by remember { mutableStateOf(false) }
    var timePickerOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "New task" else "Edit task") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("What needs doing") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { datePickerOpen = true }) {
                        Text(due?.shortLabel() ?: "Set due date")
                    }
                    if (due != null) {
                        TextButton(onClick = { due = null }) { Text("Clear") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        enabled = due != null,
                        onClick = { timePickerOpen = true },
                    ) {
                        Text(time?.let { minutesToLabel(it) } ?: "Set time")
                    }
                    if (time != null) {
                        TextButton(onClick = { time = null }) { Text("Clear time") }
                    }
                }

                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notebooks.displayOrder.size) { index ->
                        val notebook = notebooks.displayOrder[index]
                        FilterChip(
                            selected = notebookId == notebook.id,
                            onClick = { notebookId = notebook.id },
                            label = { Text(notebook.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title.trim(), note.trim(), due, time, notebookId) },
            ) { Text("Save") }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) { Text("Delete") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (datePickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (due ?: LocalDate.now()).toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerOpen = false
                    pickerState.selectedDateMillis
                        ?.utcToLocalDate()
                        ?.let { due = it }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (timePickerOpen) {
        TimePickDialog(
            initialHour = (time ?: 540) / 60,
            initialMinute = (time ?: 540) % 60,
            onDismiss = { timePickerOpen = false },
            onConfirm = { hour, minute ->
                timePickerOpen = false
                time = hour * 60 + minute
            },
        )
    }
}
