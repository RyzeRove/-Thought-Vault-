/**
 * 提醒调度器 — 使用 cron 在每个提醒时间点通过 MQTT 推送提醒事件。
 *
 * cron v3 API: new CronJob(cronExpr, onTick, null, true, timezone)
 * 提醒时间从环境变量读取，默认 11:30 / 17:30 / 21:30（中国时区）。
 */

import { CronJob } from 'cron';
import { publishReminder } from './mqtt-publisher.js';

const DEFAULT_TIMES = ['11:30', '17:30', '21:30'];
const TZ = 'Asia/Shanghai';

const MESSAGES = [
  '💭 此刻有什么想法？记下来吧',
  '📝 今天的思考值得被记录',
  '✨ 随手记一笔，AI 帮你整理',
  '🧠 有什么灵感一闪而过？',
];

function parseTimes(): string[] {
  const env = process.env.REMINDER_TIMES;
  if (!env) return DEFAULT_TIMES;
  return env
    .split(',')
    .map((t) => t.trim())
    .filter((t) => /^\d{1,2}:\d{2}$/.test(t));
}

function messageId(hour: number, minute: number): string {
  const now = new Date();
  const date = now.toISOString().slice(0, 10);
  return `r-${date}-${String(hour).padStart(2, '0')}${String(minute).padStart(2, '0')}`;
}

function scheduleTime(hour: number, minute: number): void {
  // cron v3: 6 字段格式（含秒），月份 1-12
  const cronExpr = `0 ${minute} ${hour} * * *`;
  try {
    const job = new CronJob(
      cronExpr,
      () => {
        const id = messageId(hour, minute);
        const content = MESSAGES[Math.floor(Math.random() * MESSAGES.length)];
        publishReminder({ id, content, hour, minute });
      },
      null,  // onComplete
      true,  // start now
      TZ,    // timezone
    );
    console.log(`[scheduler] 已注册提醒: ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')} (cron: ${cronExpr})`);
  } catch (e) {
    console.error(`[scheduler] 注册失败 ${hour}:${minute}:`, e);
  }
}

export function startScheduler(): void {
  const times = parseTimes();
  for (const t of times) {
    const [h, m] = t.split(':').map(Number);
    scheduleTime(h, m);
  }
  console.log(`[scheduler] 调度器已启动，共 ${times.length} 个提醒时间点 (TZ=${TZ})`);
}
