package com.milestone.app.timer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.milestone.app.MainActivity
import com.milestone.app.MilestoneApp
import com.milestone.app.R
import com.milestone.app.data.db.ActiveTimerEntity
import com.milestone.app.data.db.SessionEntity
import com.milestone.app.data.repo.StudyRepository
import com.milestone.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Focus timer (stopwatch or Pomodoro countdown).
 *
 * Design: the timer is *state*, not a ticking thread. Room stores when it was last resumed and how
 * much time was banked before, and the UI derives the elapsed time from the wall clock. This keeps it
 * correct across rotation, background and process death without a foreground service. While it runs, an
 * ongoing notification shows a system-drawn chronometer, and a Pomodoro end is signalled by an alarm.
 */
class FocusTimer(private val context: Context, private val repository: StudyRepository) {

    val state = repository.activeTimer

    suspend fun start(subjectId: Long, mode: String, targetMinutes: Int?) {
        val now = System.currentTimeMillis()
        val timer = ActiveTimerEntity(
            subjectId = subjectId, mode = mode, targetMinutes = targetMinutes,
            runningSince = now, accumulatedMs = 0, startedAt = now,
        )
        repository.saveTimer(timer)
        AppLog.i(AppLog.TIMER, "START mode=$mode target=${targetMinutes}min subject=$subjectId")
        onRunning(timer)
    }

    suspend fun pause() {
        val t = repository.getTimer() ?: return
        if (!t.isRunning) return
        val paused = t.copy(accumulatedMs = t.elapsedMs(), runningSince = null)
        repository.saveTimer(paused)
        AppLog.i(AppLog.TIMER, "PAUSE at ${paused.accumulatedMs / 1000}s")
        cancelAlarm(); showNotification(paused)
    }

    suspend fun resume() {
        val t = repository.getTimer() ?: return
        if (t.isRunning) return
        val running = t.copy(runningSince = System.currentTimeMillis())
        repository.saveTimer(running)
        AppLog.i(AppLog.TIMER, "RESUME from ${t.accumulatedMs / 1000}s")
        onRunning(running)
    }

    suspend fun switchSubject(subjectId: Long) {
        val t = repository.getTimer() ?: return
        repository.saveTimer(t.copy(subjectId = subjectId))
    }

    /**
     * Stops the timer and saves the studied time as a session. Returns the saved session, or null
     * when less than one minute was recorded (nothing worth saving).
     */
    suspend fun finish(notes: String = ""): SessionEntity? {
        val t = repository.getTimer() ?: return null
        val elapsed = t.elapsedMs()
        val capped = t.targetMinutes?.let { minOf(elapsed, it * 60_000L) } ?: elapsed
        val minutes = (capped / 60_000.0).roundToInt().coerceAtMost(600)
        if (minutes < 1) {
            repository.clearTimer(); cancelAlarm(); cancelNotification()
            AppLog.i(AppLog.TIMER, "STOP after ${elapsed / 1000}s - under 1 minute, not saved")
            return null
        }
        if (repository.getSubject(t.subjectId) == null) {
            // Subject was deleted meanwhile (should be prevented by the editor, but never crash or lose data silently).
            repository.clearTimer(); cancelAlarm(); cancelNotification()
            AppLog.w(AppLog.TIMER, "STOP: subject ${t.subjectId} no longer exists, session not saved")
            return null
        }
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(t.startedAt).atZone(zone)
        val session = SessionEntity(
            subjectId = t.subjectId,
            durationMinutes = minutes,
            // Dated on the day it started (a session past midnight belongs to the evening it began).
            dateEpochDay = start.toLocalDate().toEpochDay(),
            startHour = start.hour,
            notes = notes,
            source = SessionEntity.SOURCE_TIMER,
        )
        // Save first, then clear: if saving fails the timer (and the studied time) is still there.
        val id = repository.saveSession(session)
        repository.clearTimer(); cancelAlarm(); cancelNotification()
        AppLog.i(AppLog.TIMER, "STOP after ${elapsed / 1000}s -> saved session id=$id ${minutes}min")
        return session.copy(id = id)
    }

