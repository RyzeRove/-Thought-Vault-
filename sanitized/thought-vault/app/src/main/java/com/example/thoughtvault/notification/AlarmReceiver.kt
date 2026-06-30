package com.example.thoughtvault.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 闹钟接收器 — AlarmManager 定时触发，发送提醒通知并安排下一次闹钟。
 */
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "闹钟触发")

        val hour = intent.getIntExtra("hour", 8)
        val minute = intent.getIntExtra("minute", 0)
        Log.d(TAG, "闹钟触发: ${hour}:${minute}")

        // 简化 hasConfig 检查：只要有用户名存储就认为已配置
        val hasConfig = context.getSharedPreferences(
            "thought_vault_settings", Context.MODE_PRIVATE
        ).all.any { (key, _) -> key.startsWith("webdav_username") || key.startsWith("__androidx_security_crypto") }

        if (!hasConfig) {
            Log.d(TAG, "未配置 NAS，跳过通知")
            return
        }

        try {
            NotificationHelper.showReminder(context)
            Log.d(TAG, "通知已发送")
        } catch (e: Exception) {
            Log.e(TAG, "通知发送失败: ${e.message}")
        }

        // 重新调度下一次（明天的同一时间）
        ReminderScheduler.scheduleForTime(context, hour, minute)
    }
}
