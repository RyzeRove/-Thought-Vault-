package com.example.thoughtvault.push

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.example.thoughtvault.notification.NotificationHelper

/**
 * 电池优化 / 自启动权限引导工具。
 *
 * 国产 ROM 即使有前台服务也经常被后台杀死，需要引导用户：
 * 1. 关闭电池优化（Doze 白名单）
 * 2. 开启自启动（华为/小米/OPPO/vivo 各自路径不同）
 */
object PermissionGuideHelper {

    /** 构建一个指向系统电池优化设置页的 Intent */
    fun batteryOptimizationIntent(): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:com.example.thoughtvault")
        }
    }

    /** 是否已加入电池优化白名单 */
    fun isIgnoringBatteryOptimizations(appContext: android.content.Context): Boolean {
        val pm = appContext.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations("com.example.thoughtvault")
    }

    /** 自动启动设置 Intent（跳转到系统应用详情页，国产 ROM 自启动入口通常在此） */
    fun autoStartSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:com.example.thoughtvault")
        }
    }

    /** 检查通知权限是否已授予 */
    fun hasNotificationPermission(appContext: android.content.Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 12 及以下无需运行时权限
        }
    }
}
