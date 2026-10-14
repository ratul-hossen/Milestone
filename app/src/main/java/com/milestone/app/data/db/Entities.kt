package com.milestone.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects", indices = [Index(value = ["name"], unique = true)])
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Unique, case-insensitive ("physics" == "Physics"). */
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** Index into [com.milestone.app.ui.theme.SubjectColors]. */
    val colorIndex: Int,
    val weeklyGoalMinutes: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(
        entity = SubjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["subjectId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("subjectId"), Index("dateEpochDay")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val durationMinutes: Int,
    /** Study day as [java.time.LocalDate.toEpochDay]. */
    val dateEpochDay: Long,
    /** Hour of day (0–23) the session started; a feature for the duration model. */
    val startHour: Int,
    /** When the entry was written, epoch millis. Shown as "Added to Milestone". */
    val loggedAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    /** "manual" or "timer". */
    val source: String = SOURCE_MANUAL,
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_TIMER = "timer"
    }
}

/**
 * The single running/paused focus timer (id is always 1). Time is derived from wall-clock
 * timestamps instead of a ticking counter, so it stays correct if the process is killed.
 */
@Entity(tableName = "active_timer")
data class ActiveTimerEntity(
    @PrimaryKey val id: Int = 1,
    val subjectId: Long,
    val mode: String,
    /** Countdown length for Pomodoro mode, null for the stopwatch. */
    val targetMinutes: Int?,
    /** Epoch millis of the last resume, or null while paused. */
    val runningSince: Long?,
    /** Time banked before the last pause. */
    val accumulatedMs: Long,
    val startedAt: Long,
) {
    fun elapsedMs(now: Long = System.currentTimeMillis()): Long =
        accumulatedMs + (runningSince?.let { now - it } ?: 0L)

    val isRunning: Boolean get() = runningSince != null

    companion object {
        const val MODE_STOPWATCH = "stopwatch"
        const val MODE_POMODORO = "pomodoro"
    }
}
