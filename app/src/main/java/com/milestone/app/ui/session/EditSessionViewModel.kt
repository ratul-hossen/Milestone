package com.milestone.app.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milestone.app.MilestoneApp
import com.milestone.app.data.db.SessionEntity
import com.milestone.app.data.db.SubjectEntity
import com.milestone.app.ml.DurationSuggester
import com.milestone.app.ui.UiMessage
import com.milestone.app.ui.appViewModel
import com.milestone.app.util.AppLog
import com.milestone.app.util.formatMinutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

data class EditSessionState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val subjects: List<SubjectEntity> = emptyList(),
    val subjectId: Long? = null,
    val duration: String = "",
    val date: LocalDate = LocalDate.now(),
    val notes: String = "",
    val subjectError: String? = null,
    val durationError: String? = null,
    val dateError: String? = null,
    val suggestion: DurationSuggester.Suggestion? = null,
    val saved: Boolean = false,
) {
    val errorCount get() = listOfNotNull(subjectError, durationError, dateError).size
}

class EditSessionViewModel(private val app: MilestoneApp, handle: SavedStateHandle) : ViewModel() {
    private val sessionId: Long = handle.get<Long>(ARG_SESSION) ?: -1L
    private val presetSubject: Long = handle.get<Long>(ARG_SUBJECT) ?: -1L
    private var original: SessionEntity? = null
    private var allSessions: List<SessionEntity> = emptyList()

    private val _state = MutableStateFlow(EditSessionState())
    val state: StateFlow<EditSessionState> = _state

    init {
        viewModelScope.launch {
            val subjects = app.repository.subjects.first()
            allSessions = app.repository.sessions.first()
            val existing = if (sessionId > 0) app.repository.getSession(sessionId) else null
            original = existing
            if (existing != null) {
                _state.value = EditSessionState(
                    loading = false, isEdit = true, subjects = subjects, subjectId = existing.subjectId,
                    duration = existing.durationMinutes.toString(), date = LocalDate.ofEpochDay(existing.dateEpochDay),
                    notes = existing.notes,
                )
            } else {
                // No guessing here: a wrong preselected subject leads to mis-logged time. Only an explicit
                // choice (tapped subject) or the only subject is preselected.
                val subject = presetSubject.takeIf { id -> subjects.any { it.id == id } } ?: subjects.singleOrNull()?.id
                val suggestion = withContext(Dispatchers.Default) { suggestFor(subject) }
                // The suggestion is shown as a highlighted chip but not pre-filled: a guess should never
                // become data just because the student tapped Save.
                _state.value = EditSessionState(loading = false, subjects = subjects, subjectId = subject, suggestion = suggestion)
            }
        }
    }

    private fun suggestFor(subjectId: Long?) =
        app.insights.duration.suggest(allSessions, subjectId, LocalTime.now().hour, LocalDate.now())

    fun selectSubject(id: Long) = _state.update { s ->
        s.copy(subjectId = id, subjectError = null, suggestion = if (s.isEdit) s.suggestion else suggestFor(id))
    }

    fun setDuration(text: String) = _state.update { it.copy(duration = text.filter(Char::isDigit).take(3), durationError = null) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date, dateError = null) }
    fun setNotes(text: String) = _state.update { it.copy(notes = text.take(MAX_NOTES)) }

    private var saving = false

    fun save() = viewModelScope.launch {
        if (saving || _state.value.saved) return@launch   // ignore double taps
        saving = true
        try { doSave() } finally { saving = false }
    }

    private suspend fun doSave() {
        val s = _state.value
        val minutes = s.duration.toIntOrNull()
        val checked = s.copy(
            subjectError = if (s.subjectId == null) "Please choose a subject" else null,
            durationError = if (minutes == null || minutes !in 1..600) "Enter a number between 1 and 600" else null,
            dateError = if (s.date.isAfter(LocalDate.now())) "You can't log a session in the future" else null,
        )
        if (checked.errorCount > 0) {
            _state.value = checked
            AppLog.d(AppLog.UI, "Session form invalid: ${checked.errorCount} field(s)")
            app.messages.show(UiMessage("Fix ${checked.errorCount} field${if (checked.errorCount > 1) "s" else ""} to save this session", isError = true))
            return
        }
        val base = original
        val entity = base?.copy(
            subjectId = s.subjectId!!, durationMinutes = minutes!!, dateEpochDay = s.date.toEpochDay(), notes = s.notes.trim(),
        ) ?: SessionEntity(
            subjectId = s.subjectId!!, durationMinutes = minutes!!, dateEpochDay = s.date.toEpochDay(),
            // Logged "now": the session most likely just ended, so estimate its start hour.
            startHour = LocalTime.now().minusMinutes(minutes.toLong()).hour,
            notes = s.notes.trim(),
        )
        if (base == null && s.suggestion?.fromHistory == true) {
            AppLog.i(AppLog.ML, "Duration suggestion ${s.suggestion.minutes}min, user saved ${minutes}min " +
                "(abs error ${kotlin.math.abs(s.suggestion.minutes - minutes)}min)")
        }
        app.repository.saveSession(entity)
        val name = s.subjects.firstOrNull { it.id == s.subjectId }?.name.orEmpty()
        app.messages.show(UiMessage(if (base == null) "Logged ${formatMinutes(minutes)} of $name" else "Session updated"))
        _state.update { it.copy(saved = true) }
    }

    companion object {
        const val ARG_SESSION = "sessionId"
        const val ARG_SUBJECT = "subjectId"
        const val MAX_NOTES = 200
        val Factory = appViewModel { app, handle -> EditSessionViewModel(app, handle) }
    }
}
