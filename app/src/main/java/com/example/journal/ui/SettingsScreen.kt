package com.example.journal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.journal.ReminderSettings
import com.example.journal.UiMessage
import com.example.journal.data.MoodScale
import com.example.journal.data.Notebook
import com.example.journal.data.NotebookSet
import java.time.LocalDate

private val AUTO_LOCK_OPTIONS = listOf(
    0L to "Immediately",
    30_000L to "30 seconds",
    120_000L to "2 minutes",
    300_000L to "5 minutes",
    -1L to "Never",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    notebooks: NotebookSet,
    moodScale: MoodScale,
    placeholder: String,
    defaultNotebookId: Long,
    autoLockMillis: Long,
    collapseOldDays: Boolean,
    revealRecordedTimes: Boolean,
    reminder: ReminderSettings,
    transferInProgress: Boolean,
    message: UiMessage?,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
    onMoodEmoji: (Int, String) -> Unit,
    onMoodLabel: (Int, String) -> Unit,
    onResetMoodScale: () -> Unit,
    onPlaceholderChange: (String) -> Unit,
    onDefaultNotebookChange: (Long) -> Unit,
    onAutoLockChange: (Long) -> Unit,
    onCollapseChange: (Boolean) -> Unit,
    onRevealChange: (Boolean) -> Unit,
    onReminderChange: (Boolean, Int, Int) -> Unit,
    onCreateNotebook: (String) -> Unit,
    onRenameNotebook: (Long, String) -> Unit,
    onToggleNotebookPin: (Long) -> Unit,
    onDeleteNotebook: (Long) -> Unit,
    onChangePassword: (String, String, (String?) -> Unit) -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri, String) -> Unit,
    onLockNow: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var moodDialogIndex by remember { mutableStateOf<Int?>(null) }
    var notebookDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Notebook?>(null) }
    var deleteTarget by remember { mutableStateOf<Notebook?>(null) }
    var reminderOpen by remember { mutableStateOf(false) }
    var changePasswordOpen by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var blockedDialog by remember { mutableStateOf(false) }
    var placeholderDraft by rememberSaveable { mutableStateOf(placeholder) }

    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current.text)
        onMessageShown()
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) blockedDialog = true }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExport) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importUri = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (transferInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            SectionTitle("Mood scale")
            Text(
                text = "Each level is a number under the surface. Change an emoji or " +
                    "a label and every block you have ever written follows, because " +
                    "only the number is stored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))

            moodScale.levels.forEachIndexed { index, level ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = level.emoji,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clickable { moodDialogIndex = index }
                            .padding(end = 12.dp),
                    )
                    OutlinedTextField(
                        value = level.label,
                        onValueChange = { onMoodLabel(index, it) },
                        singleLine = true,
                        label = { Text("Label") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onResetMoodScale,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("Reset to defaults") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("Notebooks")
            notebooks.displayOrder.forEach { notebook ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = notebook.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { renameTarget = notebook },
                    )
                    FilterChip(
                        selected = notebook.pinned,
                        onClick = { onToggleNotebookPin(notebook.id) },
                        label = { Text("Pinned") },
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { deleteTarget = notebook }) { Text("Delete") }
                }
            }
            TextButton(
                onClick = { notebookDialogOpen = true },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("New notebook") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("Writing")
            OutlinedTextField(
                value = placeholderDraft,
                onValueChange = {
                    placeholderDraft = it
                    onPlaceholderChange(it)
                },
                label = { Text("Prompt shown in an empty block") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "New blocks land in this notebook unless you change it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notebooks.displayOrder.size) { index ->
                    val notebook = notebooks.displayOrder[index]
                    FilterChip(
                        selected = defaultNotebookId == notebook.id,
                        onClick = { onDefaultNotebookChange(notebook.id) },
                        label = { Text(notebook.name) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("Privacy")
            Text(
                text = "Lock the journal after it has been in the background for",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(AUTO_LOCK_OPTIONS.size) { index ->
                    val (value, label) = AUTO_LOCK_OPTIONS[index]
                    val selected = when (value) {
                        0L, 30_000L, 120_000L, 300_000L -> autoLockMillis == value
                        else -> autoLockMillis <= 0L
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onAutoLockChange(value) },
                        label = { Text(label) },
                    )
                }
            }
            Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { changePasswordOpen = true }) {
                    Text("Change password")
                }
                TextButton(onClick = onLockNow) { Text("Lock now") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("List")
            ToggleRow("Collapse days older than a week", collapseOldDays, onCollapseChange)
            ToggleRow("Show recorded times", revealRecordedTimes, onRevealChange)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("Reminder")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (reminder.enabled)
                        "Every day at " + "%02d:%02d".format(reminder.hour, reminder.minute)
                    else "Off",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { reminderOpen = true }) { Text("Change") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("Data")
            Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                TextButton(
                    enabled = !transferInProgress,
                    onClick = {
                        exportLauncher.launch("journal-backup-" + LocalDate.now() + ".json")
                    },
                ) { Text("Export") }
                TextButton(
                    enabled = !transferInProgress,
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                ) { Text("Import") }
            }
            Text(
                text = "Exports are encrypted with the password in force when you " +
                    "make them. Keep that password with the file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(40.dp))
        }
    }

    moodDialogIndex?.let { index ->
        EmojiDialog(
            title = "Level " + (index - MoodScale.OFFSET),
            initial = moodScale.levels.getOrNull(index)?.emoji ?: "",
            onDismiss = { moodDialogIndex = null },
            onConfirm = { emoji ->
                moodDialogIndex = null
                onMoodEmoji(index, emoji)
            },
        )
    }

    if (notebookDialogOpen) {
        NameDialog(
            title = "New notebook",
            initial = "",
            label = "Name",
            onDismiss = { notebookDialogOpen = false },
            onConfirm = { name ->
                notebookDialogOpen = false
                onCreateNotebook(name)
            },
        )
    }

    renameTarget?.let { notebook ->
        NameDialog(
            title = "Rename notebook",
            initial = notebook.name,
            label = "Name",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                onRenameNotebook(notebook.id, name)
            },
        )
    }

    deleteTarget?.let { notebook ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"" + notebook.name + "\"?") },
            text = {
                Text(
                    "Blocks in this notebook are moved to your other notebooks, " +
                        "not deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    onDeleteNotebook(notebook.id)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
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
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onReminderChange(enabled, hour, minute)
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

    if (blockedDialog) {
        AlertDialog(
            onDismissRequest = { blockedDialog = false },
            title = { Text("Notifications are off") },
            text = {
                Text(
                    "Android is blocking notifications for this app, so the reminder " +
                        "will not appear. You can turn them back on in Settings, Apps, " +
                        "Journal, Notifications."
                )
            },
            confirmButton = {
                TextButton(onClick = { blockedDialog = false }) { Text("OK") }
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
