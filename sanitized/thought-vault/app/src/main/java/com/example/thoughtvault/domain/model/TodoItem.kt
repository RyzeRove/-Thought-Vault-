package com.example.thoughtvault.domain.model

/**
 * 待办事项。
 * @param content 内容
 * @param date 提取日期
 * @param isLongTerm true=长期计划，false=近期待办
 * @param isDone 是否已完成
 */
data class TodoItem(
    val content: String,
    val date: String,
    val isLongTerm: Boolean = false,
    val isDone: Boolean = false,
)
