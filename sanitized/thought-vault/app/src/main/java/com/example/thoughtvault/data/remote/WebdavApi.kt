package com.example.thoughtvault.data.remote

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * WebDAV 高级 API — 定义 URL 构造和文件操作的上层语义。
 */
@Singleton
class WebdavApi @Inject constructor(
    private val client: WebdavClient
) {
    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    }

    /** 构建完整的 WebDAV URL */
    fun buildUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        val p = path.trimStart('/')
        return "$base/$p"
    }

    /** 原始记录文件路径: raw/YYYY/MM/YYYY-MM-DD.md */
    fun rawFilePath(date: LocalDate): String {
        val year = date.year
        val month = String.format("%02d", date.monthValue)
        val day = date.format(DATE_FMT)
        return "raw/$year/$month/$day.md"
    }

    /** 原始记录文件所在目录路径: raw/YYYY/MM/ */
    fun rawDirPath(date: LocalDate): String {
        val year = date.year
        val month = String.format("%02d", date.monthValue)
        return "raw/$year/$month/"
    }

    /** 锁文件路径 */
    fun lockFilePath(): String = ".sync/write.lock"

    /** 日报文件路径: daily/YYYY/MM/YYYY-MM-DD.md */
    fun dailyFilePath(date: LocalDate): String {
        val year = date.year
        val month = String.format("%02d", date.monthValue)
        return "daily/$year/$month/${date.format(DATE_FMT)}.md"
    }

    /**
     * 递归创建目录路径。WebDAV MKCOL 一次只能创建一级，
     * 所以从根开始逐级确保每一级目录都存在。
     */
    private suspend fun mkcolRecursive(
        baseUrl: String,
        username: String,
        password: String,
        fullPath: String,
    ) {
        // 去掉首尾斜杠，按 / 拆分为各级
        val parts = fullPath.trim('/').split("/")
        var current = ""
        for (part in parts) {
            current = if (current.isEmpty()) part else "$current/$part"
            val dirUrl = buildUrl(baseUrl, current)
            client.mkcol(dirUrl, username, password)
        }
    }

    /**
     * 保存一条新条目（完整的读-改-写 + 锁流程）。
     */
    suspend fun saveEntry(
        baseUrl: String,
        username: String,
        password: String,
        date: LocalDate,
        time: LocalTime,
        content: String,
    ): Result<Unit> {
        val lockUrl = buildUrl(baseUrl, lockFilePath())
        val fileUrl = buildUrl(baseUrl, rawFilePath(date))

        // 1. 确保 .sync 目录存在（WebDAV PUT 要求父目录已存在）
        mkcolRecursive(baseUrl, username, password, ".sync")

        // 2. 获取锁（指数退避重试，最多 10 秒）
        //    如果 3 次后仍失败，检查是否为僵尸锁（>60 秒未释放的锁强制清理）
        var lockAcquired = false
        for (attempt in 1..5) {
            val lockResult = client.putEmpty(lockUrl, username, password)
            if (lockResult.isSuccess) {
                lockAcquired = true
                break
            }
            if (attempt == 3) {
                // 第 3 次失败 → 可能是僵尸锁，强制清理
                Timber.w("锁获取失败 3 次，尝试强制清理僵尸锁...")
                client.delete(lockUrl, username, password)
            }
            if (attempt < 5) {
                val delayMs = 250L * (1L shl (attempt - 1))  // 250, 500, 1000, 2000
                kotlinx.coroutines.delay(delayMs)
            }
        }
        if (!lockAcquired) {
            return Result.failure(Exception("无法获取写入锁，请稍后重试"))
        }

        try {
            // 3. 递归创建目录（WebDAV MKCOL 一次只能建一级）
            mkcolRecursive(baseUrl, username, password, rawDirPath(date))

            // 4. 读取当日已有内容
            val getResult = client.get(fileUrl, username, password)
            if (getResult.isFailure) {
                return Result.failure(getResult.exceptionOrNull()!!)
            }
            val existing = getResult.getOrNull()

            // 4. 构建新内容
            val newContent = if (existing != null) {
                // 追加新条目
                val entryBlock = buildEntryBlock(time, content)
                existing.trimEnd() + "\n\n" + entryBlock
            } else {
                // 新的一天
                val header = "# ${date.format(DATE_FMT)} 原始记录\n\n---\n"
                header + buildEntryBlock(time, content)
            }

            // 5. 写回
            val putResult = client.put(fileUrl, newContent, username, password)
            if (putResult.isFailure) {
                return Result.failure(putResult.exceptionOrNull()!!)
            }

            return Result.success(Unit)
        } finally {
            // 6. 释放锁
            client.delete(lockUrl, username, password)
        }
    }

    /**
     * 读取某天的原始记录。
     */
    suspend fun getRawEntries(
        baseUrl: String,
        username: String,
        password: String,
        date: LocalDate,
    ): Result<String?> {
        val fileUrl = buildUrl(baseUrl, rawFilePath(date))
        return client.get(fileUrl, username, password)
    }

    /**
     * 读取 AI 处理后的日报。
     */
    suspend fun getDailyReport(
        baseUrl: String,
        username: String,
        password: String,
        date: LocalDate,
    ): Result<String?> {
        val fileUrl = buildUrl(baseUrl, dailyFilePath(date))
        return client.get(fileUrl, username, password)
    }

    /**
     * 列出某月所有有记录的日期。
     */
    suspend fun listMonthDays(
        baseUrl: String,
        username: String,
        password: String,
        year: Int,
        month: Int,
    ): Result<String> {
        val path = "raw/$year/${String.format("%02d", month)}/"
        val url = buildUrl(baseUrl, path)
        return client.propfind(url, username, password, depth = "1")
    }

    /** 读取待办文件 */
    suspend fun getTodosFile(
        baseUrl: String, username: String, password: String,
    ): Result<String?> {
        val url = buildUrl(baseUrl, "todos/tasks.md")
        return client.get(url, username, password)
    }

    /** 汇总目录 */
    fun summaryDirPath(type: String): String = "$type/"

    /** 周报文件路径 */
    fun weeklyFilePath(week: String): String {
        val y = week.substring(0, 4)
        return "weekly/$y/$week.md"
    }

    /** 月报文件路径 */
    fun monthlyFilePath(month: String): String {
        val y = month.substring(0, 4)
        return "monthly/$y/$month.md"
    }

    /** 季报文件路径 */
    fun quarterlyFilePath(q: String): String {
        val y = q.substring(0, 4)
        return "quarterly/$y/$q.md"
    }

    /** 年报文件路径 */
    fun yearlyFilePath(year: String): String = "yearly/$year/$year.md"

    /** PROPFIND 列出周报 */
    suspend fun listWeeklyFiles(
        baseUrl: String, username: String, password: String,
    ): Result<String> {
        val url = buildUrl(baseUrl, "weekly/")
        return client.propfind(url, username, password, depth = "infinity")
    }

    /** PROPFIND 列出月报 */
    suspend fun listMonthlyFiles(
        baseUrl: String, username: String, password: String,
    ): Result<String> {
        val url = buildUrl(baseUrl, "monthly/")
        return client.propfind(url, username, password, depth = "infinity")
    }

    /** PROPFIND 列出季报 */
    suspend fun listQuarterlyFiles(
        baseUrl: String, username: String, password: String,
    ): Result<String> {
        val url = buildUrl(baseUrl, "quarterly/")
        return client.propfind(url, username, password, depth = "infinity")
    }

    /** PROPFIND 列出年报 */
    suspend fun listYearlyFiles(
        baseUrl: String, username: String, password: String,
    ): Result<String> {
        val url = buildUrl(baseUrl, "yearly/")
        return client.propfind(url, username, password, depth = "infinity")
    }

    /** GET 汇总文件内容 */
    suspend fun getSummaryFile(
        baseUrl: String, username: String, password: String, path: String,
    ): Result<String?> {
        val url = buildUrl(baseUrl, path)
        return client.get(url, username, password)
    }

    /** 写入待办文件 */
    suspend fun putTodosFile(
        baseUrl: String, username: String, password: String, content: String,
    ): Result<Unit> {
        val url = buildUrl(baseUrl, "todos/tasks.md")
        // 确保目录存在
        mkcolRecursive(baseUrl, username, password, "todos")
        return client.put(url, content, username, password)
    }

    /** 写入原始记录文件（全量覆盖） */
    suspend fun putRawFile(
        baseUrl: String, username: String, password: String,
        date: LocalDate, content: String,
    ): Result<Unit> {
        val fileUrl = buildUrl(baseUrl, rawFilePath(date))
        mkcolRecursive(baseUrl, username, password, rawDirPath(date))
        return client.put(fileUrl, content, username, password)
    }

    /** 写入日报文件（全量覆盖） */
    suspend fun putDailyReport(
        baseUrl: String, username: String, password: String,
        date: LocalDate, content: String,
    ): Result<Unit> {
        val fileUrl = buildUrl(baseUrl, dailyFilePath(date))
        // 确保目录存在
        val year = date.year
        val month = String.format("%02d", date.monthValue)
        mkcolRecursive(baseUrl, username, password, "daily/$year/$month")
        return client.put(fileUrl, content, username, password)
    }

    /**
     * 测试连接。
     */
    suspend fun testConnection(
        baseUrl: String,
        username: String,
        password: String,
    ): Result<Boolean> {
        return client.testConnection(baseUrl, username, password)
    }

    /** 构建一条条目的 Markdown 块 */
    private fun buildEntryBlock(time: LocalTime, content: String): String {
        val timeStr = time.format(TIME_FMT)
        return "## $timeStr\n\n$content"
    }
}
