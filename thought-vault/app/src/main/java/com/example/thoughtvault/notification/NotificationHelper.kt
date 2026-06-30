package com.example.thoughtvault.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.thoughtvault.MainActivity
import com.example.thoughtvault.R

/**
 * 提醒通知管理。
 * 创建通知渠道 + 发送提醒。
 */
object NotificationHelper {

    const val CHANNEL_ID = "thought_reminder"
    const val CHANNEL_NAME = "记录提醒"
    const val NOTIFICATION_ID = 1001

    /** 创建默认提醒通知渠道（Android 8+ 必须） */
    fun createChannel(context: Context) {
        createChannel(
            context = context,
            channelId = CHANNEL_ID,
            channelName = CHANNEL_NAME,
            description = "定时提醒你记录想法",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        )
    }

    /** 用自定义参数创建通知渠道 */
    fun createChannel(
        context: Context,
        channelId: String,
        channelName: String,
        description: String,
        importance: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                this.description = description
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /** 发送一条提醒通知 */
    fun showReminder(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val messages = listOf(
            "💭 此刻有什么想法？记下来吧",
            "📝 今天的思考值得被记录",
            "✨ 随手记一笔，AI 帮你整理",
            "🧠 有什么灵感一闪而过？",
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("思维札记")
            .setContentText(messages.random())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
