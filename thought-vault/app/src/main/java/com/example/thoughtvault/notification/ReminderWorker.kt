package com.example.thoughtvault.notification

import android.content.Context
import androidx.work.*

/**
 * WorkManager Worker — 兼容保留，新方案使用 AlarmManager，不再依赖此 Worker。
 * 此文件保留作为降级兜底，实际提醒已由 [AlarmReceiver] 处理。
 */
@Deprecated("Use AlarmReceiver + ReminderScheduler.scheduleForTime instead")
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.success()
}
