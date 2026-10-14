package com.milestone.app.ui.timer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalContext
import com.milestone.app.ui.theme.TitleLarge
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milestone.app.MilestoneApp
import com.milestone.app.R
import com.milestone.app.data.db.ActiveTimerEntity
import com.milestone.app.data.db.SubjectEntity
import com.milestone.app.ml.StudyHistory
import com.milestone.app.ui.UiMessage
import com.milestone.app.ui.appViewModel
import com.milestone.app.ui.components.EmptyState
import com.milestone.app.ui.components.FilterChip
import com.milestone.app.ui.components.OutlineButton
import com.milestone.app.ui.components.PlainButton
import com.milestone.app.ui.components.PrimaryButton
import com.milestone.app.ui.components.SectionLabel
import com.milestone.app.ui.components.TopBar
import com.milestone.app.ui.theme.BodyMedium
import com.milestone.app.ui.theme.Error
import com.milestone.app.ui.theme.Jakarta
import com.milestone.app.ui.theme.LabelLarge
import com.milestone.app.ui.theme.OnSurface
import com.milestone.app.ui.theme.OnSurfaceVariant
import com.milestone.app.ui.theme.Primary
import com.milestone.app.ui.theme.SurfaceContainerHigh
import com.milestone.app.ui.theme.subjectColor
import com.milestone.app.util.formatMinutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import androidx.compose.ui.text.font.FontWeight

data class TimerUiState(
    val subjects: List<SubjectEntity> = emptyList(),
    val selectedSubjectId: Long? = null,
    /** null = stopwatch, otherwise Pomodoro length in minutes. */
    val pomodoroMinutes: Int? = 25,
    val timer: ActiveTimerEntity? = null,
    val loading: Boolean = true,
)

class TimerViewModel(private val app: MilestoneApp, initialSubject: Long?) : ViewModel() {
    private val selection = MutableStateFlow(initialSubject)
    private val mode = MutableStateFlow<Int?>(25)

    val state = combine(app.repository.subjects, app.timer.state, selection, mode) { subjects, timer, sel, mode ->
        TimerUiState(subjects, timer?.subjectId ?: sel ?: subjects.firstOrNull()?.id, timer?.targetMinutes ?: mode, timer, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUiState())

    init {
        // With no subject passed in, preselect the "needs attention" suggestion.
        if (initialSubject == null) viewModelScope.launch {
            val subjects = app.repository.subjects.first()
            val sessions = app.repository.sessions.first()
            withContext(Dispatchers.Default) {
                app.insights.recommender.suggest(StudyHistory(subjects, sessions, LocalDate.now()))
            }?.let {
                if (selection.value == null) selection.value = it.subject.id
            }
        }
    }

    fun selectSubject(id: Long) = viewModelScope.launch {
        selection.value = id
        if (state.value.timer != null) app.timer.switchSubject(id)
    }

    fun selectMode(minutes: Int?) { if (state.value.timer == null) mode.value = minutes }

    fun start() = viewModelScope.launch {
        val s = state.value
        val subject = s.selectedSubjectId ?: return@launch
        app.timer.start(subject, if (s.pomodoroMinutes == null) ActiveTimerEntity.MODE_STOPWATCH else ActiveTimerEntity.MODE_POMODORO, s.pomodoroMinutes)
    }

    fun pause() = viewModelScope.launch { app.timer.pause() }
    fun resume() = viewModelScope.launch { app.timer.resume() }

    private var finishing = false

    /** [onDone] receives the saved session id (null when nothing was saved). */
    fun finish(onDone: (Long?) -> Unit) = viewModelScope.launch {
        if (finishing) return@launch   // a second tap must not save twice or pop the screen twice
        finishing = true
        val subjectName = state.value.subjects.firstOrNull { it.id == state.value.timer?.subjectId }?.name ?: "study"
        val saved = app.timer.finish()
        if (saved == null) {
            app.messages.show(UiMessage("Less than a minute, so nothing was saved"))
        } else {
            app.messages.show(UiMessage("Saved ${formatMinutes(saved.durationMinutes)} of $subjectName", "Undo") {
                app.repository.deleteSession(saved)
            })
        }
        onDone(saved?.id)
    }

    fun discard() = viewModelScope.launch { app.timer.discard() }

    companion object {
        fun factory(subjectId: Long?) = appViewModel { app, _ -> TimerViewModel(app, subjectId) }
    }
}

/** Live mm:ss (or h:mm:ss) text derived from the wall clock; recomposes once per second only while running. */
@Composable
fun TimerClock(t: ActiveTimerEntity, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val now = rememberTicker(t.isRunning)
    val shown = displayMs(t, now)
    Text(clock(shown), style = style.copy(fontFeatureSettings = "tnum"), color = color, modifier = modifier)
}

@Composable
private fun rememberTicker(running: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        now = System.currentTimeMillis()
        while (running) { delay(1_000L - System.currentTimeMillis() % 1_000L); now = System.currentTimeMillis() }
    }
    return now
}

