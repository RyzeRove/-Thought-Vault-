package com.example.thoughtvault.domain.usecase

import com.example.thoughtvault.data.repository.EntryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 加载有记录的历史日期列表。
 */
class LoadHistoryUseCase @Inject constructor(
    private val repository: EntryRepository,
) {
    operator fun invoke(): Flow<List<String>> {
        return repository.getAllDates()
    }
}
