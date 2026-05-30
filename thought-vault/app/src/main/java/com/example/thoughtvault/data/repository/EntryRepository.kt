package com.example.thoughtvault.data.repository

import com.example.thoughtvault.data.local.dao.EntryDao
import com.example.thoughtvault.data.local.entity.EntryEntity
import com.example.thoughtvault.data.remote.WebdavApi
import com.example.thoughtvault.domain.model.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val api: WebdavApi,
    private val dao: EntryDao,
) {
    /** 保存一条新条目到 NAS（同时缓存到本地 Room） */
    suspend fun saveEntry(
        baseUrl: String,
        username: String,
        password: String,
        date: LocalDate,
        time: LocalTime,
        content: String,
    ): Result<Unit> {
        val result = api.saveEntry(baseUrl, username, password, date, time, content)
        if (result.isSuccess) {
            // 缓存到本地
            try {
                val entity = EntryEntity(
                    date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    time = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    content = content,
                )
                dao.insertEntries(listOf(entity))
            } catch (e: Exception) {
                Timber.w(e, "本地缓存失败，不影响主流程")
            }
        }
        return result
    }

    /** 获取今日已保存的条目（从本地缓存读取，快速显示） */
    fun getTodayEntries(date: LocalDate): Flow<List<Entry>> {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.getEntriesByDate(dateStr).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /** 从 NAS 获取某天的原始记录并解析为 Entry 列表 */
    suspend fun fetchRawEntries(
        baseUrl: String,
        username: String,
        password: String,
        date: LocalDate,
    ): Result<List<Entry>> {
        val result = api.getRawEntries(baseUrl, username, password, date)
        return result.fold(
            onSuccess = { md ->
                if (md != null) {
                    val entries = parseRawMarkdown(md)
                    // 更新本地缓存
                    try {
                        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        dao.insertEntries(entries.map { it.toEntity(dateStr) })
                        // 清理旧缓存
                        val cutoff = date.minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)
                        dao.deleteOlderThan(cutoff)
                    } catch (e: Exception) {
                        Timber.w(e, "缓存更新失败")
                    }
                    Result.success(entries)
                } else {
                    Result.success(emptyList())
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    /** 获取 AI 处理后的日报（纯文本 Markdown） */
    suspend fun fetchDailyReport(
        baseUrl: String,
        username: String,
        password: String,
        date: LocalDate,
    ): Result<String?> {
        return api.getDailyReport(baseUrl, username, password, date)
    }

    /** 获取所有有记录的历史日期（本地缓存） */
    fun getAllDates(): Flow<List<String>> = dao.getAllDates()

    /**
     * 解析原始 Markdown 文件为 Entry 列表。
     * 格式: ## HH:MM\n\ncontent
     */
    private fun parseRawMarkdown(md: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        // 按 ## HH:MM 分割
        val regex = Regex("^## (\\d{2}:\\d{2})\\s*$", RegexOption.MULTILINE)
        val matches = regex.findAll(md).toList()

        for (i in matches.indices) {
            val match = matches[i]
            val time = LocalTime.parse(match.groupValues[1], DateTimeFormatter.ofPattern("HH:mm"))
            val contentStart = match.range.last + 1
            val contentEnd = if (i + 1 < matches.size) matches[i + 1].range.first else md.length
            val content = md.substring(contentStart, contentEnd).trim()
            if (content.isNotBlank()) {
                entries.add(Entry(time = time, content = content))
            }
        }

        return entries
    }
}

// 扩展函数：领域模型 <-> 实体转换
private fun EntryEntity.toDomain() = Entry(
    time = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm")),
    content = content,
    category = category,
    refinedContent = refinedContent,
    title = title,
)

private fun Entry.toEntity(date: String) = EntryEntity(
    date = date,
    time = time.format(DateTimeFormatter.ofPattern("HH:mm")),
    content = content,
    category = category,
    refinedContent = refinedContent,
    title = title,
)
