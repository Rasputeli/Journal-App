package com.example.journal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.journal.JournalViewModel
import com.example.journal.LockState

@Composable
fun JournalRoot(viewModel: JournalViewModel) {

    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val onThisDay by viewModel.onThisDay.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val authBusy by viewModel.authBusy.collectAsStateWithLifecycle()
    val reveal by viewModel.revealRecordedTimes.collectAsStateWithLifecycle()
    val revealedEntries by viewModel.revealedEntries.collectAsStateWithLifecycle()
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val transfer by viewModel.transferInProgress.collectAsStateWithLifecycle()

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
            if (open != null) {
                EditorScreen(
                    state = open,
                    onTextChange = { viewModel.updateDraft(text = it) },
                    onDateChange = { viewModel.updateDraft(date = it) },
                    onMoodChange = viewModel::setMood,
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
            } else {
                JournalListScreen(
                    entries = entries,
                    stats = stats,
                    onThisDayHits = onThisDay,
                    revealedEntryIds = revealedEntries,
                    revealRecordedTimes = reveal,
                    reminder = reminder,
                    transferInProgress = transfer,
                    message = message,
                    onMessageShown = viewModel::clearMessage,
                    onToggleRevealRecordedTimes = viewModel::toggleRevealRecordedTimes,
                    onNewEntry = { viewModel.openNewEntry() },
                    onNewEntryForDate = { viewModel.openNewEntry(it) },
                    onOpenEntry = viewModel::openEntry,
                    onRevealEntry = viewModel::revealEntry,
                    onLock = viewModel::lock,
                    onChangePassword = viewModel::changePassword,
                    onSaveReminder = viewModel::setReminder,
                    onExport = viewModel::exportTo,
                    onImport = viewModel::importFrom,
                )
            }
        }
    }
}
