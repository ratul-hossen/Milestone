package com.milestone.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SubjectEntity::class, SessionEntity::class, ActiveTimerEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MilestoneDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun sessionDao(): SessionDao
    abstract fun timerDao(): TimerDao

    companion object {
        fun create(context: Context): MilestoneDatabase =
            Room.databaseBuilder(context, MilestoneDatabase::class.java, "milestone.db").build()
    }
}
