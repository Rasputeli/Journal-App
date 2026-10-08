package com.example.journal

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.journal.data.AuthManager
import com.example.journal.data.EntryRepository
import com.example.journal.data.ImageCompressor
import com.example.journal.data.JournalEntry
import com.example.journal.data.MoodScale
import com.example.journal.data.MoodScaleStore
import com.example.journal.data.Notebook
import com.example.journal.data.NotebookSet
import com.example.journal.data.SettingsStore
import com.example.journal.ui.JournalStats
import com.example.journal.ui.atTimeOfDay
import com.example.journal.ui.MoodPoint
import com.example.journal.ui.OnThisDayHit
import com.example.journal.ui.computeMoodSeries
import com.example.journal.ui.computeOnThisDay
import com.example.journal.ui.computeStats
import com.example.journal.ui.decodeSampled
import com.example.journal.ui.hourOfDay
import com.example.journal.ui.minuteOfHour
import com.example.journal.ui.timeLabel
import com.example.journal.widget.JournalWidget
import com.example.journal.work.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

sealed interface LockState {
    data object NeedsSetup : LockState
    data object Locked : LockState
    data object Unlocked : LockState
}

/**
 * The block open in the editor. It lives here rather than in the composable so
 * a background auto-lock never throws away unsaved text.
 */
data class EditorState(
    val entryId: Long?,
    val originalText: String,
    val originalDate: LocalDate,
    val originalEventTime: Long,
    val originalValence: Int?,
    val originalCustomMood: String?,
    val originalNotebookId: Long,
    val originalLocked: Boolean,
    val originalAttachments: List<String>,
    val text: String,
    val date: LocalDate,
    val eventTime: Long,
    val valence: Int?,
    val customMood: String?,
    val notebookId: Long,
    val locked: Boolean,
    val attachments: List<String>,
    val merged: Boolean = false,
    val recordedAt: Long? = null,
    val updatedAt: Long? = null,
) {
    val isNew: Boolean get() = entryId == null

    val isDirty: Boolean
        get() = text != originalText ||
                date != originalDate ||
                eventTime != originalEventTime ||
                valence != originalValence ||
                customMood != originalCustomMood ||
                notebookId != originalNotebookId ||
                locked != originalLocked ||
                attachments != originalAttachments

    /** True while the merge banner can still be dismissed without losing typing. */
    val canUndoMerge: Boolean get() = merged && text == originalText
}

data class ReminderSettings(val enabled: Boolean, val hour: Int, val minute: Int)

