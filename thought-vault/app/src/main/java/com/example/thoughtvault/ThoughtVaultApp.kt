package com.example.thoughtvault

import android.app.Application
import android.content.Intent
import com.example.thoughtvault.notification.NotificationHelper
import com.example.thoughtvault.notification.ReminderScheduler
import com.example.thoughtvault.push.MqttPushService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ThoughtVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // 创建通知渠道（提醒渠道 + 推送服务渠道）
        NotificationHelper.createChannel(this)
        // ⏰ 启动 MQTT 推送前台服务（兼容所有 Android 设备，不依赖 GMS）
        startPushService()
        // 保底：仍注册 AlarmManager 提醒（MQTT 断连时作为降级兜底）
        ReminderScheduler.schedule(this)
    }

    private fun startPushService() {
        val intent = Intent(this, MqttPushService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                startService(intent)
            }
        } catch (e: Exception) {
            Timber.w(e, "启动推送服务失败")
        }
    }
}
