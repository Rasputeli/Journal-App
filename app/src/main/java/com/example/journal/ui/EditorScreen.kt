package com.example.journal.ui

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.journal.EditorState
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorState,
    onTextChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onMoodChange: (String?) -> Unit,
    onToggleLock: () -> Unit,
    onAddPhotoFromUri: (android.net.Uri) -> Unit,
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
    var discardDialogOpen by remember { mutableStateOf(false) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var captureFile by remember { mutableStateOf<File?>(null) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.entryId) {
        if (state.isNew) runCatching { focusRequester.requestFocus() }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) onAddPhotoFromUri(uri) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
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
                title = { Text(if (state.isNew) "New entry" else "Edit entry") },
                actions = {
                    IconButton(onClick = onToggleLock) {
                        Icon(
                            imageVector = if (state.locked) Icons.Default.Lock
                            else Icons.Default.LockOpen,
                            contentDescription = if (state.locked)
                                "Hidden from the list" else "Visible in the list",
                        )
                    }
                    IconButton(onClick = { preview = !preview }) {
                        Icon(
                            imageVector = if (preview) Icons.Outlined.Edit
                            else Icons.Outlined.Visibility,
                            contentDescription = if (preview)
                                "Edit text" else "Preview formatting",
                        )
                    }
                    if (!state.isNew) {
                        IconButton(onClick = { deleteDialogOpen = true }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete entry",
                            )
                        }
                    }
                    TextButton(
                        onClick = onSave,
                        enabled = state.text.isNotBlank(),
                    ) { Text("Save") }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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

            HorizontalDivider()

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.mood == null,
                            onClick = { onMoodChange(null) },
                            label = { Text("No mood") },
                        )
                    }
                    items(MOODS.size) { index ->
                        val mood = MOODS[index]
                        FilterChip(
                            selected = state.mood == mood,
                            onClick = {
                                onMoodChange(if (state.mood == mood) null else mood)
                            },
                            label = { Text(mood, fontSize = 18.sp) },
                        )
                    }
                }
            }

            HorizontalDivider()

            PhotoStrip(
                names = state.attachments,
                thumbnailFor = thumbnailFor,
                fullImageFor = fullImageFor,
                onRemove = onRemovePhoto,
                onAddFromGallery = { galleryLauncher.launch("image/*") },
                onTakePhoto = {
                    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
                    captureFile = file
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    cameraLauncher.launch(uri)
                },
            )

            HorizontalDivider()

            if (preview) {
                SelectionContainer {
                    Text(
                        text = if (state.text.isBlank()) {
                            MarkdownLite.render("Nothing to preview yet.")
                        } else {
                            MarkdownLite.render(state.text)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    )
                }
            } else {
                TextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text("Write freely. #tag it, add a photo, and markdown works: **bold**.")
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }

            HorizontalDivider()

            val footer = buildString {
                append("${countWords(state.text)} words")
                if (state.attachments.isNotEmpty()) {
                    append("  ·  ${state.attachments.size} photo")
                    if (state.attachments.size > 1) append("s")
                }
                if (state.locked) append("  ·  hidden")
                val created = state.createdAt
                if (created != null) {
                    append("  ·  recorded ${created.stampLabel()}")
                    val updated = state.updatedAt
                    if (updated != null && updated - created > 60_000L) {
                        append("  ·  edited ${updated.stampLabel()}")
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

    if (discardDialogOpen) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this entry will be lost.") },
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
            title = { Text("Delete this entry?") },
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
