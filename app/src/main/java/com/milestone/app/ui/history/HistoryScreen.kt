package com.milestone.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milestone.app.MilestoneApp
import com.milestone.app.R
import com.milestone.app.data.db.SessionEntity
import com.milestone.app.data.db.SubjectEntity
import com.milestone.app.ui.UiMessage
import com.milestone.app.ui.appViewModel
import com.milestone.app.ui.components.EmptyState
import com.milestone.app.ui.components.FilterChip
import com.milestone.app.ui.components.SectionLabel
import com.milestone.app.ui.components.SessionCard
import com.milestone.app.ui.components.TopBar
import com.milestone.app.ui.theme.BodyMedium
import com.milestone.app.ui.theme.Error
import com.milestone.app.ui.theme.ErrorContainer
import com.milestone.app.ui.theme.LabelLarge
import com.milestone.app.ui.theme.LabelMedium
import com.milestone.app.ui.theme.OnSurface
import com.milestone.app.ui.theme.OnSurfaceVariant
import com.milestone.app.ui.theme.OutlineVariant
import com.milestone.app.ui.theme.Primary
import com.milestone.app.ui.theme.subjectColor
import com.milestone.app.ui.todayFlow
import com.milestone.app.util.dayHeader
import com.milestone.app.util.formatMinutes
import com.milestone.app.util.label
import com.milestone.app.util.rangeLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HistoryRange(val label: String) {
    LAST_7("Last 7 days"), LAST_30("Last 30 days"), THIS_MONTH("This month"), ALL("All time");

    fun start(today: LocalDate): LocalDate? = when (this) {
        LAST_7 -> today.minusDays(6)
        LAST_30 -> today.minusDays(29)
        THIS_MONTH -> today.withDayOfMonth(1)
        ALL -> null
    }
}

data class DayGroup(val date: LocalDate, val sessions: List<SessionEntity>)

data class HistoryState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val subjects: List<SubjectEntity> = emptyList(),
    val filter: Long? = null,
    val range: HistoryRange = HistoryRange.LAST_7,
    val groups: List<DayGroup> = emptyList(),
    val count: Int = 0,
    val total: Int = 0,
    val hasAnySessions: Boolean = false,
)

class HistoryViewModel(private val app: MilestoneApp, presetSubject: Long?) : ViewModel() {
    private val filter = MutableStateFlow(presetSubject)
    // A subject page shows everything for that subject; the History tab starts with the last 7 days.
    private val range = MutableStateFlow(if (presetSubject != null) HistoryRange.ALL else HistoryRange.LAST_7)

