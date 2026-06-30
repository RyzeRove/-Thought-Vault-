import { signal } from '@preact/signals'
import { type NasConfig, type ConnectionStatus, type Entry, type TodoItem } from '../lib/types'
import { settingsStore } from '../lib/settings-store'
import { WebdavClient } from '../lib/webdav-client'
import { WebdavApi } from '../lib/webdav-api'
import { today } from '../lib/date-utils'

// ── 全局响应式状态 ──

export const config = signal<NasConfig | null>(null)
export const connectionStatus = signal<ConnectionStatus>('unknown')
export const todayEntries = signal<Entry[]>([])
export const todoItems = signal<TodoItem[]>([])
export const selectedDate = signal<string>(today())
export const isInitialized = signal(false)

let apiInstance: WebdavApi | null = null

export function getApi(): WebdavApi | null {
  return apiInstance
}

export async function initialize(): Promise<boolean> {
  if (isInitialized.value) return true

  const hasConfig = await settingsStore.init()
  if (hasConfig) {
    const cfg = settingsStore.getConfig()
    if (cfg) {
      config.value = cfg
      const client = new WebdavClient(cfg.baseUrl, cfg.username, cfg.password)
      apiInstance = new WebdavApi(client, cfg.username)

      // 测试连接
      const result = await apiInstance.testConnection()
      if (result.success && result.data) {
        connectionStatus.value = 'connected'
      } else {
        connectionStatus.value = 'error'
      }
    }
  } else {
    connectionStatus.value = 'not_configured'
  }

  isInitialized.value = true
  return true
}

export async function updateConfig(username: string, password: string): Promise<boolean> {
  await settingsStore.saveConfig(username, password)
  const cfg = settingsStore.getConfig()
  if (!cfg) return false

  config.value = cfg
  const client = new WebdavClient(cfg.baseUrl, cfg.username, cfg.password)
  apiInstance = new WebdavApi(client, cfg.username)

  const result = await apiInstance.testConnection()
  if (result.success && result.data) {
    connectionStatus.value = 'connected'
    return true
  } else {
    connectionStatus.value = 'error'
    return false
  }
}

export function clearConfig(): void {
  settingsStore.clearConfig()
  config.value = null
  apiInstance = null
  connectionStatus.value = 'not_configured'
  todayEntries.value = []
  todoItems.value = []
}
