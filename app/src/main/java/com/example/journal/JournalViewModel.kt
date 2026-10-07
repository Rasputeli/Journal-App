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
import com.example.journal.data.SettingsStore
import com.example.journal.ui.JournalStats
import com.example.journal.ui.OnThisDayHit
import com.example.journal.ui.computeOnThisDay
import com.example.journal.ui.computeStats
import com.example.journal.ui.decodeSampled
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
 * The entry open in the editor. It lives here rather than in the composable
 * so a background auto-lock never throws away unsaved text.
 */
data class EditorState(
    val entryId: Long?,
    val originalText: String,
    val originalDate: LocalDate,
    val originalMood: String?,
    val originalLocked: Boolean,
    val originalAttachments: List<String>,
    val text: String,
    val date: LocalDate,
    val mood: String?,
    val locked: Boolean,
    val attachments: List<String>,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
) {
    val isNew: Boolean get() = entryId == null
    val isDirty: Boolean
        get() = text != originalText ||
                date != originalDate ||
                mood != originalMood ||
                locked != originalLocked ||
                attachments != originalAttachments
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

    private val _revealRecordedTimes = MutableStateFlow(settings.revealRecordedTimes)
    val revealRecordedTimes: StateFlow<Boolean> = _revealRecordedTimes.asStateFlow()

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

    /** Small decoded thumbnails, most recently used last so they can evict. */
    private val thumbnails = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) =
            size > 64
    }

    val entries: StateFlow<List<JournalEntry>> = _lockState
        .flatMapLatest { state ->
            if (state == LockState.Unlocked) repository.observeEntries() else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stats: StateFlow<JournalStats> = entries
        .map { computeStats(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, JournalStats())

    val onThisDay: StateFlow<List<OnThisDayHit>> = entries
        .map { computeOnThisDay(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            stats.collect { current ->
                if (_lockState.value == LockState.Unlocked && current.entries > 0) {
                    JournalWidget.push(appContext, current)
                }
            }
        }
    }

    // ------------------------------------------------------------ locking

    fun setupPassword(password: String, confirm: String) {
        val error = when {
            password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters."
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

    /** Cheap re-check used by the per-entry lock. Does not change lock state. */
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
        viewModelScope.launch { repository.cleanUpAttachments() }
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
        thumbnails.clear()
        _lockState.value = LockState.Locked
    }

    fun scheduleLock(delayMillis: Long = AUTO_LOCK_DELAY_MILLIS) {
        if (_lockState.value != LockState.Unlocked) return
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

    // ------------------------------------------------------------- editor

    fun openNewEntry(date: LocalDate = LocalDate.now()) {
        _editor.value = EditorState(
            entryId = null,
            originalText = "",
            originalDate = date,
            originalMood = null,
            originalLocked = false,
            originalAttachments = emptyList(),
            text = "",
            date = date,
            mood = null,
            locked = false,
            attachments = emptyList(),
        )
    }

    fun openEntry(entry: JournalEntry) {
        _editor.value = EditorState(
            entryId = entry.id,
            originalText = entry.text,
            originalDate = entry.date,
            originalMood = entry.mood,
            originalLocked = entry.locked,
            originalAttachments = entry.attachments,
            text = entry.text,
            date = entry.date,
            mood = entry.mood,
            locked = entry.locked,
            attachments = entry.attachments,
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
        )
    }

    fun updateDraft(text: String? = null, date: LocalDate? = null) {
        _editor.update { current ->
            if (current == null) null
            else current.copy(text = text ?: current.text, date = date ?: current.date)
        }
    }

    fun setMood(mood: String?) {
        _editor.update { it?.copy(mood = mood) }
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
                text = draft.text.trim(),
                mood = draft.mood,
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

    // --------------------------------------------------------------- photos

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
            val raw = loader() ?: run {
                postMessage("That picture could not be read.")
                return@launch
            }
            val compressed = withContext(Dispatchers.Default) {
                ImageCompressor.fromBytes(raw)
            } ?: run {
                postMessage("That file doesn't look like a picture.")
                return@launch
            }
            val name = repository.addAttachment(compressed)
            if (name == null) {
                postMessage("Could not save the picture.")
                return@launch
            }
            _editor.update { it?.copy(attachments = it.attachments + name) }
        }
    }

    suspend fun thumbnailFor(name: String): ImageBitmap? {
        thumbnails[name]?.let { return it }
        val bytes = repository.loadAttachment(name) ?: return null
        val bitmap = withContext(Dispatchers.Default) { decodeSampled(bytes, 220) }
        if (bitmap != null) thumbnails[name] = bitmap
        return bitmap
    }

    suspend fun fullImageFor(name: String): ImageBitmap? {
        val bytes = repository.loadAttachment(name) ?: return null
        return withContext(Dispatchers.Default) { decodeSampled(bytes, 2048) }
    }

    // ------------------------------------------------------------- settings

    fun toggleRevealRecordedTimes() {
        val next = !_revealRecordedTimes.value
        _revealRecordedTimes.value = next
        settings.revealRecordedTimes = next
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        settings.reminderEnabled = enabled
        settings.reminderHour = hour
        settings.reminderMinute = minute
        _reminder.value = ReminderSettings(enabled, hour, minute)

        if (enabled) {
            ReminderScheduler.schedule(appContext, hour, minute)
            postMessage("Daily reminder set for %02d:%02d.".format(hour, minute))
        } else {
            ReminderScheduler.cancel(appContext)
            postMessage("Daily reminder turned off.")
        }
    }

    fun changePassword(current: String, new: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = when {
                new.length < MIN_PASSWORD_LENGTH ->
                    "New password must be at least $MIN_PASSWORD_LENGTH characters."
                withContext(Dispatchers.Default) {
                    repository.changePassword(current.toCharArray(), new.toCharArray())
                } -> null
                else -> "Current password is incorrect."
            }
            onResult(error)
        }
    }

    // --------------------------------------------------------------- backup

    fun exportTo(uri: Uri) {
        _transferInProgress.value = true
        viewModelScope.launch {
            val result = runCatching { repository.exportTo(uri) }
            _transferInProgress.value = false
            postMessage(
                result.fold(
                    onSuccess = { count ->
                        if (count == 0) "Nothing to export yet."
                        else "Exported $count ${plural(count)}."
                    },
                    onFailure = { "Export failed: ${it.message ?: "unknown error"}" },
                )
            )
        }
    }

    fun importFrom(uri: Uri, password: String) {
        if (password.isBlank()) {
            postMessage("Enter the password for that backup.")
            return
        }
        _transferInProgress.value = true
        viewModelScope.launch {
            val result = runCatching { repository.importFrom(uri, password.toCharArray()) }
            _transferInProgress.value = false
            postMessage(
                result.fold(
                    onSuccess = { count ->
                        if (count == 0) "That backup had no entries in it."
                        else "Imported $count ${plural(count)}."
                    },
                    onFailure = { it.message ?: "Import failed." },
                )
            )
        }
    }

    // ------------------------------------------------------------- messages

    fun clearMessage() {
        _message.value = null
    }

    private fun postMessage(text: String) {
        _message.value = UiMessage(System.nanoTime(), text)
    }

    private fun plural(count: Int) = if (count == 1) "entry" else "entries"

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
        const val AUTO_LOCK_DELAY_MILLIS = 2 * 60 * 1000L
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
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
