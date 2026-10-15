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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.journal.DayDocument
import com.example.journal.DocumentSection
import com.example.journal.data.MoodScale
import com.example.journal.data.NotebookSet
import com.example.journal.data.Task
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDocumentScreen(
    document: DayDocument,
    notebooks: NotebookSet,
    moodScale: MoodScale,
    notebookTabs: List<Long>,
    dayTasks: List<Task>,
    placeholder: String,
    revealRecordedTimes: Boolean,
    onBack: () -> Unit,
    onSelectNotebook: (Long) -> Unit,
    onTextChange: (Int, String) -> Unit,
    onSectionValence: (Int, Int?) -> Unit,
    onSectionCustomMood: (Int, String?) -> Unit,
    onAddSection: () -> Unit,
    onToggleSectionLock: (Int) -> Unit,
    onMoveSection: (Int, LocalDate) -> Unit,
    onDeleteSection: (Int) -> Unit,
    onAddPhoto: (Int, Uri) -> Unit,
    onAddPhotoFile: (Int, File) -> Unit,
    onRemovePhoto: (Int, String) -> Unit,
    thumbnailFor: suspend (String) -> ImageBitmap?,
    fullImageFor: suspend (String) -> ImageBitmap?,
    onTaskToggle: (Long) -> Unit,
) {
    val context = LocalContext.current

    var optionsIndex by remember { mutableStateOf<Int?>(null) }
    var moveIndex by remember { mutableStateOf<Int?>(null) }
    var photoIndex by remember { mutableStateOf(0) }
    var captureFile by remember { mutableStateOf<File?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onAddPhoto(photoIndex, uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        TakePictureWithPermission()
    ) { saved ->
        val file = captureFile
        captureFile = null
        if (file != null) {
            if (saved) onAddPhotoFile(photoIndex, file) else file.delete()
        }
    }

    fun startCamera(index: Int) {
        val directory = File(context.cacheDir, "camera")
        directory.mkdirs()
        val file = File(directory, "capture-" + System.currentTimeMillis() + ".jpg")
        captureFile = file
        photoIndex = index
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
        cameraLauncher.launch(uri)
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                title = {
                    Text(document.date.prettyLabel(), fontSize = 17.sp)
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            if (notebookTabs.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(notebookTabs.size) { index ->
                        val id = notebookTabs[index]
                        FilterChip(
                            selected = id == document.notebookId,
                            onClick = { onSelectNotebook(id) },
                            label = { Text(notebooks.nameOf(id) ?: "Notebook") },
                        )
                    }
                }
                HorizontalDivider()
            }

            document.sections.forEachIndexed { index, section ->
                SectionView(
                    section = section,
                    scale = moodScale,
                    placeholder = placeholder,
                    revealRecordedTimes = revealRecordedTimes,
                    onTextChange = { onTextChange(index, it) },
                    onOpenOptions = { optionsIndex = index },
                    thumbnailFor = thumbnailFor,
                    fullImageFor = fullImageFor,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAddSection) { Text("+ New timestamped section") }
            }

            if (dayTasks.isNotEmpty()) {
                HorizontalDivider()
                TaskStrip(tasks = dayTasks, onToggle = onTaskToggle)
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    optionsIndex?.let { index ->
        val section = document.sections.getOrNull(index)
        if (section != null) {
            SectionOptionsDialog(
                section = section,
                scale = moodScale,
                revealRecordedTimes = revealRecordedTimes,
                thumbnailFor = thumbnailFor,
                fullImageFor = fullImageFor,
                onDismiss = { optionsIndex = null },
                onValence = { onSectionValence(index, it) },
                onCustomMood = { onSectionCustomMood(index, it) },
                onToggleLock = { onToggleSectionLock(index) },
                onRemovePhoto = { onRemovePhoto(index, it) },
                onCamera = { optionsIndex = null; startCamera(index) },
                onGallery = {
                    optionsIndex = null
                    photoIndex = index
                    galleryLauncher.launch("image/*")
                },
                onMove = { optionsIndex = null; moveIndex = index },
                onDelete = { optionsIndex = null; onDeleteSection(index) },
            )
        }
    }

    moveIndex?.let { index ->
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = document.date.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { moveIndex = null },
            confirmButton = {
                TextButton(onClick = {
                    val picked = pickerState.selectedDateMillis?.utcToLocalDate()
                    moveIndex = null
                    if (picked != null) onMoveSection(index, picked)
                }) { Text("Move") }
            },
            dismissButton = {
                TextButton(onClick = { moveIndex = null }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SectionView(
    section: DocumentSection,
    scale: MoodScale,
    placeholder: String,
    revealRecordedTimes: Boolean,
    onTextChange: (String) -> Unit,
    onOpenOptions: () -> Unit,
    thumbnailFor: suspend (String) -> ImageBitmap?,
    fullImageFor: suspend (String) -> ImageBitmap?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenOptions),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.eventTime.timeLabel(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            val mood = section.valence?.let { scale.emojiFor(it) } ?: section.customMood
            if (mood != null) {
                Text(text = "  " + mood, fontSize = 15.sp)
            }
            if (section.attachments.isNotEmpty()) {
                Text(
                    text = "  " + section.attachments.size +
                        (if (section.attachments.size == 1) " photo" else " photos"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (section.locked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp).height(13.dp),
                )
            }
        }

        TextField(
            value = section.text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    placeholder.ifBlank { "Write freely. Tap the time above for mood and photos." },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        SectionPhotos(
            names = section.attachments,
            thumbnailFor = thumbnailFor,
            fullImageFor = fullImageFor,
        )

        if (revealRecordedTimes && section.recordedAt != null) {
            val recorded = section.recordedAt
            val updated = section.updatedAt
            val edited = updated != null && updated - recorded > 3_600_000L
            Text(
                text = buildString {
                    append("Recorded ")
                    append(recorded.stampLabel())
                    if (edited && updated != null) {
                        append(" · edited ")
                        append(updated.stampLabel())
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun TaskStrip(tasks: List<Task>, onToggle: (Long) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "Tasks",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
        )
        tasks.forEach { task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = task.done,
                    onCheckedChange = { onToggle(task.id) },
                )
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (task.dueTimeMinutes != null) {
                    Text(
                        text = minutesToLabel(task.dueTimeMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionOptionsDialog(
    section: DocumentSection,
    scale: MoodScale,
    revealRecordedTimes: Boolean,
    thumbnailFor: suspend (String) -> ImageBitmap?,
    fullImageFor: suspend (String) -> ImageBitmap?,
    onDismiss: () -> Unit,
    onValence: (Int?) -> Unit,
    onCustomMood: (String?) -> Unit,
    onToggleLock: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var emojiOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(section.eventTime.timeLabel() + " · this section") },
        text = {
            Column {
                MoodChipRow(
                    scale = scale,
                    valence = section.valence,
                    customMood = section.customMood,
                    onValence = onValence,
                    onCustom = { emojiOpen = true },
                )

                Spacer(Modifier.height(10.dp))

                if (section.attachments.isNotEmpty()) {
                    SectionPhotos(
                        names = section.attachments,
                        thumbnailFor = thumbnailFor,
                        fullImageFor = fullImageFor,
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(section.attachments.size) { index ->
                            val name = section.attachments[index]
                            TextButton(onClick = { onRemovePhoto(name) }) {
                                Text("Remove " + (index + 1))
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onCamera) { Text("Camera") }
                    TextButton(onClick = onGallery) { Text("Gallery") }
                }

                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onToggleLock) {
                        Text(if (section.locked) "Unhide" else "Hide")
                    }
                    TextButton(onClick = onMove) { Text("Change date") }
                }

                if (revealRecordedTimes && section.recordedAt != null) {
                    Text(
                        text = "Recorded " + section.recordedAt.stampLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
        },
    )

    if (emojiOpen) {
        EmojiDialog(
            title = "Custom mood",
            initial = section.customMood ?: "",
            onDismiss = { emojiOpen = false },
            onConfirm = { emoji ->
                emojiOpen = false
                onCustomMood(emoji)
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this section?") },
            text = { Text("Its text and photos go too. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
