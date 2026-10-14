package com.milestone.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milestone.app.R
import com.milestone.app.data.db.SubjectEntity
import com.milestone.app.ml.SubjectRecommender
import com.milestone.app.ml.WeeklyForecaster
import com.milestone.app.ui.components.EmptyState
import com.milestone.app.ui.components.FabExtended
import com.milestone.app.ui.components.ForecastExplainSheet
import com.milestone.app.ui.components.FormField
import com.milestone.app.ui.components.MilestoneCard
import com.milestone.app.ui.components.PlainButton
import com.milestone.app.ui.components.ProgressBar
import com.milestone.app.ui.components.SectionHeader
import com.milestone.app.ui.components.SectionLabel
import com.milestone.app.ui.components.SubjectAvatar
import com.milestone.app.ui.components.SubjectCard
import com.milestone.app.ui.components.SuggestionExplainSheet
import com.milestone.app.ui.subjects.SubjectEditorHost
import com.milestone.app.ui.subjects.rememberSubjectEditor
import com.milestone.app.ui.theme.BodyMedium
import com.milestone.app.ui.theme.DisplayLarge
import com.milestone.app.ui.theme.HeadlineMedium
import com.milestone.app.ui.theme.InverseSurface
import com.milestone.app.ui.theme.LabelLarge
import com.milestone.app.ui.theme.LabelMedium
import com.milestone.app.ui.theme.OnInverseMuted
import com.milestone.app.ui.theme.OnSurface
import com.milestone.app.ui.theme.OnSurfaceVariant
import com.milestone.app.ui.theme.Primary
import com.milestone.app.ui.theme.SecondaryContainer
import com.milestone.app.ui.theme.TitleMedium
import com.milestone.app.ui.theme.subjectColor
import com.milestone.app.ui.timer.TimerClock
import com.milestone.app.util.formatGoal
import com.milestone.app.util.formatMinutes
import com.milestone.app.util.greeting
import com.milestone.app.util.headerLabel
import com.milestone.app.util.percent
import com.milestone.app.util.relativeDay
import java.time.LocalTime

@Composable
fun HomeScreen(
    onLogSession: (subjectId: Long?) -> Unit,
    onOpenTimer: (subjectId: Long?) -> Unit,
    onSeeAllSubjects: () -> Unit,
    onOpenSubject: (Long) -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val editor = rememberSubjectEditor()
    var explainForecast by remember { mutableStateOf<WeeklyForecaster.Forecast?>(null) }
    var explainSuggestion by remember { mutableStateOf<SubjectRecommender.Suggestion?>(null) }
    var editName by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Header(state, onTimer = { onOpenTimer(null) }, onAvatar = { editName = true })
            }
            state.timer?.let { t ->
                item { RunningTimerBanner(state.timerSubject ?: "Study", t, onClick = { onOpenTimer(null) }) }
            }
            item { TodayCard(state, onExplain = { explainForecast = it }) }
            state.suggestion?.let { s ->
                item {
                    SuggestionCard(s, onStart = { onOpenTimer(s.subject.id) }, onLog = { onLogSession(s.subject.id) },
                        onWhy = { explainSuggestion = s })
                }
            }
            item {
                SectionHeader("Subjects", Modifier.semantics { heading() }) {
                    if (state.subjects.isNotEmpty()) PlainButton("See all", onSeeAllSubjects)
                }
            }
            if (!state.loading && state.subjects.isEmpty()) {
                item {
                    EmptyState(
                        icon = R.drawable.ic_book,
                        title = "Add your first subject",
                        message = "Create the subjects you study and set a weekly goal for each. You can change them any time.",
                        action = "Add subject",
                        onAction = { editor.add() },
                    )
                }
            }
            items(state.subjects.take(3), key = { it.subject.id }) { s ->
                val goal = formatGoal(s.subject.weeklyGoalMinutes)
                SubjectCard(
                    name = s.subject.name,
                    subtitle = "${formatMinutes(s.weekMinutes)} of $goal · ${relativeDay(s.lastStudied, state.today)}",
                    progress = s.progress,
                    color = subjectColor(s.subject.colorIndex),
                    onClick = { onOpenSubject(s.subject.id) },
                    onEdit = { editor.edit(s.subject) },
                    onDelete = { editor.requestDelete(s.subject) },
                )
            }
        }
        if (state.subjects.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                FabExtended("Log session", onClick = { onLogSession(null) })
            }
        }
    }

    SubjectEditorHost(editor)
    explainForecast?.let { ForecastExplainSheet(it) { explainForecast = null } }
    explainSuggestion?.let { SuggestionExplainSheet(it) { explainSuggestion = null } }
    if (editName) NameDialog(state.name, onSave = { vm.setName(it); editName = false }, onDismiss = { editName = false })
}

@Composable
private fun Header(state: HomeUiState, onTimer: () -> Unit, onAvatar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(state.today.headerLabel(), style = LabelMedium, color = OnSurfaceVariant)
            val hello = greeting(LocalTime.now())
            Text(if (state.name.isBlank()) hello else "$hello, ${state.name}", style = HeadlineMedium, color = OnSurface,
                modifier = Modifier.semantics { heading() })
        }
        IconButton(onClick = onTimer) {
            Icon(painterResource(R.drawable.ic_timer), "Focus timer", tint = OnSurface)
        }
        Box(
            Modifier.minimumInteractiveComponentSize().size(44.dp).clip(CircleShape).background(SecondaryContainer).clickable(onClick = onAvatar)
                .semantics { contentDescription = "Edit your name" },
            contentAlignment = Alignment.Center,
        ) {
            Text(state.name.take(1).uppercase().ifEmpty { "M" }, style = TitleMedium, color = Color.White)
        }
    }
}

