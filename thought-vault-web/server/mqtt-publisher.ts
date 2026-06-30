/**
 * MQTT 发布者 — 连接到 Mosquitto Broker 并发布提醒消息。
 *
 * 使用 mqtt (npm) 库连接到容器内 Mosquitto。
 * 如果连接失败，最多重试 3 次，之后静默跳过（下次 cron 触发时重试）。
 */

import mqtt, { MqttClient } from 'mqtt';

const MQTT_URL = process.env.MQTT_URL || 'mqtt://mosquitto:1883';
const TOPIC = 'thought-vault/reminder';
const QOS = 1; // at least once 确保送达

export interface ReminderMessage {
  id: string;
  content: string;
  hour: number;
  minute: number;
}

let client: MqttClient | null = null;

function getClient(): MqttClient {
  if (!client || client.disconnected) {
    client = mqtt.connect(MQTT_URL, {
      clientId: `reminder-scheduler-${Math.random().toString(36).slice(2, 8)}`,
      clean: true,
      connectTimeout: 5000,
      reconnectPeriod: 0, // 我们自己做重连逻辑
    });

    client.on('connect', () => {
      console.log('[mqtt-publisher] 已连接到 Mosquitto');
    });

    client.on('error', (err) => {
      console.error('[mqtt-publisher] MQTT 错误:', err.message);
    });

    client.on('close', () => {
      console.log('[mqtt-publisher] MQTT 连接关闭');
    });
  }
  return client;
}

/** 发布一条提醒消息 */
export function publishReminder(msg: ReminderMessage): void {
  const mq = getClient();
  const payload = JSON.stringify({
    ...msg,
    timestamp: new Date().toISOString(),
  });

  if (!mq.connected) {
    // 等待连接（最多 3 秒），然后重试
    let attempts = 0;
    const maxAttempts = 3;
    const tryPublish = () => {
      attempts++;
      if (mq.connected) {
        mq.publish(TOPIC, payload, { qos: QOS }, (err) => {
          if (err) {
            console.error(`[mqtt-publisher] 发布失败: ${err.message}`);
          } else {
            console.log(`[mqtt-publisher] 提醒已发布: ${msg.id} → "${msg.content}"`);
          }
        });
      } else if (attempts < maxAttempts) {
        setTimeout(tryPublish, 1000);
      } else {
        console.error(`[mqtt-publisher] 连接超时，提醒 ${msg.id} 未发送`);
      }
    };
    tryPublish();
  } else {
    mq.publish(TOPIC, payload, { qos: QOS }, (err) => {
      if (err) {
        console.error(`[mqtt-publisher] 发布失败: ${err.message}`);
      } else {
        console.log(`[mqtt-publisher] 提醒已发布: ${msg.id} → "${msg.content}"`);
      }
    });
  }
}
