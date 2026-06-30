import { h } from 'preact'
import { useState } from 'preact/hooks'
import { TopNav } from '../components/TopNav'
import { ConnectionBadge } from '../components/ConnectionBadge'
import { config, connectionStatus, updateConfig, clearConfig } from '../signals/app-state'

export function SettingsPage() {
  const [username, setUsername] = useState(config.value?.username || '')
  const [password, setPassword] = useState(config.value?.password || '')
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<'success' | 'error' | null>(null)
  const [msg, setMsg] = useState('')

  const hasConfig = config.value !== null

  const handleSave = async () => {
    if (!username || !password) {
      setMsg('请填写用户名和密码')
      return
    }
    setSaving(true)
    setMsg('')
    const ok = await updateConfig(username, password)
    setSaving(false)
    if (ok) {
      setMsg('配置已保存，连接成功！')
      setTestResult('success')
    } else {
      setMsg('配置已保存，但连接测试失败')
      setTestResult('error')
    }
  }

  const handleTest = async () => {
    if (!username || !password) {
      setMsg('请填写用户名和密码')
      return
    }
    setTesting(true)
    setTestResult(null)
    const ok = await updateConfig(username, password)
    setTesting(false)
    setTestResult(ok ? 'success' : 'error')
    setMsg(ok ? '连接成功！' : '连接失败，请检查用户名密码和 NAS 地址')
  }

  const handleClear = () => {
    if (confirm('确定要清除所有配置吗？')) {
      clearConfig()
      setUsername('')
      setPassword('')
      setTestResult(null)
      setMsg('配置已清除')
    }
  }

  return (
    <div class="page">
      <TopNav title="设置" />
      <div class="page-content no-tab" style="padding: 16px;">
        {/* 连接状态 */}
        <div style="margin-bottom: 20px; display: flex; align-items: center; gap: 10px;">
          <span style="font-size: 15px; color: var(--color-text-secondary);">连接状态：</span>
          <ConnectionBadge />
        </div>

        {/* 配置表单 */}
        <div style="margin-bottom: 16px;">
          <label style="font-size: 13px; color: var(--color-text-secondary); display: block; margin-bottom: 6px;">
            用户名（DSM 账号，含前缀 home/）
          </label>
          <input
            class="input-field"
            type="text"
            value={username}
            onInput={(e: any) => setUsername(e.target.value)}
            placeholder="你的 DSM 账号"
            autoComplete="username"
          />
        </div>

        <div style="margin-bottom: 20px;">
          <label style="font-size: 13px; color: var(--color-text-secondary); display: block; margin-bottom: 6px;">
            密码
          </label>
          <input
            class="input-field"
            type="password"
            value={password}
            onInput={(e: any) => setPassword(e.target.value)}
            placeholder="DSM 密码"
            autoComplete="current-password"
          />
        </div>

        {/* 按钮 */}
        <div style="display: flex; gap: 12px; margin-bottom: 16px;">
          <button class="btn btn-secondary" onClick={handleTest} disabled={testing} style="flex: 1;">
            {testing ? '测试中...' : '测试连接'}
          </button>
          <button class="btn btn-primary" onClick={handleSave} disabled={saving} style="flex: 1;">
            {saving ? '保存中...' : '保存配置'}
          </button>
        </div>

        {/* 测试结果 */}
        {testResult && (
          <div class="card" style={`margin-bottom: 16px; background: ${testResult === 'success' ? 'var(--color-green)' : 'var(--color-red)'}; color: #fff; font-size: 14px;`}>
            {testResult === 'success' ? '✅ 连接测试成功' : '❌ 连接测试失败'}
          </div>
        )}

        {/* 消息 */}
        {msg && (
          <div style="font-size: 14px; color: var(--color-text-secondary); margin-bottom: 16px; text-align: center;">
            {msg}
          </div>
        )}

        {/* 清除配置 */}
        {hasConfig && (
          <button class="btn btn-danger btn-full" onClick={handleClear}>
            🗑 清除所有配置
          </button>
        )}
      </div>
    </div>
  )
}
