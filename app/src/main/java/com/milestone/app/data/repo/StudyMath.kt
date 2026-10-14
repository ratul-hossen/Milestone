package com.milestone.app.data.repo

import com.milestone.app.data.db.SessionEntity
import com.milestone.app.data.db.SubjectEntity
import com.milestone.app.ml.StudyHistory
import com.milestone.app.util.startOfWeek
import java.time.LocalDate

/** Per-subject numbers shown on subject cards. */
data class SubjectSummary(
    val subject: SubjectEntity,
    val weekMinutes: Int,
    val todayMinutes: Int,
    val lastStudied: LocalDate?,
    val sessionCount: Int,
) {
    val progress: Float
        get() = if (subject.weeklyGoalMinutes <= 0) 0f else weekMinutes.toFloat() / subject.weeklyGoalMinutes
}

/** Plain (non-ML) statistics. Every function is pure so it is easy to test and explain. */
object StudyMath {

    fun summaries(h: StudyHistory): List<SubjectSummary> {
        val weekStart = h.today.startOfWeek().toEpochDay()
        val tomorrow = h.todayEpoch + 1
        val counts = h.sessions.groupingBy { it.subjectId }.eachCount()
        return h.subjects.map { s ->
            SubjectSummary(
                subject = s,
                weekMinutes = h.total(s.id, weekStart, tomorrow),
                todayMinutes = h.minutes(s.id, h.todayEpoch),
                lastStudied = h.lastStudied(s.id, tomorrow)?.let(LocalDate::ofEpochDay),
                sessionCount = counts[s.id] ?: 0,
            )
        }
    }

    /**
     * Consecutive study days ending today. If nothing is logged yet today the streak is still alive
     * when yesterday counts, because the day isn't over.
     */
    fun currentStreak(h: StudyHistory): Int {
        var day = if (h.minutes(h.todayEpoch) > 0) h.todayEpoch else h.todayEpoch - 1
        var streak = 0
        while (h.minutes(day) > 0) { streak++; day-- }
        return streak
    }

    fun longestStreak(sessions: List<SessionEntity>): Int {
        val days = sessions.map { it.dateEpochDay }.toSortedSet()
        var best = 0
        var run = 0
        var prev: Long? = null
        for (d in days) {
            run = if (prev != null && d == prev + 1) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    fun weeklyGoal(subjects: List<SubjectEntity>) = subjects.sumOf { it.weeklyGoalMinutes }
}