    val state = combine(app.repository.subjects, app.repository.sessions, filter, range, todayFlow()) { subjects, sessions, f, r, today ->
        val start = r.start(today)?.toEpochDay() ?: Long.MIN_VALUE
        val filterId = f?.takeIf { id -> subjects.any { it.id == id } }
        val list = sessions.filter { it.dateEpochDay >= start && (filterId == null || it.subjectId == filterId) }
        HistoryState(
            loading = false, today = today, subjects = subjects, filter = filterId, range = r,
            groups = list.groupBy { it.dateEpochDay }.map { (d, s) -> DayGroup(LocalDate.ofEpochDay(d), s) },
            count = list.size, total = list.sumOf { it.durationMinutes }, hasAnySessions = sessions.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun setFilter(id: Long?) { filter.value = id }
    fun setRange(r: HistoryRange) { range.value = r }

    fun delete(session: SessionEntity, subjectName: String) = viewModelScope.launch {
        app.repository.deleteSession(session)
        app.messages.show(UiMessage("$subjectName session deleted", "Undo") { app.repository.restoreSession(session) })
    }

    companion object {
        val Factory = appViewModel { app, handle ->
            HistoryViewModel(app, handle.get<Long>("subjectId")?.takeIf { it > 0 })
        }
    }
}

@Composable
fun HistoryScreen(
    onOpenSession: (Long) -> Unit,
    onLogSession: (subjectId: Long?) -> Unit,
    onBack: (() -> Unit)? = null,
    vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var rangeMenu by remember { mutableStateOf(false) }
    val subjectPage = onBack != null
    val pageSubject = s.subjects.firstOrNull { it.id == s.filter }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (subjectPage) pageSubject?.name ?: "Subject" else "History", onBack = onBack) {
            Box {
                androidx.compose.material3.IconButton(onClick = { rangeMenu = true }) {
                    Icon(painterResource(R.drawable.ic_filter), "Choose date range", tint = OnSurface)
                }
                RangeMenu(rangeMenu, { rangeMenu = false }, vm::setRange)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!subjectPage) item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    item { FilterChip("All", s.filter == null, { vm.setFilter(null) }, showCheck = true) }
                    items(s.subjects, key = { it.id }) { sub ->
                        FilterChip(sub.name, s.filter == sub.id, { vm.setFilter(sub.id) }, dotColor = subjectColor(sub.colorIndex).accent)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Row(
                            Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(8.dp)).border(1.dp, OutlineVariant, RoundedCornerShape(8.dp))
                                .clickable(onClickLabel = "Change date range") { rangeMenu = true }
                                .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_calendar_18), null, tint = Primary, modifier = Modifier.size(18.dp))
                            val start = s.range.start(s.today)
                            Text(if (start == null) "All time" else rangeLabel(start, s.today), style = LabelLarge, color = OnSurface)
                            Icon(painterResource(R.drawable.ic_chevron_down_18), null, tint = OnSurface, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text("${s.count} session${if (s.count == 1) "" else "s"} · ${formatMinutes(s.total)}", style = LabelMedium, color = OnSurfaceVariant)
                }
            }
            if (!s.loading && s.groups.isEmpty()) {
                item {
                    EmptyState(
                        R.drawable.ic_history,
                        if (s.hasAnySessions) "Nothing in this range" else "No sessions yet",
                        if (s.hasAnySessions) "Try a longer date range or another subject." else "Sessions you log or time will show up here.",
                        if (s.hasAnySessions && !subjectPage) null else "Log a session",
                        { onLogSession(if (subjectPage) s.filter else null) },
                    )
                }
            }
            s.groups.forEach { g ->
                item(key = "h${g.date}") {
                    SectionLabel(dayHeader(g.date, s.today), Modifier.padding(start = 16.dp, top = 8.dp))
                }
                items(g.sessions, key = { it.id }) { session ->
                    val sub = s.subjects.firstOrNull { it.id == session.subjectId }
                    SwipeToDelete(onDelete = { vm.delete(session, sub?.name.orEmpty()) }) {
                        SessionCard(
                            subject = sub?.name.orEmpty(),
                            note = session.notes,
                            time = Instant.ofEpochMilli(session.loggedAt).atZone(ZoneId.systemDefault()).toLocalTime().label() +
                                if (session.source == SessionEntity.SOURCE_TIMER) " · timer" else "",
                            duration = formatMinutes(session.durationMinutes),
                            color = sub?.let { subjectColor(it.colorIndex).accent } ?: OutlineVariant,
                            onClick = { onOpenSession(session.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeMenu(open: Boolean, onDismiss: () -> Unit, onPick: (HistoryRange) -> Unit) {
    DropdownMenu(open, onDismiss, containerColor = Color.White) {
        HistoryRange.entries.forEach { r ->
            DropdownMenuItem(text = { Text(r.label, style = BodyMedium, color = OnSurface) }, onClick = { onPick(r); onDismiss() })
        }
    }
}

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    // confirmValueChange can be called more than once per swipe (seen in Logcat as two DELETEs), so the
    // side effect is guarded. Plain remember (not saveable): a restored row starts fresh.
    var fired by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) {
            if (!fired) { fired = true; onDelete() }
            true
        } else false
    })
    // After "Undo" the session comes back with the same id, and the list restores its saved swipe state
    // ("dismissed"). Snap it back so the restored row is a normal, tappable card again.
    LaunchedEffect(Unit) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) state.snapTo(SwipeToDismissBoxValue.Settled)
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        modifier = Modifier.padding(horizontal = 16.dp),
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(ErrorContainer).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Delete", style = LabelLarge, color = Error)
                Icon(painterResource(R.drawable.ic_trash_18), null, tint = Error, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            }
        },
    ) { content() }
}
