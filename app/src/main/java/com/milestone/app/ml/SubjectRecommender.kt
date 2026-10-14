package com.milestone.app.ml

import com.milestone.app.data.db.SubjectEntity
import com.milestone.app.util.AppLog
import com.milestone.app.util.startOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln

/**
 * "Needs attention" — which subject is the student most likely to neglect today?
 *
 * Step 1: logistic regression estimates p = P(the student studies subject s today on their own).
 * Model and training: ml/02_next_subject_recommender.ipynb (real OULAD learners with several modules).
 *
 * Features (only data from *before* today is used, so the model never sees the answer):
 *   x1 log(1 + days since last studied), capped at 30
 *   x2 share of this week's goal still left (0–1)
 *   x3 share of the last 8 same weekdays on which the subject was studied
 *   x4 subject's share of all minutes in the last 14 days
 *   x5 studied yesterday (0/1)
 *
 * Step 2 (decision rule): need(s) = goalLeft(s) × (1 − p). Suggest the subject with the highest need.
 * In the notebook the model is well calibrated but does NOT beat "same as yesterday" at guessing
 * what will be studied, so it is deliberately not used as a "what will you study" ranker. Its
 * probability is used to surface the subject that habit alone will probably skip.
 *
 * Personalisation: a few epochs of SGD on the user's own past days, starting from the global
 * weights and pulled back towards them with L2 (exactly the notebook's `sgd` function).
 */
class SubjectRecommender(private val config: RecommenderConfig) {

    data class Factor(val label: String, val value: String, val effect: Double)

    data class Ranked(val subject: SubjectEntity, val probability: Double, val goalLeft: Double, val need: Double)

    data class Suggestion(
        val subject: SubjectEntity,
        /** P(studied today without a nudge). */
        val probability: Double,
        val goalLeft: Double,
        val personalised: Boolean,
        /** Contributions to the log-odds of p; positive = makes studying it today more likely. */
        val factors: List<Factor>,
        val ranking: List<Ranked>,
    )

    private fun features(h: StudyHistory, s: SubjectEntity, day: Long): DoubleArray {
        val last = h.lastStudied(s.id, day)
        val daysSince = if (last == null) 30.0 else minOf(30.0, (day - last).toDouble())
        val weekStart = LocalDate.ofEpochDay(day).startOfWeek().toEpochDay()
        val done = h.total(s.id, weekStart, day)
        val goalLeft = if (s.weeklyGoalMinutes <= 0) 0.0
        else ((s.weeklyGoalMinutes - done).toDouble() / s.weeklyGoalMinutes).coerceIn(0.0, 1.0)
        val first = h.firstDay ?: day
        val pastSameDays = (1..8).map { day - 7 * it }.filter { it >= first }
        val affinity = if (pastSameDays.isEmpty()) 0.0
        else pastSameDays.count { h.minutes(s.id, it) > 0 }.toDouble() / pastSameDays.size
        val total14 = h.total(day - 14, day)
        val share = if (total14 == 0) 0.0 else h.total(s.id, day - 14, day).toDouble() / total14
        val yesterday = if (h.minutes(s.id, day - 1) > 0) 1.0 else 0.0
        return doubleArrayOf(ln(1 + daysSince), goalLeft, affinity, share, yesterday)
    }

    private fun sigmoid(z: Double) = 1 / (1 + exp(-z))

    // Re-fitting is cheap but not free; the weights only change when the data or the day changes.
    private var cacheKey: Any? = null
    private var cached: Pair<DoubleArray, Boolean>? = null

    /** [bias, w1..w5] */
    @Synchronized
    private fun weightsFor(h: StudyHistory): Pair<DoubleArray, Boolean> {
        val key = listOf(h.todayEpoch, h.sessions.size, h.sessions.sumOf { it.id * 31 + it.durationMinutes + it.dateEpochDay },
            h.subjects.map { it.id to it.weeklyGoalMinutes })
        if (key == cacheKey) cached?.let { return it }
        return fit(h).also { cacheKey = key; cached = it }
    }

    private fun fit(h: StudyHistory): Pair<DoubleArray, Boolean> {
        val w0 = doubleArrayOf(config.intercept, *config.weights)
        val first = h.firstDay ?: return w0 to false
        // Train on at most the last 120 days: recent habits matter most and it bounds the cost.
        val start = maxOf(first + config.minHistoryDays, h.todayEpoch - 120)
        if (h.todayEpoch - start < 7) return w0 to false
        val w = w0.copyOf()
        var samples = 0
        repeat(config.epochs) {
            for (day in start until h.todayEpoch) for (s in h.subjects) {
                if (createdDay(s) > day) continue
                val x = doubleArrayOf(1.0, *features(h, s, day))
                val y = if (h.minutes(s.id, day) > 0) 1.0 else 0.0
                val err = sigmoid(x.indices.sumOf { w[it] * x[it] }) - y
                for (i in w.indices) w[i] -= config.lr * (err * x[i] + config.l2 * (w[i] - w0[i]))
                samples++
            }
        }
        AppLog.d(AppLog.ML, "Recommender personalised on $samples samples: w=${w.map { "%.3f".format(it) }}")
        return w to true
    }

    private fun createdDay(s: SubjectEntity): Long =
        Instant.ofEpochMilli(s.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    fun suggest(h: StudyHistory): Suggestion? {
        if (h.subjects.isEmpty()) return null
        val (w, personalised) = weightsFor(h)
        val today = h.todayEpoch
        val scored = h.subjects.map { s ->
            val x = features(h, s, today)
            val p = sigmoid(w[0] + x.indices.sumOf { w[it + 1] * x[it] })
            Triple(s, x, p)
        }
        // goal_left as a feature counts days *before* today; for the decision include today's minutes too.
        // A subject already studied today is not "at risk of being skipped today": its chance is 1, need 0.
        val weekStart = h.today.startOfWeek().toEpochDay()
        val ranked = scored.map { (s, _, modelP) ->
            val left = if (s.weeklyGoalMinutes <= 0) 0.0
            else ((s.weeklyGoalMinutes - h.total(s.id, weekStart, today + 1)).toDouble() / s.weeklyGoalMinutes).coerceIn(0.0, 1.0)
            val p = if (h.minutes(s.id, today) > 0) 1.0 else modelP
            Ranked(s, p, left, left * (1 - p))
        }.sortedByDescending { it.need }
        val top = ranked.firstOrNull { it.need > 0 } ?: run {
            AppLog.d(AppLog.ML, "Recommender: every weekly goal is met, no suggestion")
            return null
        }
        val (best, x, p) = scored.first { it.first.id == top.subject.id }
        val factors = listOf(
            Factor("Days since you last studied it",
                (kotlin.math.round(exp(x[0]) - 1)).toInt().let { if (it >= 30) "30+ days" else "$it days" }, w[1] * x[0]),
            Factor("Weekly goal still left", "${(x[1] * 100).toInt()}%", w[2] * x[1]),
            Factor("Your habit on this weekday", "${(x[2] * 100).toInt()}% of recent weeks", w[3] * x[2]),
            Factor("Share of your last 14 days", "${(x[3] * 100).toInt()}%", w[4] * x[3]),
            Factor("Studied yesterday", if (x[4] > 0) "yes" else "no", w[5] * x[4]),
        )
        AppLog.d(AppLog.ML, "Recommender: " + ranked.joinToString {
            "${it.subject.name}(p=%.2f, left=%.2f, need=%.2f)".format(it.probability, it.goalLeft, it.need)
        } + " -> '${best.name}' (personalised=$personalised)")
        return Suggestion(best, p, top.goalLeft, personalised, factors, ranked)
    }
}
