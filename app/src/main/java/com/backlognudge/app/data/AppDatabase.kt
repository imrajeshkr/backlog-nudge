package com.backlognudge.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [BacklogItem::class, NudgeEvent::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun backlogDao(): BacklogDao
    abstract fun nudgeDao(): NudgeDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "backlog_nudge.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
