package com.example.thoughtvault.data.local.dao

import androidx.room.*
import com.example.thoughtvault.data.local.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    /** 获取某天的所有条目，按时间排序 */
    @Query("SELECT * FROM entries WHERE date = :date ORDER BY time ASC")
    fun getEntriesByDate(date: String): Flow<List<EntryEntity>>

    /** 获取某天的所有条目（一次性） */
    @Query("SELECT * FROM entries WHERE date = :date ORDER BY time ASC")
    suspend fun getEntriesByDateOnce(date: String): List<EntryEntity>

    /** 获取所有有记录的日期 */
    @Query("SELECT DISTINCT date FROM entries ORDER BY date DESC")
    fun getAllDates(): Flow<List<String>>

    /** 插入或替换条目 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<EntryEntity>)

    /** 删除某天的所有条目 */
    @Query("DELETE FROM entries WHERE date = :date")
    suspend fun deleteByDate(date: String)

    /** 删除 7 天前的缓存 */
    @Query("DELETE FROM entries WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
