package com.milestone.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milestone.app.MilestoneApp
import com.milestone.app.data.db.SubjectEntity
import com.milestone.app.data.repo.StudyMath
import com.milestone.app.ml.StudyHistory
import com.milestone.app.ui.appViewModel
import com.milestone.app.ui.todayFlow
import com.milestone.app.util.startOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class StatsRange(val label: String, val days: Int?) {
    W1("1W", 7), M1("1M", 28), M3("3M", 91), M6("6M", 182), Y1("1Y", 365), ALL("ALL", null)
}

/** One chart point: a day, or a week when the range is long (keeps the line readable). */
data class ChartPoint(val start: LocalDate, val end: LocalDate, val minutes: Int)

data class SubjectTrend(
    val subject: SubjectEntity,
    val weekMinutes: Int,
    val sessions: Int,
    /** Minutes per day for the last 7 days, oldest first. */
    val spark: List<Int>,
    /** % change vs last week; null when last week was 0 (no meaningful %). */
    val changePct: Double?,
)

data class StatsState(
    val loading: Boolean = true,
    val range: StatsRange = StatsRange.M1,
    val rangeDays: Int = 28,
    val total: Int = 0,
    val previousTotal: Int? = null,
    val points: List<ChartPoint> = emptyList(),
    val weekly: Boolean = false,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val sessions: Int = 0,
    val previousSessions: Int? = null,
    val dailyAverage: Int = 0,
    val previousDailyAverage: Int? = null,
    val goalWeeksHit: Int = 0,
    val goalWeeks: Int = 0,
    val trends: List<SubjectTrend> = emptyList(),
    val hasData: Boolean = false,
)

class StatsViewModel(app: MilestoneApp) : ViewModel() {
    private val range = MutableStateFlow(StatsRange.M1)

    val state = combine(app.repository.subjects, app.repository.sessions, range, todayFlow()) { subjects, sessions, r, today ->
        val h = StudyHistory(subjects, sessions, today)
        val end = today.toEpochDay() + 1                              // exclusive
        val first = h.firstDay ?: today.toEpochDay()
        val days = r.days ?: (end - first).toInt().coerceAtLeast(7)
        val start = end - days
        val prevStart = start - days
        // A previous period only exists if the user was already logging back then.
        val hasPrevious = r.days != null && first < start

        val weekly = days > 91
        val points = if (!weekly) {
            (start until end).map { d -> LocalDate.ofEpochDay(d).let { ChartPoint(it, it, h.minutes(d)) } }
        } else {
            // Buckets are built backwards from today so every point is a full 7 days (no fake dip at the end).
            generateSequence(end) { it - 7 }.takeWhile { it - 7 >= start }.toList().reversed().map { e ->
                ChartPoint(LocalDate.ofEpochDay(e - 7), LocalDate.ofEpochDay(e - 1), h.total(e - 7, e))
            }
        }
        // Averages and goal weeks only count days since the first log, so a new user isn't divided by 365.
        val activeStart = maxOf(start, first)
        val activeDays = (end - activeStart).toInt().coerceAtLeast(1)
        val inRange = sessions.filter { it.dateEpochDay in start until end }
        val inPrev = sessions.filter { it.dateEpochDay in prevStart until start }
        val total = inRange.sumOf { it.durationMinutes }
        val prevTotal = inPrev.sumOf { it.durationMinutes }

        // Goal weeks: complete Mon–Sun weeks inside the range, compared with today's total goal.
        val goal = StudyMath.weeklyGoal(subjects)
        val thisWeek = today.startOfWeek().toEpochDay()
        val weeks = generateSequence(LocalDate.ofEpochDay(activeStart).startOfWeek().toEpochDay().let { if (it < activeStart) it + 7 else it }) { it + 7 }
            .takeWhile { it + 7 <= thisWeek }.toList()
        val weeksHit = if (goal == 0) 0 else weeks.count { h.total(it, it + 7) >= goal }

        val trends = subjects.map { s ->
            val week = h.total(s.id, thisWeek, end)
            val last = h.total(s.id, thisWeek - 7, thisWeek - 7 + (end - thisWeek))  // same weekdays last week
            SubjectTrend(
                subject = s,
                weekMinutes = week,
                sessions = sessions.count { it.subjectId == s.id && it.dateEpochDay in thisWeek until end },
                spark = (end - 7 until end).map { h.minutes(s.id, it) },
                changePct = if (last == 0) null else (week - last) * 100.0 / last,
            )
        }.sortedByDescending { it.weekMinutes }

        StatsState(
            loading = false, range = r, rangeDays = days, total = total,
            previousTotal = if (hasPrevious) prevTotal else null,
            points = points, weekly = weekly,
            streak = StudyMath.currentStreak(h), bestStreak = StudyMath.longestStreak(sessions),
            sessions = inRange.size, previousSessions = if (hasPrevious) inPrev.size else null,
            dailyAverage = total / activeDays,
            previousDailyAverage = if (hasPrevious) prevTotal / (start - maxOf(prevStart, first)).toInt().coerceAtLeast(1) else null,
            goalWeeksHit = weeksHit, goalWeeks = if (goal == 0) 0 else weeks.size,
            trends = trends, hasData = sessions.isNotEmpty(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun setRange(r: StatsRange) { range.value = r }

    companion object {
        val Factory = appViewModel { app, _ -> StatsViewModel(app) }
    }
}
