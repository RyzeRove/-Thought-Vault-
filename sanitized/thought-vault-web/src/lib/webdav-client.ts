import { type Result, ok, err } from './types'

/**
 * WebDAV 底层客户端 — 对标 Android WebdavClient.kt
 * 所有请求通过同域 /api/ 转发到 NAS Python 代理
 */
export class WebdavClient {
  private baseUrl: string
  private username: string
  private password: string
  private proxyPath: string

  constructor(baseUrl: string, username: string, password: string, proxyPath = '/api/') {
    this.baseUrl = baseUrl.replace(/\/+$/, '')
    this.username = username
    this.password = password
    this.proxyPath = proxyPath
  }

  /** 构建代理 URL — 路径直接透传，不再使用 ?path= 查询参数 */
  private proxyUrl(webdavPath: string): string {
    const path = webdavPath.replace(/^\//, '')
    return `${this.proxyPath}${path}`
  }

  /** HTTP Basic Auth 头 */
  private authHeaders(): Record<string, string> {
    const creds = btoa(`${this.username}:${this.password}`)
    return {
      'Authorization': `Basic ${creds}`,
      'Content-Type': 'text/markdown; charset=utf-8',
    }
  }

  /** 指数退避重试（对标 Android retryWithBackoff） */
  private async retryWithBackoff<T>(
    fn: () => Promise<Result<T>>,
    maxAttempts = 3,
    initialDelayMs = 500,
  ): Promise<Result<T>> {
    let lastError: Result<T> | null = null
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      const result = await fn()
      if (result.success) return result
      lastError = result

      // 仅对网络错误重试
      if (attempt < maxAttempts - 1) {
        const delay = initialDelayMs * (1 << attempt) // 500, 1000, 2000
        console.log(`重试 ${attempt + 1}/${maxAttempts}，等待 ${delay}ms...`)
        await new Promise(r => setTimeout(r, delay))
      }
    }
    return lastError!
  }

  /** PUT — 创建或覆盖文件 */
  async put(webdavPath: string, content: string): Promise<Result<void>> {
    return this.retryWithBackoff(async () => {
      try {
        const resp = await fetch(this.proxyUrl(webdavPath), {
          method: 'PUT',
          headers: this.authHeaders(),
          body: content,
        })
        if (resp.ok) return ok(undefined)
        return err(`PUT 失败: HTTP ${resp.status}`)
      } catch (e) {
        return err(e instanceof Error ? e.message : 'PUT 网络错误')
      }
    })
  }

  /** GET — 读取文件内容，404 返回 null */
  async get(webdavPath: string): Promise<Result<string | null>> {
    try {
      const resp = await fetch(this.proxyUrl(webdavPath), {
        headers: { 'Authorization': this.authHeaders()['Authorization'] },
      })
      if (resp.ok) {
        const text = await resp.text()
        return ok(text)
      }
      if (resp.status === 404) return ok(null)
      return err(`GET 失败: HTTP ${resp.status}`)
    } catch (e) {
      return err(e instanceof Error ? e.message : 'GET 网络错误')
    }
  }

  /** PUT 空内容（锁文件） */
  async putEmpty(webdavPath: string): Promise<Result<void>> {
    try {
      const resp = await fetch(this.proxyUrl(webdavPath), {
        method: 'PUT',
        headers: { 'Authorization': this.authHeaders()['Authorization'] },
      })
      if (resp.ok) return ok(undefined)
      return err(`PUT empty 失败: HTTP ${resp.status}`)
    } catch (e) {
      return err(e instanceof Error ? e.message : 'PUT empty 网络错误')
    }
  }

  /** DELETE */
  async delete(webdavPath: string): Promise<Result<void>> {
    try {
      const resp = await fetch(this.proxyUrl(webdavPath), {
        method: 'DELETE',
        headers: { 'Authorization': this.authHeaders()['Authorization'] },
      })
      if (resp.ok) return ok(undefined)
      return err(`DELETE 失败: HTTP ${resp.status}`)
    } catch (e) {
      return err(e instanceof Error ? e.message : 'DELETE 网络错误')
    }
  }

  /** MKCOL — 创建目录 */
  async mkcol(webdavPath: string): Promise<Result<void>> {
    try {
      const resp = await fetch(this.proxyUrl(webdavPath), {
        method: 'POST',
        headers: {
          ...this.authHeaders(),
          'X-Http-Method-Override': 'MKCOL',
        },
      })
      if (resp.ok || resp.status === 405) return ok(undefined)
      return err(`MKCOL 失败: HTTP ${resp.status}`)
    } catch (e) {
      return err(e instanceof Error ? e.message : 'MKCOL 网络错误')
    }
  }

  /** PROPFIND — 列出目录内容。depth: 0 | 1 | "infinity"（Synology Apache 不接受 2） */
  async propfind(webdavPath: string, depth: number | string = 1): Promise<Result<string>> {
    const body = `<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:displayname/>
    <D:getlastmodified/>
    <D:getcontentlength/>
  </D:prop>
</D:propfind>`.trim()

    // 通过 nginx /api/ 代理访问 WebDAV，复用容器的 LE 证书
    try {
      const resp = await fetch(this.proxyUrl(webdavPath), {
        method: 'PROPFIND',
        headers: {
          ...this.authHeaders(),
          'Content-Type': 'application/xml; charset=utf-8',
          'Depth': String(depth),
        },
        body,
      })
      if (resp.ok) {
        const text = await resp.text()
        return ok(text)
      }
      return err(`PROPFIND 失败: HTTP ${resp.status}`)
    } catch (e) {
      return err(e instanceof Error ? e.message : 'PROPFIND 网络错误')
    }
  }

  /** 测试连接 */
  async testConnection(): Promise<Result<boolean>> {
    const result = await this.propfind('', 0)
    if (result.success) return ok(true)
    return err(result.error || '连接测试失败')
  }
}
