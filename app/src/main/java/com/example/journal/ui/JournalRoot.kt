package com.example.journal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.journal.JournalViewModel
import com.example.journal.LockState

@Composable
fun JournalRoot(viewModel: JournalViewModel) {

    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
    val moodScale by viewModel.moodScale.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val moodSeries by viewModel.moodSeries.collectAsStateWithLifecycle()
    val onThisDay by viewModel.onThisDay.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val authBusy by viewModel.authBusy.collectAsStateWithLifecycle()
    val reveal by viewModel.revealRecordedTimes.collectAsStateWithLifecycle()
    val collapse by viewModel.collapseOldDays.collectAsStateWithLifecycle()
    val expansion by viewModel.expansion.collectAsStateWithLifecycle()
    val revealedEntries by viewModel.revealedEntries.collectAsStateWithLifecycle()
    val placeholder by viewModel.placeholder.collectAsStateWithLifecycle()
    val defaultNotebook by viewModel.defaultNotebookId.collectAsStateWithLifecycle()
    val autoLock by viewModel.autoLockMillis.collectAsStateWithLifecycle()
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val transfer by viewModel.transferInProgress.collectAsStateWithLifecycle()

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
            val open = editor
            when {
                open != null -> EditorScreen(
                    state = open,
                    moodScale = moodScale,
                    notebooks = notebooks,
                    placeholder = placeholder,
                    onTextChange = viewModel::updateText,
                    onDateChange = viewModel::setDate,
                    onTimeChange = viewModel::setTime,
                    onValenceChange = viewModel::setValence,
                    onCustomMoodChange = viewModel::setCustomMood,
                    onNotebookChange = viewModel::setDraftNotebook,
                    onCreateNotebook = { viewModel.createNotebook(it, select = true) },
                    onStartNewBlock = viewModel::startNewBlock,
                    onToggleLock = viewModel::toggleDraftLock,
                    onAddPhotoFromUri = viewModel::addPhotoFromUri,
                    onAddPhotoFromFile = viewModel::addPhotoFromFile,
                    onRemovePhoto = viewModel::removePhoto,
                    thumbnailFor = viewModel::thumbnailFor,
                    fullImageFor = viewModel::fullImageFor,
                    onSave = viewModel::saveDraft,
                    onDelete = viewModel::deleteEntry,
                    onClose = viewModel::closeEditor,
                )

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

                else -> JournalListScreen(
                    entries = entries,
                    notebooks = notebooks,
                    stats = stats,
                    moodSeries = moodSeries,
                    moodScale = moodScale,
                    onThisDayHits = onThisDay,
                    revealedEntryIds = revealedEntries,
                    revealRecordedTimes = reveal,
                    collapseOldDays = collapse,
                    expansion = expansion,
                    message = message,
                    transferInProgress = transfer,
                    onMessageShown = viewModel::clearMessage,
                    onNewBlock = { viewModel.openNewEntry() },
                    onNewBlockForDate = { viewModel.openNewEntry(it, null) },
                    onOpenEntry = viewModel::openEntry,
                    onRevealEntry = viewModel::revealEntry,
                    onToggleDay = viewModel::toggleDay,
                    onOpenSettings = { settingsOpen = true },
                    onLock = viewModel::lock,
                    onExport = viewModel::exportTo,
                    onImport = viewModel::importFrom,
                )
            }
        }
    }
}
