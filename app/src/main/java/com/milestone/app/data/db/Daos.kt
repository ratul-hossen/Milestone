package com.milestone.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY createdAt")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun get(id: Long): SubjectEntity?

    @Insert
    suspend fun insert(subject: SubjectEntity): Long

    @Update
    suspend fun update(subject: SubjectEntity)

    @Delete
    suspend fun delete(subject: SubjectEntity)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY dateEpochDay DESC, loggedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions WHERE subjectId = :subjectId")
    suspend fun countForSubject(subjectId: Long): Int

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)
}

@Dao
interface TimerDao {
    @Query("SELECT * FROM active_timer WHERE id = 1")
    fun observe(): Flow<ActiveTimerEntity?>

    @Query("SELECT * FROM active_timer WHERE id = 1")
    suspend fun get(): ActiveTimerEntity?

    @Upsert
    suspend fun upsert(timer: ActiveTimerEntity)

    @Query("DELETE FROM active_timer")
    suspend fun clear()
}
