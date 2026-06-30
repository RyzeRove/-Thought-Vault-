import { WebdavClient } from './webdav-client'
import { type Result, ok, err } from './types'

/**
 * WebDAV 高级 API — 对标 Android WebdavApi.kt
 * 路径前缀包含用户名，支持多用户数据隔离
 */
export class WebdavApi {
  private client: WebdavClient
  private userPrefix: string

  constructor(client: WebdavClient, username: string) {
    this.client = client
    this.userPrefix = `homes/${username}/thoughts/`
  }


  // ── 路径构造（带用户前缀） ──

  rawFilePath(date: string): string {
    const [y, m] = date.split('-')
    return `${this.userPrefix}raw/${y}/${m}/${date}.md`
  }

  rawDirPath(date: string): string {
    const [y, m] = date.split('-')
    return `${this.userPrefix}raw/${y}/${m}/`
  }

  dailyFilePath(date: string): string {
    const [y, m] = date.split('-')
    return `${this.userPrefix}daily/${y}/${m}/${date}.md`
  }

  dailyDirPath(date: string): string {
    const [y, m] = date.split('-')
    return `${this.userPrefix}daily/${y}/${m}/`
  }

  lockFilePath(): string {
    return `${this.userPrefix}.sync/write.lock`
  }

  todosFilePath(): string {
    return `${this.userPrefix}todos/tasks.md`
  }

  /** WebDAV 根路径（用于 testConnection） */
  rootPath(): string {
    return this.userPrefix
  }

  // ── 汇总文件路径 ──

  weeklyFilePath(week: string): string {
    const y = week.slice(0, 4)
    return `${this.userPrefix}weekly/${y}/${week}.md`
  }

  monthlyFilePath(month: string): string {
    const y = month.slice(0, 4)
    return `${this.userPrefix}monthly/${y}/${month}.md`
  }

  quarterlyFilePath(q: string): string {
    const y = q.slice(0, 4)
    return `${this.userPrefix}quarterly/${y}/${q}.md`
  }

  yearlyFilePath(year: string): string {
    return `${this.userPrefix}yearly/${year}/${year}.md`
  }

  /** PROPFIND 列出所有周报 */
  async listWeeklyFiles(): Promise<Result<string>> {
    return this.client.propfind(`${this.userPrefix}weekly/`, 'infinity')
  }

  /** PROPFIND 列出所有月报 */
  async listMonthlyFiles(): Promise<Result<string>> {
    return this.client.propfind(`${this.userPrefix}monthly/`, 'infinity')
  }

  /** PROPFIND 列出所有季报 */
  async listQuarterlyFiles(): Promise<Result<string>> {
    return this.client.propfind(`${this.userPrefix}quarterly/`, 'infinity')
  }

  /** PROPFIND 列出所有年报 */
  async listYearlyFiles(): Promise<Result<string>> {
    return this.client.propfind(`${this.userPrefix}yearly/`, 'infinity')
  }

  /** GET 汇总文件内容 */
  async getSummaryContent(path: string): Promise<Result<string | null>> {
    return this.client.get(path)
  }

  // ── 读操作 ──

  async getRawEntries(date: string): Promise<Result<string | null>> {
    return this.client.get(this.rawFilePath(date))
  }

  async getDailyReport(date: string): Promise<Result<string | null>> {
    return this.client.get(this.dailyFilePath(date))
  }

  async getTodosFile(): Promise<Result<string | null>> {
    return this.client.get(this.todosFilePath())
  }

  /** PROPFIND 列出某月所有有记录的日期 */
  async listMonthDays(year: number, month: number): Promise<Result<string>> {
    const path = `${this.userPrefix}raw/${year}/${String(month).padStart(2, '0')}/`
    return this.client.propfind(path, 1)
  }

  // ── 写操作 ──

  /**
   * 保存一条新条目（完整的读-改-写 + 锁流程）
   */
  async saveEntry(date: string, time: string, content: string): Promise<Result<void>> {
    const lockPath = this.lockFilePath()
    const filePath = this.rawFilePath(date)
    const dirPath = this.rawDirPath(date)

    // 1. 获取写入锁（最多 5 次重试）
    let lockAcquired = false
    for (let attempt = 1; attempt <= 5; attempt++) {
      const lockResult = await this.client.putEmpty(lockPath)
      if (lockResult.success) {
        lockAcquired = true
        break
      }
      if (attempt === 3) {
        console.warn('锁获取失败 3 次，尝试强制清理僵尸锁...')
        await this.client.delete(lockPath)
      }
      if (attempt < 5) {
        const delayMs = 250 * (1 << (attempt - 1))
        await new Promise(r => setTimeout(r, delayMs))
      }
    }
    if (!lockAcquired) {
      return err('无法获取写入锁，请稍后重试')
    }

    try {
      // 2. 确保目录存在
      await this.client.mkcol(dirPath)

      // 3. 读取已有内容
      const getResult = await this.client.get(filePath)
      if (!getResult.success) return err(getResult.error || '读取文件失败')

      const existing = getResult.data || ''

      // 4. 构建新内容（追加新条目）
      const entryBlock = `## ${time}\n\n${content}`
      let newContent: string
      if (existing) {
        newContent = existing.trimEnd() + '\n\n' + entryBlock
      } else {
        newContent = `# ${date} 原始记录\n\n---\n${entryBlock}`
      }

      // 5. 写回
      const putResult = await this.client.put(filePath, newContent)
      if (!putResult.success) return putResult

      return ok(undefined)
    } finally {
      // 6. 释放锁
      await this.client.delete(lockPath)
    }
  }

  /** 全量覆盖原始记录文件 */
  async putRawFile(date: string, content: string): Promise<Result<void>> {
    const dirPath = this.rawDirPath(date)
    await this.client.mkcol(dirPath)
    return this.client.put(this.rawFilePath(date), content)
  }

  /** 全量覆盖日报文件 */
  async putDailyReport(date: string, content: string): Promise<Result<void>> {
    const dirPath = this.dailyDirPath(date)
    await this.client.mkcol(dirPath)
    return this.client.put(this.dailyFilePath(date), content)
  }

  /** 保存待办文件 */
  async putTodosFile(content: string): Promise<Result<void>> {
    await this.client.mkcol(`${this.userPrefix}todos`)
    return this.client.put(this.todosFilePath(), content)
  }

  // ── 连接测试 ──

  async testConnection(): Promise<Result<boolean>> {
    const r = await this.client.propfind(this.rootPath(), 0)
    if (r.success) return { success: true, data: true }
    console.error('testConnection error:', r.error)
    return { success: false, error: r.error }
  }
}
