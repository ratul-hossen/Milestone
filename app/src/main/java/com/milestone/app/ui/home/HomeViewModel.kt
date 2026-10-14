package com.milestone.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milestone.app.MilestoneApp
import com.milestone.app.data.db.ActiveTimerEntity
import com.milestone.app.data.repo.StudyMath
import com.milestone.app.data.repo.SubjectSummary
import com.milestone.app.ml.StudyHistory
import com.milestone.app.ml.SubjectRecommender
import com.milestone.app.ml.WeeklyForecaster
import com.milestone.app.ui.appViewModel
import com.milestone.app.ui.todayFlow
import com.milestone.app.util.AppLog
import com.milestone.app.util.startOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val name: String = "",
    val todayMinutes: Int = 0,
    val todaySubjects: Int = 0,
    val todaySessions: Int = 0,
    val streak: Int = 0,
    val weekMinutes: Int = 0,
    val weeklyGoal: Int = 0,
    val forecast: WeeklyForecaster.Forecast? = null,
    val suggestion: SubjectRecommender.Suggestion? = null,
    val subjects: List<SubjectSummary> = emptyList(),
    val timer: ActiveTimerEntity? = null,
    val timerSubject: String? = null,
)

class HomeViewModel(private val app: MilestoneApp) : ViewModel() {
    private val repo = app.repository
    private val name = MutableStateFlow(app.prefs.name)

    val state: StateFlow<HomeUiState> = combine(
        repo.subjects, repo.sessions, repo.activeTimer, todayFlow(), name,
    ) { subjects, sessions, timer, today, name ->
        val h = StudyHistory(subjects, sessions, today)
        val weekStart = today.startOfWeek().toEpochDay()
        val todays = sessions.filter { it.dateEpochDay == today.toEpochDay() }
        val goal = StudyMath.weeklyGoal(subjects)
        HomeUiState(
            loading = false,
            today = today,
            name = name,
            todayMinutes = todays.sumOf { it.durationMinutes },
            todaySubjects = todays.map { it.subjectId }.distinct().size,
            todaySessions = todays.size,
            streak = StudyMath.currentStreak(h),
            weekMinutes = h.total(weekStart, today.toEpochDay() + 1),
            weeklyGoal = goal,
            forecast = if (goal > 0) app.insights.forecaster.forecast(h, goal) else null,
            suggestion = app.insights.recommender.suggest(h),
            // Most relevant first: furthest behind on its weekly goal.
            subjects = StudyMath.summaries(h).sortedBy { it.progress },
            timer = timer,
            timerSubject = timer?.let { t -> subjects.firstOrNull { it.id == t.subjectId }?.name },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setName(value: String) {
        app.prefs.name = value
        name.value = value.trim()
        AppLog.d(AppLog.UI, "Name updated")
    }

    companion object {
        val Factory = appViewModel { app, _ -> HomeViewModel(app) }
    }
}
