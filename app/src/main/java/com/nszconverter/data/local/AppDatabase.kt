package com.nszconverter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [JobEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
}
