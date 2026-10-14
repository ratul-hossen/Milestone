package com.milestone.app.ml

import com.milestone.app.data.db.SessionEntity
import com.milestone.app.util.AppLog
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Suggests a session length from the user's OWN past sessions (k-nearest-neighbours framework).
 * Hyper-parameters: ml/03_session_duration_knn.ipynb.
 *
 * Honest note: in that notebook a plain per-subject median beat every k-NN variant on held-out users,
 * so the exported config (huge subject weight, other terms 0, k = 1000, median) makes this class
 * behave exactly as that median. Better-scoring settings can be shipped later without code changes.
 *
 *   distance = wS·[different subject] + wH·|Δhour|/12 + wD·[different weekday] + wA·ageDays/30
 *   prediction = weighted median (or mean) of the k nearest durations, weights 1 / (d + ε),
 *                rounded to 5 min. The median is used when it wins in the notebook, because
 *                session lengths are skewed and we judge the suggestion by absolute error.
 */
class DurationSuggester(private val config: DurationConfig) {

    data class Suggestion(val minutes: Int, val basedOn: List<SessionEntity>, val fromHistory: Boolean)

    fun suggest(sessions: List<SessionEntity>, subjectId: Long?, hour: Int, date: LocalDate): Suggestion {
        val past = sessions.sortedByDescending { it.loggedAt }.take(config.maxHistory)
        if (past.size < config.minHistory) {
            AppLog.d(AppLog.ML, "Duration: only ${past.size} sessions, fallback ${config.fallbackMinutes}min")
            return Suggestion(config.fallbackMinutes, emptyList(), false)
        }
        val day = date.toEpochDay()
        val weekday = date.dayOfWeek
        val nearest = past.map { s ->
            val d = config.wSubject * (if (s.subjectId != subjectId) 1 else 0) +
                config.wHour * abs(s.startHour - hour) / 12.0 +
                config.wWeekday * (if (LocalDate.ofEpochDay(s.dateEpochDay).dayOfWeek != weekday) 1 else 0) +
                config.wAge * (day - s.dateEpochDay).coerceAtLeast(0) / 30.0
            s to d
        }.sortedBy { it.second }.take(config.k)
        val weights = nearest.map { 1 / (it.second + config.epsilon) }
        val estimate = if (config.aggregate == "mean") {
            nearest.indices.sumOf { nearest[it].first.durationMinutes * weights[it] } / weights.sum()
        } else {
            weightedMedian(nearest.map { it.first.durationMinutes.toDouble() }, weights)
        }
        val minutes = ((estimate / 5).roundToInt() * 5).coerceIn(5, 600)
        AppLog.d(AppLog.ML, "Duration subject=$subjectId hour=$hour -> ${minutes}min (${config.aggregate} of " +
            "${nearest.size} sessions; nearest: " + nearest.take(5).joinToString { "${it.first.durationMinutes}m d=%.2f".format(it.second) } + ")")
        // For the explanation, show the sessions that actually carried weight (same subject when available).
        val shown = nearest.filter { it.second < 1.0 }.ifEmpty { nearest }.map { it.first }
        return Suggestion(minutes, shown, true)
    }

    /**
     * Weighted median. Neighbours with negligible weight (< 0.1% of the largest) are dropped, and when
     * the cumulative weight lands exactly on half, the two middle values are averaged. With equal
     * weights this is identical to numpy's `np.median`, which is what the notebook's winning
     * "subject median" baseline was scored with (verified in ModelsTest).
     */
    private fun weightedMedian(values: List<Double>, weights: List<Double>): Double {
        val maxW = weights.max()
        val kept = values.indices.filter { weights[it] >= maxW * 1e-3 }.sortedBy { values[it] }
        val half = kept.sumOf { weights[it] } / 2
        var cumulative = 0.0
        for ((pos, i) in kept.withIndex()) {
            cumulative += weights[i]
            if (kotlin.math.abs(cumulative - half) < 1e-9 * half && pos < kept.lastIndex) {
                return (values[i] + values[kept[pos + 1]]) / 2
            }
            if (cumulative > half) return values[i]
        }
        return values[kept.last()]
    }
}
