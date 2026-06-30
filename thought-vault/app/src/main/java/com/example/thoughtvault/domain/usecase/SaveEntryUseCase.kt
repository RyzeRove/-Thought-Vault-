package com.example.thoughtvault.domain.usecase

import com.example.thoughtvault.data.repository.EntryRepository
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * 保存一条新条目。
 * 编排完整流程：锁 → 读 → 追加 → 写 → 解锁（在 WebdavApi 层实现）。
 */
class SaveEntryUseCase @Inject constructor(
    private val repository: EntryRepository,
) {
    suspend operator fun invoke(
        baseUrl: String,
        username: String,
        password: String,
        content: String,
    ): Result<Unit> {
        val now = LocalDate.now()
        val time = LocalTime.now()
        return repository.saveEntry(
            baseUrl = baseUrl,
            username = username,
            password = password,
            date = now,
            time = time,
            content = content.trim(),
        )
    }
}