@Composable
private fun TodayCard(state: HomeUiState, onExplain: (WeeklyForecaster.Forecast) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(InverseSurface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Studied today", style = LabelLarge, color = OnInverseMuted)
                Text(formatMinutes(state.todayMinutes), style = DisplayLarge, color = Color.White)
                Text(
                    if (state.todaySessions == 0) "Nothing logged yet today"
                    else "across ${state.todaySubjects} subject${if (state.todaySubjects == 1) "" else "s"} · " +
                        "${state.todaySessions} session${if (state.todaySessions == 1) "" else "s"}",
                    style = BodyMedium, color = OnInverseMuted,
                )
            }
            if (state.streak > 0) {
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Primary).padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "${state.streak} day study streak" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_flame_18), null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("${state.streak} day${if (state.streak == 1) "" else "s"}", style = LabelMedium, color = Color.White)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_flag_18), null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("Weekly goal", style = LabelLarge, color = Color.White, modifier = Modifier.weight(1f))
            Text(
                if (state.weeklyGoal > 0) "${formatMinutes(state.weekMinutes)} / ${formatGoal(state.weeklyGoal)}" else "No goal yet",
                style = LabelLarge, color = Color.White,
            )
        }
        ProgressBar(
            progress = if (state.weeklyGoal > 0) state.weekMinutes.toFloat() / state.weeklyGoal else 0f,
            color = Primary,
            // 12% white track keeps the red fill at 3.01:1 (WCAG non-text); Figma's 22% gave only 2.11:1.
            track = Color.White.copy(alpha = 0.12f),
        )
        val left = (state.weeklyGoal - state.weekMinutes).coerceAtLeast(0)
        Text(
            when {
                state.weeklyGoal == 0 -> "Add subjects with weekly goals to track progress"
                left == 0 -> "Goal reached this week · ${percent(state.weekMinutes, state.weeklyGoal)}%"
                else -> "${percent(state.weekMinutes, state.weeklyGoal)}% complete · ${formatMinutes(left)} to go this week"
            },
            style = LabelMedium, color = OnInverseMuted,
        )
        state.forecast?.let { f ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClickLabel = "Explain forecast") { onExplain(f) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("FORECAST", style = LabelMedium, color = OnInverseMuted)
                    Text(
                        "~${formatMinutes(f.predictedTotalMinutes)} by Sunday · " +
                            if (f.onTrack) "on track" else "${formatMinutes(f.goalMinutes - f.predictedTotalMinutes)} short",
                        style = LabelLarge, color = Color.White,
                    )
                    if (!f.onTrack) {
                        // Actionable: how much per remaining day (today included) closes the gap.
                        val daysLeft = 7 - state.today.dayOfWeek.value + 1
                        val perDay = (f.goalMinutes - f.soFarMinutes).coerceAtLeast(0) / daysLeft
                        Text("About ${formatMinutes(perDay)} a day reaches your goal", style = LabelMedium, color = OnInverseMuted)
                    }
                }
                Text("Why?", style = LabelLarge, color = Color(0xFFFFB3B8))
            }
        }
    }
}

@Composable
private fun SuggestionCard(s: SubjectRecommender.Suggestion, onStart: () -> Unit, onLog: () -> Unit, onWhy: () -> Unit) {
    val color = subjectColor(s.subject.colorIndex)
    MilestoneCard(onClick = onLog) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("NEEDS ATTENTION TODAY", Modifier.weight(1f))
                Text("Why?", style = LabelLarge, color = Primary,
                    modifier = Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = "Explain this suggestion", onClick = onWhy).padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SubjectAvatar(s.subject.name, color)
                Column(Modifier.weight(1f)) {
                    Text(s.subject.name, style = TitleMedium, color = OnSurface)
                    Text("${(s.goalLeft * 100).toInt()}% of this week's goal left", style = BodyMedium, color = OnSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(999.dp)).background(SecondaryContainer)
                        .clickable(onClick = onStart).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_timer), null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Start timer", style = LabelLarge, color = Color.White)
                }
                Row(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(999.dp))
                        .border(1.dp, com.milestone.app.ui.theme.Outline, RoundedCornerShape(999.dp))
                        .clickable(onClick = onLog).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { Text("Log time", style = LabelLarge, color = Primary) }
            }
        }
    }
}

@Composable
private fun RunningTimerBanner(subject: String, t: com.milestone.app.data.db.ActiveTimerEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(com.milestone.app.ui.theme.PrimaryContainer)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_timer), null, tint = com.milestone.app.ui.theme.OnPrimaryContainer, modifier = Modifier.size(20.dp))
        Text(if (t.isRunning) "Focusing on $subject" else "Paused · $subject", style = LabelLarge,
            color = com.milestone.app.ui.theme.OnPrimaryContainer, modifier = Modifier.weight(1f))
        TimerClock(t, style = LabelLarge, color = com.milestone.app.ui.theme.OnPrimaryContainer)
    }
}

@Composable
private fun NameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("What should we call you?", style = com.milestone.app.ui.theme.TitleLarge) },
        text = {
            FormField("Your name", value, { value = it.take(24) }, R.drawable.ic_book_field, placeholder = "e.g. Ratul",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        },
        confirmButton = { PlainButton("Save", { onSave(value) }) },
        dismissButton = { PlainButton("Cancel", onDismiss) },
    )
}
