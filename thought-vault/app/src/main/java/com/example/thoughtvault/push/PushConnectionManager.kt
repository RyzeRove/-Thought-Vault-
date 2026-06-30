package com.example.thoughtvault.push

import android.util.Log
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

/**
 * MQTT 连接管理器 — 维护到 NAS Mosquitto 的长连接。
 *
 * 通过 nginx 反代的 wss://<your-nas-domain>:<your-mqtt-wss-port>/mqtt 连接，
 * 复用现有端口和 LE 证书。
 *
 * 连接断开后指数退避重连（5s → 10s → 20s → ... → max 120s）。
 */
class PushConnectionManager {

    companion object {
        const val TAG = "PushConnection"
        const val TOPIC = "thought-vault/reminder"
        const val MQTT_URL = "wss://<your-nas-domain>:<your-mqtt-wss-port>/mqtt"

        /** 最大重连尝试次数，超过后停止重连避免资源耗尽 */
        const val MAX_RECONNECT_ATTEMPTS = 10

        val instance: PushConnectionManager by lazy { PushConnectionManager() }
    }

    private var client: MqttClient? = null
    private var reconnectJob: Job? = null
    private var scope: CoroutineScope? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    @Volatile
    private var isReconnecting: Boolean = false

    /** 重连失败累计次数，连接成功后清零 */
    private var consecutiveFailures: Int = 0

    var onReminderReceived: ((String, Int, Int) -> Unit)? = null

    /**
     * 建立连接。在独立协程中执行以保活。
     */
    fun connect(coroutineScope: CoroutineScope) {
        scope = coroutineScope
        doConnect()
    }

    /**
     * 执行 MQTT 连接——通过 Dispatchers.IO 协程调度，避免裸线程泄漏。
     * 添加硬超时保护，防止 Paho 客户端无限阻塞。
     */
    private fun doConnect() {
        val s = scope ?: return
        reconnectJob?.cancel()

        s.launch {
            try {
                // 硬超时：20 秒内必须完成连接，否则取消
                withTimeout(20_000L) {
                    val clientId = "android-${java.util.UUID.randomUUID().toString().take(8)}"
                    val c = MqttClient(MQTT_URL, clientId, MemoryPersistence())

                    val options = MqttConnectOptions().apply {
                        isCleanSession = true
                        connectionTimeout = 10 // 秒
                        keepAliveInterval = 240 // 4 分钟
                        isAutomaticReconnect = false
                    }

                    // 在 IO 调度器上执行 Paho 的阻塞 connect
                    c.connect(options)
                    c.subscribe(TOPIC, 1)

                    synchronized(this@PushConnectionManager) {
                        // 断开旧连接，替换为新连接
                        client?.disconnect()
                        client = c
                    }
                    isConnected = true
                    consecutiveFailures = 0  // 连接成功，重置失败计数
                    Log.d(TAG, "MQTT 已连接")

                    c.setCallback(object : MqttCallback {
                        override fun connectionLost(cause: Throwable?) {
                            Log.w(TAG, "MQTT 连接断开: ${cause?.message}")
                            isConnected = false
                            s.launch { scheduleReconnect() }
                        }

                        override fun messageArrived(topic: String?, message: MqttMessage?) {
                            message?.let { handleMessage(it) }
                        }

                        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                    })
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "MQTT 连接超时（20秒硬限制）")
                isConnected = false
                scheduleReconnect()
            } catch (e: Exception) {
                Log.e(TAG, "MQTT 连接失败: ${e.message}")
                isConnected = false
                scheduleReconnect()
            }
        }
    }

    private fun handleMessage(message: MqttMessage) {
        try {
            val json = JSONObject(String(message.payload))
            val content = json.getString("content")
            val hour = json.optInt("hour", 0)
            val minute = json.optInt("minute", 0)
            val msgId = json.optString("id", "")
            Log.d(TAG, "收到提醒: $content ($hour:$minute) id=$msgId")
            onReminderReceived?.invoke(content, hour, minute)
        } catch (e: Exception) {
            Log.e(TAG, "消息解析失败: ${e.message}")
        }
    }

    /** 指数退避重连：从 5 秒开始，翻倍直到 120 秒上限 */
    private suspend fun scheduleReconnect() {
        if (isReconnecting) {
            Log.d(TAG, "已有重连任务进行中，跳过")
            return
        }
        isReconnecting = true

        try {
            var delayMs = 5_000L
            val s = scope ?: run { isReconnecting = false; return }

            while (s.isActive) {
                consecutiveFailures++
                if (consecutiveFailures > MAX_RECONNECT_ATTEMPTS) {
                    Log.e(TAG, "MQTT 重连失败 $MAX_RECONNECT_ATTEMPTS 次，停止重连（节省电量）")
                    return
                }

                Log.d(TAG, "MQTT 将在 ${delayMs / 1000}s 后重连...（第 $consecutiveFailures 次）")
                delay(delayMs)
                if (!s.isActive) break

                try {
                    synchronized(this@PushConnectionManager) {
                        client?.disconnect()
                        client = null
                    }
                    doConnect()
                    return // doConnect 会通过回调或内部 catch 处理后续状态
                } catch (e: Exception) {
                    Log.w(TAG, "MQTT 重连失败: ${e.message}")
                    delayMs = (delayMs * 2).coerceAtMost(120_000L)
                }
            }
        } finally {
            isReconnecting = false
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        isReconnecting = false
        consecutiveFailures = 0
        try {
            synchronized(this) {
                client?.disconnect()
                client = null
            }
        } catch (_: Exception) {}
        isConnected = false
    }
}
