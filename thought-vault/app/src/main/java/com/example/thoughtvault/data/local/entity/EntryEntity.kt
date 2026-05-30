package com.example.thoughtvault.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 实体 — 缓存最近 7 天的原始条目。
 */
@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: String,       // "YYYY-MM-DD"
    @ColumnInfo(name = "time") val time: String,        // "HH:mm"
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "category") val category: String? = null,
    @ColumnInfo(name = "refined_content") val refinedContent: String? = null,
    @ColumnInfo(name = "title") val title: String? = null,
)
