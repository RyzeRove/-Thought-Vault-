package com.example.thoughtvault.data.repository

import com.example.thoughtvault.data.remote.WebdavApi
import com.example.thoughtvault.domain.model.TodoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val api: WebdavApi,
) {
    /** 解析 tasks.md 文本为 TodoItem 列表 */
    fun parseTodos(md: String): List<TodoItem> {
        val result = mutableListOf<TodoItem>()
        var section: String? = null
        for (line in md.lines()) {
            when {
                line.startsWith("## 近期待办") -> section = "day"
                line.startsWith("## 长期计划") -> section = "long"
                line.startsWith("## 已完成") -> section = "done"
                else -> {
                    val m = Regex("""^- \[([ x])\] (.+?) \| (.+)$""").find(line)
                    if (m != null && section != null) {
                        val done = m.groupValues[1] == "x" || section == "done"
                        result.add(
                            TodoItem(
                                content = m.groupValues[2],
                                date = m.groupValues[3],
                                isLongTerm = section == "long",
                                isDone = done,
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    /** 将 TodoItem 列表序列化为 tasks.md 格式 */
    fun serializeTodos(items: List<TodoItem>): String {
        val day = items.filter { !it.isLongTerm && !it.isDone }
        val long = items.filter { it.isLongTerm && !it.isDone }
        val done = items.filter { it.isDone }
        val sb = StringBuilder()
        sb.appendLine("# 待办事项")
        sb.appendLine()
        sb.appendLine("## 近期待办")
        day.forEach { sb.appendLine("- [${if (it.isDone) "x" else " "}] ${it.content} | ${it.date}") }
        sb.appendLine()
        sb.appendLine("## 长期计划")
        long.forEach { sb.appendLine("- [${if (it.isDone) "x" else " "}] ${it.content} | ${it.date}") }
        sb.appendLine()
        sb.appendLine("## 已完成")
        done.forEach { sb.appendLine("- [x] ${it.content} | ${it.date}") }
        return sb.toString()
    }

    /** 从 NAS 加载待办 */
    suspend fun load(
        baseUrl: String, username: String, password: String,
    ): Result<List<TodoItem>> {
        return api.getTodosFile(baseUrl, username, password).map { md ->
            if (md != null) parseTodos(md) else emptyList()
        }
    }

    /** 保存到 NAS */
    suspend fun save(
        baseUrl: String, username: String, password: String, items: List<TodoItem>,
    ): Result<Unit> {
        return api.putTodosFile(baseUrl, username, password, serializeTodos(items))
    }
}
