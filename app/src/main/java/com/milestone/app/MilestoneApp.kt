package com.milestone.app

import android.app.Application
import com.milestone.app.data.db.MilestoneDatabase
import com.milestone.app.data.repo.StudyRepository
import com.milestone.app.ml.StudyInsights
import com.milestone.app.timer.FocusTimer
import com.milestone.app.ui.MessageBus
import com.milestone.app.ui.UserPrefs
import com.milestone.app.util.AppLog

/** Manual dependency injection: one instance of each dependency for the whole process. */
class MilestoneApp : Application() {
    lateinit var repository: StudyRepository
        private set
    lateinit var insights: StudyInsights
        private set
    lateinit var timer: FocusTimer
        private set
    val messages = MessageBus()
    val prefs by lazy { UserPrefs(this) }

    override fun onCreate() {
        super.onCreate()
        val db = MilestoneDatabase.create(this)
        repository = StudyRepository(db)
        insights = StudyInsights(this)
        timer = FocusTimer(this, repository)
        AppLog.i(AppLog.UI, "Milestone started (offline, local Room database)")
    }
}
