package com.milestone.app.data.repo

import com.milestone.app.data.db.ActiveTimerEntity
import com.milestone.app.data.db.MilestoneDatabase
import com.milestone.app.data.db.SessionEntity
import com.milestone.app.data.db.SubjectEntity
import com.milestone.app.util.AppLog
import kotlinx.coroutines.flow.Flow

/** The single source of truth for the UI. Everything is local (Room); there is no network layer. */
class StudyRepository(private val db: MilestoneDatabase) {

    val subjects: Flow<List<SubjectEntity>> = db.subjectDao().observeAll()
    val sessions: Flow<List<SessionEntity>> = db.sessionDao().observeAll()
    val activeTimer: Flow<ActiveTimerEntity?> = db.timerDao().observe()

    fun session(id: Long): Flow<SessionEntity?> = db.sessionDao().observe(id)

    suspend fun getSession(id: Long) = db.sessionDao().get(id)
    suspend fun getSubject(id: Long) = db.subjectDao().get(id)
    suspend fun sessionCount(subjectId: Long) = db.sessionDao().countForSubject(subjectId)

    suspend fun saveSubject(subject: SubjectEntity): Long =
        if (subject.id == 0L) {
            db.subjectDao().insert(subject).also {
                AppLog.i(AppLog.DB, "INSERT subject id=$it name='${subject.name}' goal=${subject.weeklyGoalMinutes}min")
            }
        } else {
            db.subjectDao().update(subject)
            AppLog.i(AppLog.DB, "UPDATE subject id=${subject.id} name='${subject.name}' goal=${subject.weeklyGoalMinutes}min")
            subject.id
        }

    suspend fun deleteSubject(subject: SubjectEntity) {
        db.subjectDao().delete(subject)
        AppLog.i(AppLog.DB, "DELETE subject id=${subject.id} (its sessions cascade)")
    }

    suspend fun saveSession(session: SessionEntity): Long =
        if (session.id == 0L) {
            db.sessionDao().insert(session).also {
                AppLog.i(AppLog.DB, "INSERT session id=$it subject=${session.subjectId} " +
                    "${session.durationMinutes}min day=${session.dateEpochDay} source=${session.source}")
            }
        } else {
            db.sessionDao().update(session)
            AppLog.i(AppLog.DB, "UPDATE session id=${session.id} ${session.durationMinutes}min")
            session.id
        }

    suspend fun deleteSession(session: SessionEntity) {
        db.sessionDao().delete(session)
        AppLog.i(AppLog.DB, "DELETE session id=${session.id}")
    }

    /** Re-inserts a deleted session with its original id (used by "Undo"). */
    suspend fun restoreSession(session: SessionEntity) {
        db.sessionDao().insert(session)
        AppLog.i(AppLog.DB, "RESTORE session id=${session.id}")
    }

    suspend fun getTimer() = db.timerDao().get()
    suspend fun saveTimer(timer: ActiveTimerEntity) = db.timerDao().upsert(timer)
    suspend fun clearTimer() = db.timerDao().clear()
}
