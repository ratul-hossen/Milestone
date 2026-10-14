package com.milestone.app.ml

import com.milestone.app.data.db.SessionEntity
import com.milestone.app.data.db.SubjectEntity
import java.time.LocalDate

/**
 * Read-only view of the user's logs, indexed by day, that every model shares.
 * Building it is O(sessions); queries are O(1) or O(days).
 */
class StudyHistory(
    val subjects: List<SubjectEntity>,
    val sessions: List<SessionEntity>,
    val today: LocalDate,
) {
    private val todayIdx = today.toEpochDay()

    /** minutes[subjectId][epochDay] */
    private val bySubjectDay: Map<Long, Map<Long, Int>> =
        sessions.groupBy { it.subjectId }.mapValues { (_, list) ->
            list.groupingBy { it.dateEpochDay }.fold(0) { acc, s -> acc + s.durationMinutes }
        }

    private val totalByDay: Map<Long, Int> =
        sessions.groupingBy { it.dateEpochDay }.fold(0) { acc, s -> acc + s.durationMinutes }

    val firstDay: Long? = sessions.minOfOrNull { it.dateEpochDay }

    fun minutes(day: Long): Int = totalByDay[day] ?: 0
    fun minutes(subjectId: Long, day: Long): Int = bySubjectDay[subjectId]?.get(day) ?: 0

    /** Sum of minutes over the half-open day range [from, to). */
    fun total(from: Long, to: Long): Int = (from until to).sumOf { minutes(it) }
    fun total(subjectId: Long, from: Long, to: Long): Int = (from until to).sumOf { minutes(subjectId, it) }

    private val sortedDays: Map<Long, LongArray> =
        bySubjectDay.mapValues { (_, days) -> days.keys.sorted().toLongArray() }

    /** Most recent study day strictly before [before], or null. Binary search: called inside training loops. */
    fun lastStudied(subjectId: Long, before: Long): Long? {
        val days = sortedDays[subjectId] ?: return null
        val i = java.util.Arrays.binarySearch(days, before)
        val insertion = if (i >= 0) i else -i - 1
        return if (insertion == 0) null else days[insertion - 1]
    }

    val todayEpoch: Long get() = todayIdx
}