    suspend fun discard() {
        repository.clearTimer()
        cancelAlarm(); cancelNotification()
        AppLog.i(AppLog.TIMER, "DISCARD")
    }

    private suspend fun onRunning(t: ActiveTimerEntity) {
        showNotification(t)
        t.targetMinutes?.let { scheduleAlarm(t.runningSince!! + it * 60_000L - t.accumulatedMs) }
    }

    // ------------------------------------------------------------------ notifications & alarm

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent() = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TIMER, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private suspend fun showNotification(t: ActiveTimerEntity) {
        ensureChannels(context)
        if (!canNotify()) return
        val subject = repository.getSubject(t.subjectId)?.name ?: "Study"
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(if (t.isRunning) "Focusing on $subject" else "Paused · $subject")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        if (t.isRunning) {
            val target = t.targetMinutes
            if (target != null) {
                builder.setUsesChronometer(true).setChronometerCountDown(true)
                    .setWhen(t.runningSince!! + target * 60_000L - t.accumulatedMs)
            } else {
                builder.setUsesChronometer(true).setWhen(t.runningSince!! - t.accumulatedMs)
            }
        } else {
            builder.setContentText("${t.accumulatedMs / 60_000} min so far. Tap to resume.").setShowWhen(false)
        }
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_RUNNING, builder.build()) }
            .onFailure { AppLog.w(AppLog.TIMER, "notify failed", it) }
    }

    private fun cancelNotification() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_RUNNING)

    private fun alarmIntent() = PendingIntent.getBroadcast(
        context, 1, Intent(context, TimerDoneReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleAlarm(atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, alarmIntent())
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, alarmIntent())
        AppLog.d(AppLog.TIMER, "Alarm scheduled in ${(atMillis - System.currentTimeMillis()) / 1000}s (exact=$exact)")
    }

    private fun cancelAlarm() = context.getSystemService(AlarmManager::class.java).cancel(alarmIntent())

    companion object {
        const val CHANNEL_RUNNING = "timer_running"
        const val CHANNEL_DONE = "timer_done"
        const val NOTIFICATION_RUNNING = 1
        const val NOTIFICATION_DONE = 2

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_RUNNING, "Running timer", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shows the focus timer while it runs" })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Timer finished", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Alerts you when a Pomodoro session ends" })
        }
    }
}

/** Fired by AlarmManager when a Pomodoro countdown reaches zero. */
class TimerDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.i(AppLog.TIMER, "Pomodoro finished (alarm)")
        val app = context.applicationContext as MilestoneApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val t = app.repository.getTimer() ?: return@launch
                val subject = app.repository.getSubject(t.subjectId)?.name ?: "your subject"
                // Save automatically: the student shouldn't have to come back for the time to count.
                val saved = app.timer.finish()
                if (saved != null) app.messages.show(com.milestone.app.ui.UiMessage("Pomodoro done: saved ${saved.durationMinutes}m of $subject"))
                FocusTimer.ensureChannels(context)
                // The ongoing chronometer would otherwise keep counting below zero.
                NotificationManagerCompat.from(context).cancel(FocusTimer.NOTIFICATION_RUNNING)
                val canNotify = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (canNotify) {
                    val open = PendingIntent.getActivity(context, 0,
                        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TIMER, true)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    NotificationManagerCompat.from(context).notify(FocusTimer.NOTIFICATION_DONE,
                        NotificationCompat.Builder(context, FocusTimer.CHANNEL_DONE)
                            .setSmallIcon(R.drawable.ic_check_18)
                            .setContentTitle("Focus session complete")
                            .setContentText(if (saved != null) "Saved ${saved.durationMinutes} min of $subject. Tap to add notes."
                                else "Session ended.")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .setContentIntent(open)
                            .build())
                }
            } finally {
                pending.finish()
            }
        }
    }
}
