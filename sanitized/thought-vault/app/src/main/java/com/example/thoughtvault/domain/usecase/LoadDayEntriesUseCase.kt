package com.example.thoughtvault.domain.usecase

import com.example.thoughtvault.data.repository.EntryRepository
import com.example.thoughtvault.domain.model.Entry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

/**
 * 加载某天的条目（优先本地缓存）。
 */
class LoadDayEntriesUseCase @Inject constructor(
    private val repository: EntryRepository,
) {
    /** 从本地缓存加载（响应式） */
    operator fun invoke(date: LocalDate): Flow<List<Entry>> {
        return repository.getTodayEntries(date)
    }

    /** 从 NAS 强制刷新 */
    suspend fun refresh(
        baseUrl: String,
        username: String,
        password: String,
        date: LocalDate,
    ): Result<List<Entry>> {
        return repository.fetchRawEntries(baseUrl, username, password, date)
    }
}
