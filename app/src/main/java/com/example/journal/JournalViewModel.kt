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
import com.example.journal.data.Task
import com.example.journal.ui.JournalStats
import com.example.journal.ui.MoodPoint
import com.example.journal.ui.OnThisDayHit
import com.example.journal.ui.computeMoodSeries
import com.example.journal.ui.computeOnThisDay
import com.example.journal.ui.computeStats
import com.example.journal.ui.decodeSampled
import com.example.journal.ui.withCurrentTimeOfDay
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

data class DocumentSection(
    val id: Long,
    val eventTime: Long,
    val text: String,
    val valence: Int?,
    val customMood: String?,
    val locked: Boolean,
    val attachments: List<String>,
    val recordedAt: Long?,
    val updatedAt: Long?,
)

data class DayDocument(
    val date: LocalDate,
    val notebookId: Long,
    val sections: List<DocumentSection>,
)

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

    private val _document = MutableStateFlow<DayDocument?>(null)
    val document: StateFlow<DayDocument?> = _document.asStateFlow()

    private val _notebooks = MutableStateFlow(NotebookSet.DEFAULT)
    val notebooks: StateFlow<NotebookSet> = _notebooks.asStateFlow()

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

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
        ReminderSettings(
            settings.reminderEnabled,
            settings.reminderHour,
            settings.reminderMinute,
        )
    )
    val reminder: StateFlow<ReminderSettings> = _reminder.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _transferInProgress = MutableStateFlow(false)
    val transferInProgress: StateFlow<Boolean> = _transferInProgress.asStateFlow()

    private var pendingLock: Job? = null
    private var autosaveJob: Job? = null
    private var documentDirty = false
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

    // ------------------------------------------------------------- locking

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

    private fun afterUnlock() {
        if (pendingNewEntry) {
            pendingNewEntry = false
            openToday()
        }
        viewModelScope.launch {
            val set = repository.notebooks()
            _notebooks.value = set
            repository.saveNotebooks(set)
            _tasks.value = repository.tasks()
            repository.cleanUpAttachments()
        }
    }

    fun requestNewEntry() {
        if (_lockState.value == LockState.Unlocked) openToday() else pendingNewEntry = true
    }

    fun lock() {
        if (_lockState.value != LockState.Unlocked) return
        viewModelScope.launch { flushDocument() }
        pendingLock?.cancel()
        pendingLock = null
        autosaveJob?.cancel()
        auth.lock()
        thumbnails.clear()
        _document.value = null
        _tasks.value = emptyList()
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

    // --------------------------------------------------------- day documents

    fun openToday() {
        val set = _notebooks.value
        val configured = _defaultNotebookId.value
        val notebookId = if (set.byId(configured) != null) configured
        else set.firstId ?: NotebookSet.DEFAULT_ID
        openDay(LocalDate.now(), notebookId)
    }

    fun openDay(date: LocalDate, notebookId: Long) {
        viewModelScope.launch {
            val blocks = repository.blocksForDay(date)
                .filter { it.notebookId == notebookId }
                .map { it.toSection() }
                .toMutableList()

            val now = System.currentTimeMillis()
            val lastTouched = blocks.maxOfOrNull { it.updatedAt ?: 0L } ?: 0L
            val stale = blocks.isEmpty() || now - lastTouched > MERGE_WINDOW_MILLIS

            if (stale) {
                blocks += DocumentSection(
                    id = 0L,
                    eventTime = if (date == LocalDate.now()) now
                    else date.withCurrentTimeOfDay(),
                    text = "",
                    valence = null,
                    customMood = null,
                    locked = false,
                    attachments = emptyList(),
                    recordedAt = null,
                    updatedAt = null,
                )
            }

            documentDirty = false
            _document.value = DayDocument(date, notebookId, blocks)
        }
    }

    fun switchDocumentNotebook(notebookId: Long) {
        val current = _document.value ?: return
        if (current.notebookId == notebookId) return
        viewModelScope.launch {
            flushDocument()
            openDay(current.date, notebookId)
        }
    }

    fun closeDocument() {
        viewModelScope.launch {
            flushDocument()
            _document.value = null
        }
    }

    fun updateSectionText(index: Int, text: String) {
        mutateSection(index) { it.copy(text = text) }
    }

    fun setSectionValence(index: Int, valence: Int?) {
        mutateSection(index) { it.copy(valence = valence, customMood = null) }
    }

    fun setSectionCustomMood(index: Int, emoji: String?) {
        mutateSection(index) { it.copy(customMood = emoji, valence = null) }
    }

    fun toggleSectionLock(index: Int) {
        mutateSection(index) { it.copy(locked = !it.locked) }
    }

    fun addSection() {
        val current = _document.value ?: return
        val stamp = if (current.date == LocalDate.now()) System.currentTimeMillis()
        else current.date.withCurrentTimeOfDay()
        val added = current.sections + DocumentSection(
            id = 0L,
            eventTime = stamp,
            text = "",
            valence = null,
            customMood = null,
            locked = false,
            attachments = emptyList(),
            recordedAt = null,
            updatedAt = null,
        )
        _document.value = current.copy(sections = added)
        documentDirty = true
        scheduleAutosave()
    }

    fun deleteSection(index: Int) {
        val current = _document.value ?: return
        val section = current.sections.getOrNull(index) ?: return
        _document.value = current.copy(
            sections = current.sections.filterIndexed { i, _ -> i != index }
        )
        viewModelScope.launch {
            if (section.id != 0L) repository.delete(section.id)
        }
    }

    fun moveSection(index: Int, date: LocalDate) {
        val current = _document.value ?: return
        val section = current.sections.getOrNull(index) ?: return
        viewModelScope.launch {
            flushDocument()
            if (section.id != 0L) {
                repository.save(
                    id = section.id,
                    date = date,
                    eventTime = date.withCurrentTimeOfDay(),
                    text = section.text,
                    valence = section.valence,
                    customMood = section.customMood,
                    notebookId = current.notebookId,
                    locked = section.locked,
                    attachmentNames = section.attachments,
                )
                repository.delete(section.id)
                repository.save(
                    id = null,
                    date = date,
                    eventTime = date.withCurrentTimeOfDay(),
                    text = section.text,
                    valence = section.valence,
                    customMood = section.customMood,
                    notebookId = current.notebookId,
                    locked = section.locked,
                    attachmentNames = section.attachments,
                )
            }
            openDay(current.date, current.notebookId)
            post("Moved to " + date.shortLabel() + ".")
        }
    }

    private fun mutateSection(index: Int, transform: (DocumentSection) -> DocumentSection) {
        val current = _document.value ?: return
        val updated = current.sections.mapIndexed { i, section ->
            if (i == index) transform(section) else section
        }
        _document.value = current.copy(sections = updated)
        documentDirty = true
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MILLIS)
            flushDocument()
        }
    }

    private suspend fun flushDocument() {
        val current = _document.value ?: return
        if (!documentDirty) return
        documentDirty = false

        val now = System.currentTimeMillis()
        val kept = mutableListOf<DocumentSection>()

        for (section in current.sections) {
            if (section.text.isBlank()) {
                if (section.id != 0L) repository.delete(section.id)
                continue
            }
            if (section.id == 0L) {
                val newId = repository.save(
                    id = null,
                    date = current.date,
                    eventTime = section.eventTime,
                    text = section.text,
                    valence = section.valence,
                    customMood = section.customMood,
                    notebookId = current.notebookId,
                    locked = section.locked,
                    attachmentNames = section.attachments,
                )
                kept += section.copy(id = newId, recordedAt = now, updatedAt = now)
            } else {
                repository.save(
                    id = section.id,
                    date = current.date,
                    eventTime = section.eventTime,
                    text = section.text,
                    valence = section.valence,
                    customMood = section.customMood,
                    notebookId = current.notebookId,
                    locked = section.locked,
                    attachmentNames = section.attachments,
                )
                kept += section
            }
        }

        _document.value = current.copy(sections = kept)
    }

    private fun JournalEntry.toSection() = DocumentSection(
        id = id,
        eventTime = eventTime,
        text = text,
        valence = valence,
        customMood = customMood,
        locked = locked,
        attachments = attachments,
        recordedAt = recordedAt,
        updatedAt = updatedAt,
    )

    // ---------------------------------------------------------------- photos

    fun addPhotoToSection(index: Int, uri: Uri) {
        ingestPhoto(index) {
            withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        }
    }

    fun addPhotoFileToSection(index: Int, file: File) {
        ingestPhoto(index) {
            withContext(Dispatchers.IO) {
                val bytes = file.takeIf { it.isFile }?.readBytes()
                file.delete()
                bytes
            }
        }
    }

    private fun ingestPhoto(index: Int, loader: suspend () -> ByteArray?) {
        if (_document.value == null) return
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
            mutateSection(index) { it.copy(attachments = it.attachments + name) }
        }
    }

    fun removePhotoFromSection(index: Int, name: String) {
        mutateSection(index) { it.copy(attachments = it.attachments - name) }
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
                current.notebooks + Notebook(
                    id = nextId,
                    name = trimmed,
                    pinned = false,
                    order = current.notebooks.size,
                )
            )
            repository.saveNotebooks(updated)
            _notebooks.value = updated
            if (select) switchDocumentNotebook(nextId)
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

            val newTasks = _tasks.value.map {
                if (it.notebookId == id) it.copy(notebookId = fallback) else it
            }
            _tasks.value = newTasks
            repository.saveTasks(newTasks)

            if (_defaultNotebookId.value == id) setDefaultNotebook(fallback)
            if (_document.value?.notebookId == id) {
                _document.value = _document.value?.copy(notebookId = fallback)
            }
            post(
                if (moved == 0) "Notebook removed."
                else "Notebook removed and " + moved + " section(s) moved."
            )
        }
    }

    // ------------------------------------------------------------------ tasks

    fun addTask(
        title: String,
        note: String,
        dueDate: LocalDate?,
        dueTimeMinutes: Int?,
        notebookId: Long,
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val nextId = (_tasks.value.maxOfOrNull { it.id } ?: 0L) + 1L
            val updated = _tasks.value + Task(
                id = nextId,
                title = trimmed,
                done = false,
                completedAt = null,
                dueDate = dueDate,
                dueTimeMinutes = dueTimeMinutes,
                note = note,
                notebookId = notebookId,
                createdAt = now,
                updatedAt = now,
            )
            _tasks.value = updated
            repository.saveTasks(updated)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            val updated = _tasks.value.map { if (it.id == task.id) task else it }
            _tasks.value = updated
            repository.saveTasks(updated)
        }
    }

    fun toggleTask(id: Long) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = _tasks.value.map { task ->
                if (task.id != id) task
                else if (task.done) task.copy(done = false, completedAt = null, updatedAt = now)
                else task.copy(done = true, completedAt = now, updatedAt = now)
            }
            _tasks.value = updated
            repository.saveTasks(updated)
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            val updated = _tasks.value.filterNot { it.id == id }
            _tasks.value = updated
            repository.saveTasks(updated)
        }
    }

    // --------------------------------------------------------------- settings

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

    // ----------------------------------------------------------------- backup

    fun exportTo(uri: Uri) {
        _transferInProgress.value = true
        viewModelScope.launch {
            flushDocument()
            val result = runCatching { repository.exportTo(uri) }
            _transferInProgress.value = false
            post(
                result.fold(
                    onSuccess = { count ->
                        if (count == 0) "Nothing to export yet."
                        else "Exported " + count + " " + sections(count) + "."
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
            _tasks.value = repository.tasks()
            post(
                result.fold(
                    onSuccess = { count ->
                        if (count == 0) "That backup had no sections in it."
                        else "Imported " + count + " " + sections(count) + "."
                    },
                    onFailure = { it.message ?: "Import failed." },
                )
            )
        }
    }

    // --------------------------------------------------------------- messages

    fun clearMessage() {
        _message.value = null
    }

    private fun post(text: String) {
        _message.value = UiMessage(System.nanoTime(), text)
    }

    private fun sections(count: Int) = if (count == 1) "section" else "sections"

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
        const val MERGE_WINDOW_MILLIS = 30 * 60 * 1000L
        const val AUTOSAVE_DEBOUNCE_MILLIS = 1200L
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
