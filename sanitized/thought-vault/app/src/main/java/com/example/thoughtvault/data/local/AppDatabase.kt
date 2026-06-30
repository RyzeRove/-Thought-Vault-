package com.example.thoughtvault.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.thoughtvault.data.local.dao.EntryDao
import com.example.thoughtvault.data.local.entity.EntryEntity

@Database(
    entities = [EntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
}
