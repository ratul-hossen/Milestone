package com.milestone.app.ui.subjects

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.milestone.app.data.repo.StudyMath
import com.milestone.app.data.repo.SubjectSummary
import com.milestone.app.ml.StudyHistory
import com.milestone.app.ui.appViewModel
import com.milestone.app.ui.components.EmptyState
import com.milestone.app.ui.components.FabExtended
import com.milestone.app.ui.components.SubjectCard
import com.milestone.app.ui.components.TopBar
import com.milestone.app.ui.theme.LabelLarge
import com.milestone.app.ui.theme.SecondaryContainer
import com.milestone.app.ui.theme.TitleMedium
import com.milestone.app.ui.theme.subjectColor
import com.milestone.app.ui.todayFlow
import com.milestone.app.util.formatGoal
import com.milestone.app.util.formatMinutes
import com.milestone.app.util.percent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SubjectsState(
    val loading: Boolean = true,
    val subjects: List<SubjectSummary> = emptyList(),
    val weeklyGoal: Int = 0,
    val weekMinutes: Int = 0,
)

class SubjectsViewModel(app: MilestoneApp) : ViewModel() {
    val state = combine(app.repository.subjects, app.repository.sessions, todayFlow()) { subjects, sessions, today ->
        val summaries = StudyMath.summaries(StudyHistory(subjects, sessions, today))
        SubjectsState(false, summaries, StudyMath.weeklyGoal(subjects),
            // Count minutes up to each subject's goal, so one over-studied subject can't hide another at 0%.
            summaries.sumOf { minOf(it.weekMinutes, it.subject.weeklyGoalMinutes) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubjectsState())

    companion object {
        val Factory = appViewModel { app, _ -> SubjectsViewModel(app) }
    }
}

@Composable
fun SubjectsScreen(
    onOpenSubject: (Long) -> Unit,
    vm: SubjectsViewModel = viewModel(factory = SubjectsViewModel.Factory),
    editor: SubjectEditorViewModel = rememberSubjectEditor(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Column {
            TopBar("Subjects")
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (s.subjects.isNotEmpty()) item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SecondaryContainer)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_target_20), null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("${s.subjects.size} subject${if (s.subjects.size == 1) "" else "s"} · ${formatGoal(s.weeklyGoal)} weekly goal",
                            style = LabelLarge, color = Color.White, modifier = Modifier.weight(1f))
                        Text("${percent(s.weekMinutes, s.weeklyGoal)}%", style = TitleMedium, color = Color.White)
                    }
                }
                if (!s.loading && s.subjects.isEmpty()) item {
                    EmptyState(R.drawable.ic_book, "No subjects yet",
                        "Add every subject you're studying, each with its own colour and weekly goal.",
                        "Add subject", { editor.add() })
                }
                items(s.subjects, key = { it.subject.id }) { sum ->
                    SubjectCard(
                        name = sum.subject.name,
                        subtitle = "${formatMinutes(sum.weekMinutes)} of ${formatGoal(sum.subject.weeklyGoalMinutes)} this week",
                        progress = sum.progress,
                        color = subjectColor(sum.subject.colorIndex),
                        onClick = { onOpenSubject(sum.subject.id) },
                        onEdit = { editor.edit(sum.subject) },
                        onDelete = { editor.requestDelete(sum.subject) },
                    )
                }
            }
        }
        if (s.subjects.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { FabExtended("Add subject", { editor.add() }) }
        }
    }
    SubjectEditorHost(editor)
}
