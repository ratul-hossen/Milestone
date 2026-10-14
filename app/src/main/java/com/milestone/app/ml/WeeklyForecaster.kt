package com.milestone.app.ml

import com.milestone.app.util.AppLog
import com.milestone.app.util.startOfWeek
import kotlin.math.max

/**
 * Predicts how many minutes the user will have studied by the end of this week (Mon–Sun).
 * Model and training: ml/01_weekly_goal_forecast.ipynb (ridge regression on real OULAD data).
 *
 *   forecast = soFar + max(0, w · f)
 *   f_hist = left · (mean daily minutes in the 14 days before this week) · 7
 *   f_last = left · last week's total
 *   f_pace = left · soFar / frac          where frac = (dayIndex + 1) / 7, left = 1 − frac
 *
 * The model was trained on rows taken at the END of a day, so on device the features are computed as
 * of the end of YESTERDAY (today is still in progress), and today's minutes are simply added:
 *   forecast = max(soFar, soFarUntilYesterday + max(0, w · f(yesterday)))
 * On Monday there is no finished day this week yet, so the forecast is the recent daily average × 7.
 * No forecast is shown until there are 14 days of history (the notebook never trains on less).
 *
 * Personalisation: when the user has ≥ minPersonalWeeks complete weeks, refit
 *   w = (XᵀX + λI)⁻¹ (Xᵀy + λ w₀)   with λ = λ_rel · Σx² / rows
 * i.e. ridge regression pulled towards the global weights w₀ (same as the notebook).
 */
class WeeklyForecaster(private val config: ForecastConfig) {
    companion object {
        const val MIN_HISTORY_DAYS = 14
    }

    data class Contribution(val label: String, val explanation: String, val minutes: Double)

    data class Forecast(
        val soFarMinutes: Int,
        val predictedTotalMinutes: Int,
        val goalMinutes: Int,
        val personalised: Boolean,
        val weeksUsed: Int,
        val contributions: List<Contribution>,
    ) {
        val onTrack: Boolean get() = goalMinutes > 0 && predictedTotalMinutes >= goalMinutes
    }

    /** Mean daily minutes over the 14 days before [weekStart], counting only days since the first log. */
    private fun avg14(h: StudyHistory, weekStart: Long): Double {
        val from = maxOf(weekStart - 14, h.firstDay ?: weekStart)
        val days = weekStart - from
        return if (days <= 0) 0.0 else h.total(from, weekStart).toDouble() / days
    }

    private fun features(h: StudyHistory, weekStart: Long, dayIndex: Int): DoubleArray {
        val frac = (dayIndex + 1) / 7.0
        val left = 1 - frac
        val avg14 = avg14(h, weekStart)
        val last = h.total(weekStart - 7, weekStart).toDouble()
        val soFar = h.total(weekStart, weekStart + dayIndex + 1).toDouble()
        return doubleArrayOf(left * avg14 * 7, left * last, left * soFar / frac)
    }

    fun forecast(h: StudyHistory, goalMinutes: Int): Forecast? {
        val first = h.firstDay ?: return null
        if (h.todayEpoch - first < MIN_HISTORY_DAYS) {
            AppLog.d(AppLog.ML, "Forecast hidden: only ${h.todayEpoch - first} days of history (< $MIN_HISTORY_DAYS)")
            return null
        }
        val weekStart = h.today.startOfWeek().toEpochDay()
        val dayIndex = (h.todayEpoch - weekStart).toInt()
        val soFar = h.total(weekStart, h.todayEpoch + 1)
        if (dayIndex == 0) {
            // Monday: no finished day this week yet; use the recent rhythm (avg of the last 14 days × 7).
            val usual = avg14(h, weekStart) * 7
            return Forecast(soFar, max(soFar.toDouble(), usual).toInt(), goalMinutes, false, 0,
                listOf(Contribution("Your usual rhythm", "average of your last 14 days × 7", usual)))
        }
        val (weights, weeks) = personalWeights(h, weekStart)
        val f = features(h, weekStart, dayIndex - 1)               // as of the end of yesterday
        val untilYesterday = h.total(weekStart, h.todayEpoch)
        val parts = f.indices.map { weights[it] * f[it] }
        val predicted = max(soFar.toDouble(), untilYesterday + max(0.0, parts.sum()))
        val result = Forecast(
            soFarMinutes = soFar,
            predictedTotalMinutes = predicted.toInt(),
            goalMinutes = goalMinutes,
            personalised = weeks > 0,
            weeksUsed = weeks,
            contributions = listOf(
                Contribution("Your usual rhythm", "based on your last 14 days", parts[0]),
                Contribution("Last week", "how much you did last week", parts[1]),
                Contribution("This week's pace", "what you logged this week up to yesterday", parts[2]),
            ),
        )
        AppLog.d(AppLog.ML, "Forecast day=$dayIndex (features as of yesterday) f=${f.map { "%.0f".format(it) }} " +
            "w=${weights.map { "%.3f".format(it) }} soFar=$soFar -> ${result.predictedTotalMinutes}min " +
            "(goal=$goalMinutes, personalised=${result.personalised}, weeks=$weeks)")
        return result
    }

    /** Returns (weights, number of personal weeks used). */
    private fun personalWeights(h: StudyHistory, currentWeekStart: Long): Pair<DoubleArray, Int> {
        val first = h.firstDay ?: return config.weights to 0
        val firstWeek = java.time.LocalDate.ofEpochDay(first).startOfWeek().toEpochDay()
        // Complete weeks with two weeks of history before them (the notebook trains from week 2 on).
        val weekStarts = generateSequence(firstWeek + 14) { it + 7 }.takeWhile { it < currentWeekStart }.toList()
        if (weekStarts.size < config.minPersonalWeeks) return config.weights to 0

        val xs = ArrayList<DoubleArray>()
        val ys = ArrayList<Double>()
        for (ws in weekStarts) for (d in 0..6) {
            xs += features(h, ws, d)
            ys += (h.total(ws, ws + 7) - h.total(ws, ws + d + 1)).toDouble()
        }
        val n = config.weights.size
        val lambda = config.lambdaPersonalRel * xs.sumOf { r -> r.sumOf { it * it } } / xs.size + 1e-9
        val a = Array(n) { i -> DoubleArray(n) { j -> xs.sumOf { it[i] * it[j] } + if (i == j) lambda else 0.0 } }
        val b = DoubleArray(n) { i -> xs.indices.sumOf { r -> xs[r][i] * ys[r] } + lambda * config.weights[i] }
        val w = LinearAlgebra.solve(a, b) ?: return config.weights to 0
        return w to weekStarts.size
    }
}

internal object LinearAlgebra {
    /** Gaussian elimination with partial pivoting; null if the system is singular. */
    fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { i -> a[i].copyOf(n + 1).also { it[n] = b[i] } }
        for (col in 0 until n) {
            val pivot = (col until n).maxBy { kotlin.math.abs(m[it][col]) }
            if (kotlin.math.abs(m[pivot][col]) < 1e-12) return null
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (r in 0 until n) if (r != col) {
                val factor = m[r][col] / m[col][col]
                for (c in col..n) m[r][c] -= factor * m[col][c]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }
}
