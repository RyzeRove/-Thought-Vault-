package com.example.thoughtvault.push

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 权限引导页面 — 帮助用户配置电池优化白名单 + 自启动权限，
 * 确保 MQTT 推送前台服务不被系统杀死。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val isBatteryOptimized = remember { mutableStateOf(!PermissionGuideHelper.isIgnoringBatteryOptimizations(context)) }
    val hasNotification = remember { mutableStateOf(PermissionGuideHelper.hasNotificationPermission(context)) }

    // 每次进入页面刷新状态
    LaunchedEffect(Unit) {
        isBatteryOptimized.value = !PermissionGuideHelper.isIgnoringBatteryOptimizations(context)
        hasNotification.value = PermissionGuideHelper.hasNotificationPermission(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 标题说明
            Text(
                "为确保提醒及时送达，请完成以下设置",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 1. 通知权限
            if (!hasNotification.value) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("通知权限未开启", fontWeight = FontWeight.Bold)
                                Text("需要通知权限才能显示提醒", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:com.example.thoughtvault")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("前往设置") }
                    }
                }
            }

            // 2. 电池优化
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBatteryOptimized.value) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isBatteryOptimized.value) Icons.Default.BatteryAlert else Icons.Default.BatteryFull,
                            contentDescription = null,
                            tint = if (isBatteryOptimized.value) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "关闭电池优化",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (isBatteryOptimized.value) "⚠️ 未关闭：后台服务可能被系统休眠"
                                else "✅ 已关闭电池优化",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isBatteryOptimized.value) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (isBatteryOptimized.value) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                context.startActivity(PermissionGuideHelper.batteryOptimizationIntent())
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("关闭电池优化") }
                    }
                }
            }

            // 3. 自启动引导
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("开启自启动", fontWeight = FontWeight.Bold)
                            Text(
                                "不同手机路径不同，通常在「设置 > 应用 > 思维札记 > 自启动」",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(PermissionGuideHelper.autoStartSettingsIntent())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("前往应用设置") }
                }
            }

            // 使用说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📋 各品牌自启动路径", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        """• 华为：手机管家 > 启动管理 > 思维札记 > 允许自启动
• 小米：安全中心 > 应用管理 > 思维札记 > 自启动
• OPPO：设置 > 应用 > 思维札记 > 自启动
• vivo：i管家 > 应用管理 > 权限管理 > 自启动
• 三星：设置 > 应用程序 > 思维札记 > 电池 > 不受限制""",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