data class UiMessage(val id: Long, val text: String)

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(
    private val repository: EntryRepository,
    private val auth: AuthManager,
    private val settings: SettingsStore,
    private val appContext: Context,
) : ViewModel() {

    private val moodScaleStore = MoodScaleStore(appContext)

    private val _lockState = MutableStateFlow<LockState>(
        if (auth.hasPassword) LockState.Locked else LockState.NeedsSetup
    )
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    private val _revealedEntries = MutableStateFlow<Set<Long>>(emptySet())
    val revealedEntries: StateFlow<Set<Long>> = _revealedEntries.asStateFlow()

    /** User overrides for day collapsing. Cleared whenever the journal locks. */
    private val _expansion = MutableStateFlow<Map<LocalDate, Boolean>>(emptyMap())
    val expansion: StateFlow<Map<LocalDate, Boolean>> = _expansion.asStateFlow()

    private val _notebooks = MutableStateFlow(NotebookSet.DEFAULT)
    val notebooks: StateFlow<NotebookSet> = _notebooks.asStateFlow()

    private val _moodScale = MutableStateFlow(moodScaleStore.load())
    val moodScale: StateFlow<MoodScale> = _moodScale.asStateFlow()

    private val _revealRecordedTimes = MutableStateFlow(settings.revealRecordedTimes)
    val revealRecordedTimes: StateFlow<Boolean> = _revealRecordedTimes.asStateFlow()

    private val _collapseOldDays = MutableStateFlow(settings.collapseOldDays)
    val collapseOldDays: StateFlow<Boolean> = _collapseOldDays.asStateFlow()

    private val _autoLockMillis = MutableStateFlow(settings.autoLockMillis)
    val autoLockMillis: StateFlow<Long> = _autoLockMillis.asStateFlow()

    private val _placeholder = MutableStateFlow(settings.placeholder)
    val placeholder: StateFlow<String> = _placeholder.asStateFlow()

    private val _defaultNotebookId = MutableStateFlow(settings.defaultNotebookId)
    val defaultNotebookId: StateFlow<Long> = _defaultNotebookId.asStateFlow()

    private val _reminder = MutableStateFlow(
        ReminderSettings(settings.reminderEnabled, settings.reminderHour, settings.reminderMinute)
    )
    val reminder: StateFlow<ReminderSettings> = _reminder.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _transferInProgress = MutableStateFlow(false)
    val transferInProgress: StateFlow<Boolean> = _transferInProgress.asStateFlow()

    private var pendingLock: Job? = null
    private var pendingNewEntry = false

    private val thumbnails = HashMap<String, ImageBitmap>()

    val entries: StateFlow<List<JournalEntry>> = _lockState
        .flatMapLatest { state ->
            if (state == LockState.Unlocked) repository.observeEntries()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stats: StateFlow<JournalStats> = entries
        .map { computeStats(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, JournalStats())

    val moodSeries: StateFlow<List<MoodPoint>> = entries
        .map { computeMoodSeries(it, 30, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val onThisDay: StateFlow<List<OnThisDayHit>> = entries
        .map { computeOnThisDay(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            stats.collect { current ->
                if (_lockState.value == LockState.Unlocked && current.blocks > 0) {
                    JournalWidget.push(appContext, current)
                }
            }
        }
    }

    // --------------------------------------------------------------- locking

    fun setupPassword(password: String, confirm: String) {
        val error = when {
            password.length < MIN_PASSWORD_LENGTH ->
                "Use at least " + MIN_PASSWORD_LENGTH + " characters."
            password != confirm -> "Those passwords don't match."
            else -> null
        }
        if (error != null) {
            _authError.value = error
            return
        }

        _authBusy.value = true
        viewModelScope.launch {
            val created = withContext(Dispatchers.Default) {
                auth.setupPassword(password.toCharArray())
            }
            _authBusy.value = false
            if (!created) {
                _authError.value = "This journal already has a password."
                return@launch
            }
            _authError.value = null
            _lockState.value = LockState.Unlocked
            afterUnlock()
        }
    }

    fun unlock(password: String) {
        _authBusy.value = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                auth.unlock(password.toCharArray())
            }
            _authBusy.value = false
            _authError.value = if (ok) null else "Wrong password."
            if (ok) {
                _lockState.value = LockState.Unlocked
                afterUnlock()
            }
        }
    }

    fun revealEntry(entryId: Long, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                auth.unlock(password.toCharArray())
            }
            if (ok) _revealedEntries.update { it + entryId }
            onResult(ok)
        }
    }

    private fun afterUnlock() {
        if (pendingNewEntry) {
            pendingNewEntry = false
            openNewEntry()
        }
        viewModelScope.launch {
            val set = repository.notebooks()
            _notebooks.value = set
            repository.saveNotebooks(set)
            repository.cleanUpAttachments()
        }
    }

    fun requestNewEntry() {
        if (_lockState.value == LockState.Unlocked) openNewEntry() else pendingNewEntry = true
    }

    fun lock() {
        if (_lockState.value != LockState.Unlocked) return
        pendingLock?.cancel()
        pendingLock = null
        auth.lock()
        _revealedEntries.value = emptySet()
        _expansion.value = emptyMap()
        thumbnails.clear()
        _lockState.value = LockState.Locked
    }

    fun scheduleLock() {
        if (_lockState.value != LockState.Unlocked) return
        val delayMillis = _autoLockMillis.value
        if (delayMillis <= 0L) return
        pendingLock?.cancel()
        pendingLock = viewModelScope.launch {
            delay(delayMillis)
            lock()
        }
    }

    fun cancelPendingLock() {
        pendingLock?.cancel()
        pendingLock = null
    }

    fun toggleDay(date: LocalDate, currentlyExpanded: Boolean) {
        _expansion.update { it + (date to !currentlyExpanded) }
    }

    // ---------------------------------------------------------------- editor

    fun openNewEntry() = openNewEntry(LocalDate.now(), null)

    fun openNewEntry(date: LocalDate, notebookId: Long?) =
        beginNewBlock(date, notebookId, forceNew = false)

    private fun beginNewBlock(date: LocalDate, notebookId: Long?, forceNew: Boolean) {
        val target = notebookId ?: resolvedDefaultNotebookId()

        if (!forceNew && date == LocalDate.now()) {
            val now = System.currentTimeMillis()
            val recent = entries.value
                .filter { it.date == date && !it.locked && it.notebookId == target }
                .maxByOrNull { it.updatedAt }
            if (recent != null && now - recent.updatedAt <= MERGE_WINDOW_MILLIS) {
                post("Continuing your " + recent.eventTime.timeLabel() + " block.")
                openExisting(recent, merged = true)
                return
            }
        }

        val eventTime = date.atTimeOfDay(
            System.currentTimeMillis().hourOfDay(),
            System.currentTimeMillis().minuteOfHour(),
        )
        _editor.value = EditorState(
            entryId = null,
            originalText = "",
            originalDate = date,
            originalEventTime = eventTime,
            originalValence = null,
            originalCustomMood = null,
            originalNotebookId = target,
            originalLocked = false,
            originalAttachments = emptyList(),
            text = "",
            date = date,
            eventTime = eventTime,
            valence = null,
            customMood = null,
            notebookId = target,
            locked = false,
            attachments = emptyList(),
        )
    }

    /** Abandons a merge and starts a genuinely new block instead. */
    fun startNewBlock() {
        val current = _editor.value ?: return
        if (!current.canUndoMerge) return
        beginNewBlock(current.date, current.notebookId, forceNew = true)
    }

    fun openEntry(entry: JournalEntry) = openExisting(entry, merged = false)

    private fun openExisting(entry: JournalEntry, merged: Boolean) {
        _editor.value = EditorState(
            entryId = entry.id,
            originalText = entry.text,
            originalDate = entry.date,
            originalEventTime = entry.eventTime,
            originalValence = entry.valence,
            originalCustomMood = entry.customMood,
            originalNotebookId = entry.notebookId,
            originalLocked = entry.locked,
            originalAttachments = entry.attachments,
            text = entry.text,
            date = entry.date,
            eventTime = entry.eventTime,
            valence = entry.valence,
            customMood = entry.customMood,
            notebookId = entry.notebookId,
            locked = entry.locked,
            attachments = entry.attachments,
            merged = merged,
            recordedAt = entry.recordedAt,
            updatedAt = entry.updatedAt,
        )
    }

    fun updateText(text: String) {
        _editor.update { it?.copy(text = text) }
    }

    fun setDate(date: LocalDate) {
        _editor.update { current ->
            if (current == null) null
            else current.copy(
                date = date,
                eventTime = date.atTimeOfDay(
                    current.eventTime.hourOfDay(),
                    current.eventTime.minuteOfHour(),
                ),
            )
        }
    }

    fun setTime(hour: Int, minute: Int) {
        _editor.update { current ->
            current?.copy(eventTime = current.date.atTimeOfDay(hour, minute))
        }
    }

    fun setValence(valence: Int?) {
        _editor.update { it?.copy(valence = valence, customMood = null) }
    }

    fun setCustomMood(emoji: String?) {
        _editor.update { it?.copy(customMood = emoji, valence = null) }
    }

    fun setDraftNotebook(notebookId: Long) {
        _editor.update { it?.copy(notebookId = notebookId) }
    }

    fun toggleDraftLock() {
        _editor.update { it?.copy(locked = !it.locked) }
    }

    fun removePhoto(name: String) {
        _editor.update { it?.copy(attachments = it.attachments - name) }
    }

    fun closeEditor() {
        _editor.value = null
    }

    fun saveDraft() {
        val draft = _editor.value ?: return
        if (draft.text.isBlank()) return
        viewModelScope.launch {
            repository.save(
                id = draft.entryId,
                date = draft.date,
                eventTime = draft.eventTime,
                text = draft.text.trim(),
                valence = draft.valence,
                customMood = draft.customMood,
                notebookId = draft.notebookId,
                locked = draft.locked,
                attachmentNames = draft.attachments,
            )
            _editor.value = null
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            if (_editor.value?.entryId == id) _editor.value = null
        }
    }

    // ---------------------------------------------------------------- photos

    fun addPhotoFromUri(uri: Uri) {
        ingestPhoto {
            withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        }
    }

    fun addPhotoFromFile(file: File) {
        ingestPhoto {
            withContext(Dispatchers.IO) {
                val bytes = file.takeIf { it.isFile }?.readBytes()
                file.delete()
                bytes
            }
        }
    }

    private fun ingestPhoto(loader: suspend () -> ByteArray?) {
        if (_editor.value == null) return
        viewModelScope.launch {
            val raw = loader()
            if (raw == null) {
                post("That picture could not be read.")
                return@launch
            }
            val compressed = withContext(Dispatchers.Default) {
                ImageCompressor.fromBytes(raw)
            }
            if (compressed == null) {
                post("That file doesn't look like a picture.")
                return@launch
            }
            val name = repository.addAttachment(compressed)
            if (name == null) {
                post("Could not save the picture.")
                return@launch
            }
            _editor.update { it?.copy(attachments = it.attachments + name) }
        }
    }

    suspend fun thumbnailFor(name: String): ImageBitmap? {
        thumbnails[name]?.let { return it }
        val bytes = repository.loadAttachment(name) ?: return null
        val bitmap = withContext(Dispatchers.Default) { decodeSampled(bytes, 220) }
        if (bitmap != null) {
            if (thumbnails.size > 64) thumbnails.clear()
            thumbnails[name] = bitmap
        }
        return bitmap
    }

    suspend fun fullImageFor(name: String): ImageBitmap? {
        val bytes = repository.loadAttachment(name) ?: return null
        return withContext(Dispatchers.Default) { decodeSampled(bytes, 2048) }
    }

    // ------------------------------------------------------------- notebooks

    private fun resolvedDefaultNotebookId(): Long {
        val set = _notebooks.value
        val configured = _defaultNotebookId.value
        return if (set.byId(configured) != null) configured
        else set.firstId ?: NotebookSet.DEFAULT_ID
    }

    fun createNotebook(name: String, select: Boolean = false) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = _notebooks.value
            if (current.notebooks.any { it.name.equals(trimmed, ignoreCase = true) }) {
                post("You already have a notebook called \"" + trimmed + "\".")
                return@launch
            }
            val nextId = (current.notebooks.maxOfOrNull { it.id } ?: 0L) + 1L
            val updated = NotebookSet(
                current.notebooks +
                    Notebook(nextId, trimmed, pinned = false, order = current.notebooks.size)
            )
            repository.saveNotebooks(updated)
            _notebooks.value = updated
            if (select) _editor.update { it?.copy(notebookId = nextId) }
        }
    }

    fun renameNotebook(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = _notebooks.value
            if (current.notebooks.any {
                    it.id != id && it.name.equals(trimmed, ignoreCase = true)
                }
            ) {
                post("Another notebook is already called \"" + trimmed + "\".")
                return@launch
            }
            val updated = NotebookSet(
                current.notebooks.map {
                    if (it.id == id) it.copy(name = trimmed) else it
                }
            )
            repository.saveNotebooks(updated)
            _notebooks.value = updated
        }
    }

    fun toggleNotebookPin(id: Long) {
        viewModelScope.launch {
            val current = _notebooks.value
            val updated = NotebookSet(
                current.notebooks.map {
                    if (it.id == id) it.copy(pinned = !it.pinned) else it
                }
            )
            repository.saveNotebooks(updated)
            _notebooks.value = updated
        }
    }

    fun deleteNotebook(id: Long) {
        viewModelScope.launch {
            val current = _notebooks.value
            if (current.notebooks.size <= 1) {
                post("Keep at least one notebook.")
                return@launch
            }
            val remaining = current.notebooks.filterNot { it.id == id }
            val fallback = NotebookSet(remaining).displayOrder.first().id
            val moved = repository.reassignNotebook(id, fallback)
            val updated = NotebookSet(remaining)
            repository.saveNotebooks(updated)
            _notebooks.value = updated

            if (_defaultNotebookId.value == id) setDefaultNotebook(fallback)
            if (_editor.value?.notebookId == id) {
                _editor.update { it?.copy(notebookId = fallback) }
            }
            post(
                if (moved == 0) "Notebook removed."
                else "Notebook removed and " + moved + " block(s) moved."
            )
        }
    }

    // -------------------------------------------------------------- settings

    fun setRevealRecordedTimes(value: Boolean) {
        settings.revealRecordedTimes = value
        _revealRecordedTimes.value = value
    }

    fun setCollapseOldDays(value: Boolean) {
        settings.collapseOldDays = value
        _collapseOldDays.value = value
    }

    fun setAutoLock(millis: Long) {
        settings.autoLockMillis = millis
        _autoLockMillis.value = millis
        if (millis > 0L && pendingLock != null) scheduleLock()
    }

    fun setPlaceholder(value: String) {
        settings.placeholder = value
        _placeholder.value = value
    }

    fun setDefaultNotebook(id: Long) {
        settings.defaultNotebookId = id
        _defaultNotebookId.value = id
    }

    fun setMoodEmoji(index: Int, emoji: String) {
        val updated = _moodScale.value.withEmoji(index, emoji)
        moodScaleStore.save(updated)
        _moodScale.value = updated
    }

    fun setMoodLabel(index: Int, label: String) {
        val updated = _moodScale.value.withLabel(index, label)
        moodScaleStore.save(updated)
        _moodScale.value = updated
    }

    fun resetMoodScale() {
        moodScaleStore.reset()
        _moodScale.value = MoodScale.DEFAULT
        post("Mood scale reset.")
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        settings.reminderEnabled = enabled
        settings.reminderHour = hour
        settings.reminderMinute = minute
        _reminder.value = ReminderSettings(enabled, hour, minute)

        if (enabled) {
            ReminderScheduler.schedule(appContext, hour, minute)
            post("Daily reminder set for " + "%02d:%02d".format(hour, minute) + ".")
        } else {
            ReminderScheduler.cancel(appContext)
            post("Daily reminder turned off.")
        }
    }

    fun changePassword(current: String, new: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = when {
                new.length < MIN_PASSWORD_LENGTH ->
                    "New password must be at least " + MIN_PASSWORD_LENGTH + " characters."
                withContext(Dispatchers.Default) {
                    repository.changePassword(current.toCharArray(), new.toCharArray())
                } -> null
                else -> "Current password is incorrect."
            }
            onResult(result)
        }
    }

    // ---------------------------------------------------------------- backup

    fun exportTo(uri: Uri) {
        _transferInProgress.value = true
        viewModelScope.launch {
            val result = runCatching { repository.exportTo(uri) }
            _transferInProgress.value = false
            post(
                result.fold(
                    onSuccess = { count ->
                        if (count == 0) "Nothing to export yet."
                        else "Exported " + count + " " + blocks(count) + "."
                    },
                    onFailure = { "Export failed: " + (it.message ?: "unknown error") },
                )
            )
        }
    }

    fun importFrom(uri: Uri, password: String) {
        if (password.isBlank()) {
            post("Enter the password for that backup.")
            return
        }
        _transferInProgress.value = true
        viewModelScope.launch {
            val result = runCatching {
                repository.importFrom(uri, password.toCharArray())
            }
            _transferInProgress.value = false
            _notebooks.value = repository.notebooks()
            post(
                result.fold(
                    onSuccess = { count ->
                        if (count == 0) "That backup had no blocks in it."
                        else "Imported " + count + " " + blocks(count) + "."
                    },
                    onFailure = { it.message ?: "Import failed." },
                )
            )
        }
    }

    // -------------------------------------------------------------- messages

    fun clearMessage() {
        _message.value = null
    }

    private fun post(text: String) {
        _message.value = UiMessage(System.nanoTime(), text)
    }

    private fun blocks(count: Int) = if (count == 1) "block" else "blocks"

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
        const val MERGE_WINDOW_MILLIS = 30 * 60 * 1000L
    }
}

class JournalViewModelFactory(
    private val app: JournalApplication,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(JournalViewModel::class.java)) {
            return JournalViewModel(
                repository = app.repository,
                auth = app.authManager,
                settings = app.settingsStore,
                appContext = app.applicationContext,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: " + modelClass.name)
    }
}
