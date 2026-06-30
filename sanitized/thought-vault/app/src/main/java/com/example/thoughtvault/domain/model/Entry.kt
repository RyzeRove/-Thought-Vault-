package com.example.thoughtvault.domain.model

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 一条思考记录。
 * @param time 记录时间
 * @param content 原始文字（口语化）
 * @param category 分类标签（AI 处理后才有值，原始记录为 null）
 * @param refinedContent AI 精炼后的文字（可选）
 * @param title AI 提取的标题（可选）
 */
data class Entry(
    val time: LocalTime,
    val content: String,
    val category: String? = null,
    val refinedContent: String? = null,
    val title: String? = null,
) {
    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

        fun formatTime(time: LocalTime): String = time.format(TIME_FORMAT)
    }

    /** 是否为 AI 处理后的条目 */
    val isRefined: Boolean get() = category != null && refinedContent != null

    /** 展示用文字（优先精炼版） */
    val displayContent: String get() = refinedContent ?: content
}
