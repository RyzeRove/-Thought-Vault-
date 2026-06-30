/** 核心类型定义 */

export interface Entry {
  time: string       // HH:mm
  content: string
  category?: string
  refinedContent?: string
  title?: string
}

export interface TodoItem {
  content: string
  date: string
  isLongTerm: boolean
  isDone: boolean
}

export interface NasConfig {
  baseUrl: string    // WebDAV 完整地址: http://127.0.0.1:<port>/homes/<user>/thoughts
  username: string
  password: string
}

export interface DailyReport {
  date: string
  raw?: string       // full markdown content
}

export type ConnectionStatus = 'unknown' | 'connected' | 'error' | 'not_configured'

export type SaveState = 'idle' | 'saving' | 'success' | 'error'

export interface Result<T> {
  success: boolean
  data?: T
  error?: string
}

export function ok<T>(data: T): Result<T> {
  return { success: true, data }
}

export function err<T>(error: string): Result<T> {
  return { success: false, error }
}
