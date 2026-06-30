/**
 * 提醒调度服务入口。
 *
 * 独立运行在 thought-vault-web 容器内（通过 pm2 或直接 node），
 * 不占用 nginx 端口，只负责 cron + MQTT publish。
 */

import { startScheduler } from './scheduler.js';

console.log('[reminder-service] 提醒调度服务启动中...');
startScheduler();
console.log('[reminder-service] 运行中，等待 cron 触发...');
