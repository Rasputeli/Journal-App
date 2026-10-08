package com.example.journal.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.journal.EditorState
import com.example.journal.data.MoodScale
import com.example.journal.data.NotebookSet
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorState,
    moodScale: MoodScale,
    notebooks: NotebookSet,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onValenceChange: (Int?) -> Unit,
    onCustomMoodChange: (String?) -> Unit,
    onNotebookChange: (Long) -> Unit,
    onCreateNotebook: (String) -> Unit,
    onStartNewBlock: () -> Unit,
    onToggleLock: () -> Unit,
    onAddPhotoFromUri: (Uri) -> Unit,
    onAddPhotoFromFile: (File) -> Unit,
    onRemovePhoto: (String) -> Unit,
    thumbnailFor: suspend (String) -> ImageBitmap?,
    fullImageFor: suspend (String) -> ImageBitmap?,
    onSave: () -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current

    var datePickerOpen by remember { mutableStateOf(false) }
    var timePickerOpen by remember { mutableStateOf(false) }
    var moodDialogOpen by remember { mutableStateOf(false) }
    var notebookDialogOpen by remember { mutableStateOf(false) }
    var discardDialogOpen by remember { mutableStateOf(false) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var captureFile by remember { mutableStateOf<File?>(null) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.entryId) {
        if (state.isNew) runCatching { focusRequester.requestFocus() }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) onAddPhotoFromUri(uri) }

    val cameraLauncher = rememberLauncherForActivityResult(
        TakePictureWithPermission()
    ) { saved ->
        val file = captureFile
        captureFile = null
        if (file != null) {
            if (saved) onAddPhotoFromFile(file) else file.delete()
        }
    }

    fun attemptClose() {
        if (state.isDirty) discardDialogOpen = true else onClose()
    }

    BackHandler { attemptClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { attemptClose() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                title = { Text(if (state.isNew) "New block" else "Edit block") },
                actions = {
                    IconButton(onClick = onToggleLock) {
                        Icon(
                            imageVector = if (state.locked) Icons.Default.Lock
                            else Icons.Default.LockOpen,
                            contentDescription = if (state.locked)
                                "Hidden from the list" else "Visible in the list",
                        )
                    }
                    if (!state.isNew) {
                        IconButton(onClick = { deleteDialogOpen = true }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete block",
                            )
                        }
                    }
                    TextButton(onClick = onSave, enabled = state.text.isNotBlank()) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (state.canUndoMerge) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Continuing this block",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onStartNewBlock) {
                            Text("Start a new one")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { datePickerOpen = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "  " + state.date.prettyLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = state.eventTime.timeLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { timePickerOpen = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            HorizontalDivider()

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                NotebookChipRow(
                    notebooks = notebooks.displayOrder,
                    selectedId = state.notebookId,
                    onSelect = onNotebookChange,
                    onCreate = { notebookDialogOpen = true },
                )
            }

            HorizontalDivider()

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                MoodChipRow(
                    scale = moodScale,
                    valence = state.valence,
                    customMood = state.customMood,
                    onValence = onValenceChange,
                    onCustom = { moodDialogOpen = true },
                )
            }

            HorizontalDivider()

            PhotoStrip(
                names = state.attachments,
                thumbnailFor = thumbnailFor,
                fullImageFor = fullImageFor,
                onRemove = onRemovePhoto,
                onAddFromGallery = { galleryLauncher.launch("image/*") },
                onTakePhoto = {
                    val directory = File(context.cacheDir, "camera")
                    directory.mkdirs()
                    val file = File(directory, "capture-" + System.currentTimeMillis() + ".jpg")
                    captureFile = file
                    val uri = FileProvider.getUriForFile(
                        context,
                        context.packageName + ".fileprovider",
                        file,
                    )
                    cameraLauncher.launch(uri)
                },
            )

            HorizontalDivider()

            TextField(
                value = state.text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(placeholder.ifBlank { "Write freely. #tag it, add a photo." })
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            HorizontalDivider()

            val footer = buildString {
                append(countWords(state.text))
                append(" words")
                if (state.attachments.isNotEmpty()) {
                    append("  ·  ")
                    append(state.attachments.size)
                    append(if (state.attachments.size == 1) " photo" else " photos")
                }
                if (state.locked) append("  ·  hidden")
                val recorded = state.recordedAt
                if (recorded != null) {
                    append("  ·  recorded ")
                    append(recorded.stampLabel())
                    val updated = state.updatedAt
                    if (updated != null && updated - recorded > 60_000L) {
                        append("  ·  edited ")
                        append(updated.stampLabel())
                    }
                }
            }
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    if (datePickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerOpen = false
                    pickerState.selectedDateMillis?.utcToLocalDate()?.let(onDateChange)
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
            initialHour = state.eventTime.hourOfDay(),
            initialMinute = state.eventTime.minuteOfHour(),
            onDismiss = { timePickerOpen = false },
            onConfirm = { hour, minute ->
                timePickerOpen = false
                onTimeChange(hour, minute)
            },
        )
    }

    if (moodDialogOpen) {
        EmojiDialog(
            title = "Custom mood",
            initial = state.customMood ?: "",
            onDismiss = { moodDialogOpen = false },
            onConfirm = { emoji ->
                moodDialogOpen = false
                onCustomMoodChange(emoji)
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

    if (discardDialogOpen) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this block will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    discardDialogOpen = false
                    onClose()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { discardDialogOpen = false }) { Text("Keep editing") }
            },
        )
    }

    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("Delete this block?") },
            text = { Text("This cannot be undone. Its photos go too.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialogOpen = false
                    state.entryId?.let(onDelete)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogOpen = false }) { Text("Cancel") }
            },
        )
    }
}
