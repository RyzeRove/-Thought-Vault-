import { type NasConfig } from './types'

const STORAGE_KEY = 'thoughtvault_config'

/** 简单的凭证存储（localStorage base64） */
export class SettingsStore {
  private config: NasConfig | null = null

  async init(): Promise<boolean> {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      try {
        const json = decodeURIComponent(escape(atob(stored)))
        this.config = JSON.parse(json)
        return true
      } catch {
        return false
      }
    }
    return false
  }

  hasConfig(): boolean {
    return this.config !== null
  }

  getConfig(): NasConfig | null {
    return this.config
  }

  async saveConfig(username: string, password: string): Promise<void> {
    const baseUrl = 'https://<your-nas-domain>'
    this.config = { baseUrl, username, password }
    const json = JSON.stringify(this.config)
    localStorage.setItem(STORAGE_KEY, btoa(unescape(encodeURIComponent(json))))
  }

  clearConfig(): void {
    this.config = null
    localStorage.removeItem(STORAGE_KEY)
  }
}

export const settingsStore = new SettingsStore()
