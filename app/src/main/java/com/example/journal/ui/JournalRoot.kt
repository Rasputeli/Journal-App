package com.example.journal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.journal.JournalViewModel
import com.example.journal.LockState
import java.time.LocalDate

@Composable
fun JournalRoot(viewModel: JournalViewModel) {

    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val moodScale by viewModel.moodScale.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val moodSeries by viewModel.moodSeries.collectAsStateWithLifecycle()
    val onThisDay by viewModel.onThisDay.collectAsStateWithLifecycle()
    val document by viewModel.document.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val authBusy by viewModel.authBusy.collectAsStateWithLifecycle()
    val reveal by viewModel.revealRecordedTimes.collectAsStateWithLifecycle()
    val collapse by viewModel.collapseOldDays.collectAsStateWithLifecycle()
    val placeholder by viewModel.placeholder.collectAsStateWithLifecycle()
    val defaultNotebook by viewModel.defaultNotebookId.collectAsStateWithLifecycle()
    val autoLock by viewModel.autoLockMillis.collectAsStateWithLifecycle()
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val transfer by viewModel.transferInProgress.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    when (lockState) {
        LockState.NeedsSetup -> SetupPasswordScreen(
            error = authError,
            busy = authBusy,
            onSetup = viewModel::setupPassword,
        )

        LockState.Locked -> UnlockScreen(
            error = authError,
            busy = authBusy,
            onUnlock = viewModel::unlock,
        )

        LockState.Unlocked -> {
            val open = document
            when {
                open != null -> {
                    val dayTasks = tasks.filter { task ->
                        val completedHere = task.completedAt?.let { stamp ->
                            java.time.Instant.ofEpochMilli(stamp)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate() == open.date
                        } ?: false
                        task.dueDate == open.date || completedHere
                    }
                    DayDocumentScreen(
                        document = open,
                        notebooks = notebooks,
                        moodScale = moodScale,
                        notebookTabs = entries
                            .filter { it.date == open.date }
                            .map { it.notebookId }
                            .distinct(),
                        dayTasks = dayTasks,
                        placeholder = placeholder,
                        revealRecordedTimes = reveal,
                        onBack = viewModel::closeDocument,
                        onSelectNotebook = viewModel::switchDocumentNotebook,
                        onTextChange = viewModel::updateSectionText,
                        onSectionValence = viewModel::setSectionValence,
                        onSectionCustomMood = viewModel::setSectionCustomMood,
                        onAddSection = viewModel::addSection,
                        onToggleSectionLock = viewModel::toggleSectionLock,
                        onMoveSection = viewModel::moveSection,
                        onDeleteSection = viewModel::deleteSection,
                        onAddPhoto = viewModel::addPhotoToSection,
                        onAddPhotoFile = viewModel::addPhotoFileToSection,
                        onRemovePhoto = viewModel::removePhotoFromSection,
                        thumbnailFor = viewModel::thumbnailFor,
                        fullImageFor = viewModel::fullImageFor,
                        onTaskToggle = viewModel::toggleTask,
                    )
                }

                settingsOpen -> SettingsScreen(
                    notebooks = notebooks,
                    moodScale = moodScale,
                    placeholder = placeholder,
                    defaultNotebookId = defaultNotebook,
                    autoLockMillis = autoLock,
                    collapseOldDays = collapse,
                    revealRecordedTimes = reveal,
                    reminder = reminder,
                    transferInProgress = transfer,
                    message = message,
                    onMessageShown = viewModel::clearMessage,
                    onBack = { settingsOpen = false },
                    onMoodEmoji = viewModel::setMoodEmoji,
                    onMoodLabel = viewModel::setMoodLabel,
                    onResetMoodScale = viewModel::resetMoodScale,
                    onPlaceholderChange = viewModel::setPlaceholder,
                    onDefaultNotebookChange = viewModel::setDefaultNotebook,
                    onAutoLockChange = viewModel::setAutoLock,
                    onCollapseChange = viewModel::setCollapseOldDays,
                    onRevealChange = viewModel::setRevealRecordedTimes,
                    onReminderChange = viewModel::setReminder,
                    onCreateNotebook = { viewModel.createNotebook(it, select = false) },
                    onRenameNotebook = viewModel::renameNotebook,
                    onToggleNotebookPin = viewModel::toggleNotebookPin,
                    onDeleteNotebook = viewModel::deleteNotebook,
                    onChangePassword = viewModel::changePassword,
                    onExport = viewModel::exportTo,
                    onImport = viewModel::importFrom,
                    onLockNow = viewModel::lock,
                )

                else -> Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                },
                                label = { Text("Journal") },
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                },
                                label = { Text("Tasks") },
                            )
                        }
                    },
                ) { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner),
                    ) {
                        if (tab == 0) {
                            JournalListScreen(
                                entries = entries,
                                notebooks = notebooks,
                                stats = stats,
                                moodSeries = moodSeries,
                                moodScale = moodScale,
                                onThisDayHits = onThisDay,
                                revealRecordedTimes = reveal,
                                collapseOldDays = collapse,
                                message = message,
                                transferInProgress = transfer,
                                onMessageShown = viewModel::clearMessage,
                                onNewBlock = { viewModel.openToday() },
                                onOpenDay = { date: LocalDate, notebookId: Long ->
                                    viewModel.openDay(date, notebookId)
                                },
                                onOpenSettings = { settingsOpen = true },
                            )
                        } else {
                            TasksScreen(
                                tasks = tasks,
                                notebooks = notebooks,
                                message = message,
                                onMessageShown = viewModel::clearMessage,
                                onOpenSettings = { settingsOpen = true },
                                onAdd = { title, note, due, time, notebook ->
                                    viewModel.addTask(title, note, due, time, notebook)
                                },
                                onUpdate = viewModel::updateTask,
                                onToggle = viewModel::toggleTask,
                                onDelete = viewModel::deleteTask,
                            )
                        }
                    }
                }
            }
        }
    }
}
