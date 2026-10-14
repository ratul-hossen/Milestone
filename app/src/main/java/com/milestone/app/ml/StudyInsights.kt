package com.milestone.app.ml

import android.content.Context

/** Facade the ViewModels talk to; holds the three models and their exported configuration. */
class StudyInsights(context: Context) {
    private val config = ModelConfig.load(context)
    val forecaster = WeeklyForecaster(config.forecast)
    val recommender = SubjectRecommender(config.recommender)
    val duration = DurationSuggester(config.duration)
}
