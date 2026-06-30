package com.example.thoughtvault.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.*

/**
 * 提醒任务调度器 — 基于 [AlarmManager] 精确闹钟。
 *
 * [AlarmManager.setExact] 保证在指定时间精确触发，不受系统后台限制。
 * 每次触发后自动安排下一次（明天同一时间），重启后通过 [BootReceiver] 恢复。
 */
object ReminderScheduler {

    private const val ACTION_REMINDER = "com.example.thoughtvault.REMINDER"
    private const val REQUEST_CODE_BASE = 3000

    val REMINDER_TIMES = listOf(
        11 to 30,  // 上午 11:30
        17 to 30,  // 下午 17:30
        21 to 30,  // 晚间 21:30
    )

    /** 注册所有提醒 */
    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences(
            "thought_vault_reminder", Context.MODE_PRIVATE
        )
        val enabled = prefs.getBoolean("reminder_enabled", true)
        android.util.Log.d("ReminderScheduler", "schedule() called, enabled=$enabled")
        if (!enabled) {
            android.util.Log.d("ReminderScheduler", "reminder disabled, skipping")
            return
        }

        REMINDER_TIMES.forEach { (hour, minute) ->
            android.util.Log.d("ReminderScheduler", "scheduling $hour:$minute")
            scheduleForTime(context, hour, minute)
        }
    }

    /** 为指定时间点安排下一次闹钟 */
    fun scheduleForTime(context: Context, hour: Int, minute: Int) {
        val target = calculateNextTime(hour, minute)
        val intent = Intent(ACTION_REMINDER).apply {
            setClassName(context.packageName, AlarmReceiver::class.java.name)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }
        val requestCode = REQUEST_CODE_BASE + hour * 60 + minute
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent, flags
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Android 12+ 精确闹钟需要用户手动授权，否则只能降级
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent
            )
        }
    }

    private fun calculateNextTime(hour: Int, minute: Int): Calendar {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return target
    }

    /** 取消所有提醒闹钟 */
    fun cancel(context: Context) {
        REMINDER_TIMES.forEach { (hour, minute) ->
            val intent = Intent(ACTION_REMINDER).apply {
                setClassName(context.packageName, AlarmReceiver::class.java.name)
            }
            val requestCode = REQUEST_CODE_BASE + hour * 60 + minute
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent, flags
            )
            pendingIntent?.cancel()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
        }
    }
}
