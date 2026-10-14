package com.milestone.app.ui.session

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
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
import com.milestone.app.ui.components.MilestoneCard
import com.milestone.app.ui.components.OutlineButton
import com.milestone.app.ui.components.PlainButton
import com.milestone.app.ui.components.PrimaryButton
import com.milestone.app.ui.components.TopBar
import com.milestone.app.ui.theme.BodyMedium
import com.milestone.app.ui.theme.CardBorder
import com.milestone.app.ui.theme.DisplayLarge
import com.milestone.app.ui.theme.Error
import com.milestone.app.ui.theme.HeadlineSmall
import com.milestone.app.ui.theme.LabelLarge
import com.milestone.app.ui.theme.LabelMedium
import com.milestone.app.ui.theme.OnSurface
import com.milestone.app.ui.theme.OnSurfaceVariant
import com.milestone.app.ui.theme.Primary
import com.milestone.app.ui.theme.SurfaceContainerHigh
import com.milestone.app.ui.theme.SurfaceContainerLow
import com.milestone.app.ui.theme.TitleLarge
import com.milestone.app.ui.theme.TitleMedium
import com.milestone.app.ui.theme.subjectColor
import com.milestone.app.util.formatGoal
import com.milestone.app.util.formatMinutes
import com.milestone.app.util.fullLabel
import com.milestone.app.util.label
import com.milestone.app.util.percent
import com.milestone.app.util.shortLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SessionDetailState(val loading: Boolean = true, val session: SessionEntity? = null, val subject: SubjectEntity? = null)

class SessionDetailViewModel(private val app: MilestoneApp, handle: SavedStateHandle) : ViewModel() {
    private val id: Long = handle.get<Long>(EditSessionViewModel.ARG_SESSION) ?: -1L

    val state = combine(app.repository.session(id), app.repository.subjects) { session, subjects ->
        SessionDetailState(false, session, subjects.firstOrNull { it.id == session?.subjectId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailState())

    /** The screen leaves by itself once the session flow emits null (see LaunchedEffect below). */
    fun delete() = viewModelScope.launch {
        val s = state.value.session ?: return@launch
        val name = state.value.subject?.name.orEmpty()
        app.repository.deleteSession(s)
        app.messages.show(UiMessage("$name session deleted", "Undo") { app.repository.restoreSession(s) })
    }

    companion object {
        val Factory = appViewModel { app, handle -> SessionDetailViewModel(app, handle) }
    }
}

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: SessionDetailViewModel = viewModel(factory = SessionDetailViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val session = state.session
    val subject = state.subject

    // Leave once the session no longer exists (deleted here or elsewhere). Single exit path, so no double pop.
    LaunchedEffect(state.loading, session) { if (!state.loading && session == null) onBack() }
    if (session == null || subject == null) {
        Column(Modifier.fillMaxSize()) { TopBar("Session details", onBack = onBack) }
        return
    }
    val color = subjectColor(subject.colorIndex)
    val date = LocalDate.ofEpochDay(session.dateEpochDay)
    val loggedAt = Instant.ofEpochMilli(session.loggedAt).atZone(ZoneId.systemDefault())

    Column(Modifier.fillMaxSize()) {
        TopBar("Session details", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(color.accent.copy(alpha = 0.08f)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(color.accent), contentAlignment = Alignment.Center) {
                        Text(subject.name.take(1).uppercase(), style = TitleLarge, color = color.onAccent)
                    }
                    Column {
                        Text(subject.name, style = TitleLarge, color = OnSurface)
                        Text("Weekly goal · ${formatGoal(subject.weeklyGoalMinutes)}", style = LabelMedium, color = OnSurfaceVariant)
                    }
                }
                Column {
                    Text(formatMinutes(session.durationMinutes), style = DisplayLarge, color = OnSurface)
                    Text(if (session.source == SessionEntity.SOURCE_TIMER) "Timed with focus timer" else "Study duration",
                        style = BodyMedium, color = OnSurfaceVariant)
                }
            }
            MilestoneCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                Column {
                    InfoRow(R.drawable.ic_calendar_22, "Date", date.fullLabel())
                    Divider()
                    InfoRow(R.drawable.ic_timer_22, "Added to Milestone", loggedAt.toLocalDate().shortLabel() + ", " +
                        loggedAt.toLocalTime().label())
                    Divider()
                    InfoRow(R.drawable.ic_target_22, "Goal progress",
                        "+${percent(session.durationMinutes, subject.weeklyGoalMinutes)}% of weekly goal")
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceContainerLow).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_notes_20), null, tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text("Notes", style = TitleMedium, color = OnSurface)
                }
                Text(session.notes.ifBlank { "No notes for this session." }, style = BodyMedium, color = OnSurfaceVariant)
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton("Delete", { confirm = true }, Modifier.weight(1f), color = Error, borderColor = Error, icon = R.drawable.ic_trash_18)
            PrimaryButton("Edit session", { onEdit(session.id) }, Modifier.weight(1f), icon = R.drawable.ic_edit_18)
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = androidx.compose.ui.graphics.Color.White,
            icon = { Icon(painterResource(R.drawable.ic_trash), null, tint = Error) },
            title = { Text("Delete this session?", style = HeadlineSmall, color = OnSurface, textAlign = TextAlign.Center) },
            text = {
                Text("${formatMinutes(session.durationMinutes)} of ${subject.name} on ${date.shortLabel()} will be removed " +
                    "from your history and weekly progress.", style = BodyMedium, color = OnSurfaceVariant, textAlign = TextAlign.Center)
            },
            confirmButton = { PlainButton("Delete", { confirm = false; vm.delete() }, color = Error) },
            dismissButton = { PlainButton("Cancel", { confirm = false }) },
        )
    }
}

@Composable
private fun InfoRow(@DrawableRes icon: Int, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), null, tint = Primary, modifier = Modifier.size(22.dp))
        Text(label, style = BodyMedium, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = LabelLarge, color = OnSurface)
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder.copy(alpha = 0.6f / 0.7f)))
