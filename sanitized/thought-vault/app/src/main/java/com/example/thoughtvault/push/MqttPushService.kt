package com.example.thoughtvault.push

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.thoughtvault.MainActivity
import com.example.thoughtvault.R
import com.example.thoughtvault.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * MQTT 推送前台服务 — 维持 MQTT 长连接，接收 NAS 推送的提醒。
 *
 * 前台服务显示持久通知"思维札记提醒服务运行中"（Android 8+ 强制），
 * 使用 IMPORTANCE_LOW 最小化打扰。
 */
class MqttPushService : Service() {

    companion object {
        const val CHANNEL_PUSH_SVC = "thought_vault_push_svc"
        const val NOTIFICATION_ID_PUSH = 2001
    }

    private val connectionManager = PushConnectionManager.instance
    // 用 SupervisorJob + IO — 服务生命周期足够长，不依赖 viewModelScope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        NotificationHelper.createChannel(
            this,
            channelId = CHANNEL_PUSH_SVC,
            channelName = "推送服务",
            description = "维持提醒推送连接所需的后台服务",
            importance = android.app.NotificationManager.IMPORTANCE_LOW,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID_PUSH, buildServiceNotification())

        connectionManager.onReminderReceived = { _, _, _ ->
            NotificationHelper.showReminder(this)
        }

        connectionManager.connect(serviceScope)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connectionManager.disconnect()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildServiceNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_PUSH_SVC)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("思维札记")
            .setContentText("提醒服务运行中")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
