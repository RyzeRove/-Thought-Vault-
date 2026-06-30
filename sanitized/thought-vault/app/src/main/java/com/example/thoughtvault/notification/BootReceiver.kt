package com.example.thoughtvault.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 设备重启后重新注册每日提醒。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.schedule(context)
        }
    }
}
