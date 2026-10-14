package com.milestone.app.ml

import android.content.Context
import com.milestone.app.util.AppLog
import org.json.JSONObject

/**
 * Parameters exported by the notebooks in /ml into app/src/main/assets/ml/.
 * If an asset is missing or malformed the app falls back to these defaults (and logs it),
 * so a bad export can never crash the app.
 */
data class ForecastConfig(
    val weights: DoubleArray = doubleArrayOf(0.5, 0.2, 0.3),
    val lambdaPersonalRel: Double = 1.0,
    val minPersonalWeeks: Int = 3,
)

data class RecommenderConfig(
    val intercept: Double = -1.5,
    val weights: DoubleArray = doubleArrayOf(-0.5, 0.5, 2.0, 2.0, 1.0),
    val lr: Double = 0.02,
    val epochs: Int = 2,
    val l2: Double = 0.1,
    val minHistoryDays: Int = 14,
)

data class DurationConfig(
    val k: Int = 5,
    val wSubject: Double = 3.0,
    val wHour: Double = 1.5,
    val wWeekday: Double = 0.3,
    val wAge: Double = 0.1,
    val epsilon: Double = 0.05,
    val minHistory: Int = 3,
    val maxHistory: Int = 400,
    val fallbackMinutes: Int = 45,
    /** "median" or "mean" of the k neighbours; chosen in the notebook on held-out data. */
    val aggregate: String = "median",
)

class ModelConfig(
    val forecast: ForecastConfig,
    val recommender: RecommenderConfig,
    val duration: DurationConfig,
) {
    companion object {
        fun load(context: Context): ModelConfig {
            fun json(name: String): JSONObject? = runCatching {
                JSONObject(context.assets.open("ml/$name").bufferedReader().use { it.readText() })
            }.onFailure { AppLog.w(AppLog.ML, "Could not read ml/$name, using defaults", it) }.getOrNull()

            fun JSONObject.doubles(key: String) =
                getJSONArray(key).let { a -> DoubleArray(a.length()) { a.getDouble(it) } }

            val f = json("weekly_forecast.json")?.let {
                ForecastConfig(it.doubles("weights"), it.getDouble("lambda_personal_rel"), it.getInt("min_personal_weeks"))
            } ?: ForecastConfig()
            val r = json("next_subject.json")?.let {
                val sgd = it.getJSONObject("sgd")
                RecommenderConfig(it.getDouble("intercept"), it.doubles("weights"), sgd.getDouble("lr"),
                    sgd.getInt("epochs"), sgd.getDouble("l2"), it.getInt("min_history_days"))
            } ?: RecommenderConfig()
            val d = json("duration_knn.json")?.let {
                DurationConfig(it.getInt("k"), it.getDouble("w_subject"), it.getDouble("w_hour"),
                    it.getDouble("w_weekday"), it.getDouble("w_age"), it.getDouble("epsilon"),
                    it.getInt("min_history"), it.getInt("max_history"), it.getInt("fallback_minutes"),
                    it.optString("aggregate", "median"))
            } ?: DurationConfig()

            AppLog.i(AppLog.ML, "Models loaded | forecast w=${f.weights.toList()} | " +
                "recommender b=${r.intercept} w=${r.weights.toList()} | knn k=${d.k} ${d.aggregate}")
            return ModelConfig(f, r, d)
        }
    }
}