/** Countdown shows time left; stopwatch shows time elapsed. */
private fun displayMs(t: ActiveTimerEntity, now: Long): Long {
    val elapsed = t.elapsedMs(now)
    return t.targetMinutes?.let { (it * 60_000L - elapsed).coerceAtLeast(0) } ?: elapsed
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
fun TimerScreen(
    subjectId: Long?,
    onBack: () -> Unit,
    onAddSubject: () -> Unit,
    onSaved: (sessionId: Long) -> Unit,
    vm: TimerViewModel = viewModel(factory = TimerViewModel.factory(subjectId)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val t = state.timer
    val now = rememberTicker(t?.isRunning == true)
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(notificationsAllowed(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = granted
        vm.start()
    }
    // Ask only when not yet granted (Android stops showing the prompt by itself after two refusals).
    val startWithPermission = {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsOn) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.start()
    }
    var confirmDiscard by remember { mutableStateOf(false) }
    val chipRow = rememberLazyListState()
    LaunchedEffect(state.selectedSubjectId, state.subjects.size) {
        // Make sure the preselected subject (e.g. from "Needs attention") is visible, not off-screen.
        val i = state.subjects.indexOfFirst { it.id == state.selectedSubjectId }
        if (i >= 0) chipRow.animateScrollToItem(i)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Focus timer", onBack = onBack)
        if (!state.loading && state.subjects.isEmpty()) {
            EmptyState(R.drawable.ic_book, "No subjects yet", "Add a subject first, then time your study sessions.",
                "Add subject", onAddSubject)
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SectionLabel("SUBJECT")
            LazyRow(state = chipRow, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.subjects, key = { it.id }) { s ->
                    FilterChip(s.name, s.id == state.selectedSubjectId, { vm.selectSubject(s.id) },
                        dotColor = subjectColor(s.colorIndex).accent)
                }
            }
            SectionLabel("MODE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50).forEach { m ->
                    FilterChip("Pomodoro ${m}m", state.pomodoroMinutes == m, { vm.selectMode(m) })
                }
                FilterChip("Stopwatch", state.pomodoroMinutes == null, { vm.selectMode(null) })
            }
            if (t != null) Text("Mode is locked while the timer runs.", style = BodyMedium, color = OnSurfaceVariant)
            if (!notificationsOn && (state.pomodoroMinutes != null)) {
                Text("Notifications are off, so you won't get an alert when the Pomodoro ends while the app is closed. " +
                    "The session is still saved automatically.", style = BodyMedium, color = OnSurfaceVariant)
            }

            TimerDial(state, t)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                t == null -> PrimaryButton("Start focusing", { startWithPermission() }, Modifier.fillMaxWidth(),
                    enabled = state.selectedSubjectId != null)
                else -> {
                    val done = t.targetMinutes != null && t.elapsedMs(now) >= t.targetMinutes * 60_000L
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!done) {
                            OutlineButton(if (t.isRunning) "Pause" else "Resume", { if (t.isRunning) vm.pause() else vm.resume() },
                                Modifier.weight(1f))
                        }
                        PrimaryButton("Finish & save", { vm.finish { id -> if (id != null) onSaved(id) else onBack() } },
                            Modifier.weight(1f), icon = R.drawable.ic_check_18)
                    }
                    PlainButton("Discard", { confirmDiscard = true }, Modifier.fillMaxWidth(), color = Error)
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = Color.White,
            title = { Text("Discard this timer?", style = TitleLarge, color = OnSurface) },
            text = { Text("The time on the clock won't be saved.", style = BodyMedium, color = OnSurfaceVariant) },
            confirmButton = { PlainButton("Discard", { confirmDiscard = false; vm.discard() }, color = Error) },
            dismissButton = { PlainButton("Keep timing", { confirmDiscard = false }) },
        )
    }
}

private fun notificationsAllowed(context: android.content.Context) = Build.VERSION.SDK_INT < 33 ||
    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
    android.content.pm.PackageManager.PERMISSION_GRANTED

@Composable
private fun TimerDial(state: TimerUiState, t: ActiveTimerEntity?) {
    val subject = state.subjects.firstOrNull { it.id == state.selectedSubjectId }
    val accent = subject?.let { subjectColor(it.colorIndex).accent } ?: Primary
    val now = rememberTicker(t?.isRunning == true)
    val target = (t?.targetMinutes ?: state.pomodoroMinutes)?.times(60_000L)
    val elapsed = t?.elapsedMs(now) ?: 0L
    val progress = when {
        target != null -> (elapsed.toFloat() / target).coerceIn(0f, 1f)
        else -> ((elapsed % 3_600_000L) / 3_600_000f)   // stopwatch: one lap per hour
    }
    val shown = if (t == null) (target ?: 0L) else displayMs(t, now)
    val finished = target != null && t != null && elapsed >= target
    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(260.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(SurfaceContainerHigh, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(accent, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            // No live region: announcing every second would flood TalkBack. Only completion is announced.
            modifier = Modifier.semantics(mergeDescendants = true) {
                if (finished) { liveRegion = LiveRegionMode.Polite; contentDescription = "Session complete" }
            }) {
            Text(
                clock(shown),
                style = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp,
                    letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
                color = OnSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    t == null -> if (target == null) "Stopwatch" else "Ready"
                    finished -> "Session complete!"
                    !t.isRunning -> "Paused"
                    target != null -> "remaining · ${subject?.name.orEmpty()}"
                    else -> subject?.name.orEmpty()
                },
                style = LabelLarge, color = if (finished) Primary else OnSurfaceVariant,
            )
        }
    }
}
