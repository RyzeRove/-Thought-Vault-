import { h } from 'preact'
import { useState, useEffect } from 'preact/hooks'
import { TopNav } from '../components/TopNav'
import { EntryCard } from '../components/EntryCard'
import { EmptyState } from '../components/EmptyState'
import { ConnectionBadge } from '../components/ConnectionBadge'
import { getApi, config, connectionStatus, todayEntries } from '../signals/app-state'
import { today, nowTime } from '../lib/date-utils'
import { parseRawEntries } from '../lib/markdown-parser'
import type { Entry, SaveState } from '../lib/types'

export function HomePage() {
  const [inputText, setInputText] = useState('')
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [errorMsg, setErrorMsg] = useState('')

  const entries = todayEntries.value
  const hasConfig = config.value !== null

  const loadToday = async () => {
    const api = getApi()
    if (!api) return
    const result = await api.getRawEntries(today())
    if (result.success && result.data) {
      todayEntries.value = parseRawEntries(result.data)
    }
  }

  useEffect(() => {
    if (hasConfig && connectionStatus.value === 'connected') {
      loadToday()
    }
  }, [])

  const handleSave = async () => {
    const text = inputText.trim()
    if (!text) return

    const api = getApi()
    if (!api) {
      setSaveState('error')
      setErrorMsg('请先配置 NAS 连接')
      return
    }

    setSaveState('saving')
    setErrorMsg('')

    const result = await api.saveEntry(today(), nowTime(), text)
    if (result.success) {
      setSaveState('success')
      setInputText('')
      await loadToday()
      setTimeout(() => setSaveState('idle'), 2000)
    } else {
      setSaveState('error')
      setErrorMsg(result.error || '保存失败')
    }
  }

  const handleRetry = () => {
    setSaveState('idle')
    setErrorMsg('')
  }

  const saveButtonLabel = () => {
    switch (saveState) {
      case 'saving': return '保存中...'
      case 'success': return '已保存 ✓'
      case 'error': return '重试'
      default: return '保存'
    }
  }

  return (
    <div style="display: flex; flex-direction: column; height: 100%; overflow: hidden;">
      <TopNav title="思维札记" />

      {/* 连接状态 */}
      <div style="padding: 8px 16px; display: flex; align-items: center; gap: 8px; flex-shrink: 0;">
        <ConnectionBadge />
        {!hasConfig && (
          <span style="font-size: 13px; color: var(--color-text-tertiary);">
            请先 <a href="#/settings">设置 NAS 连接</a>
          </span>
        )}
      </div>

      {/* 可滚动内容区 */}
      <div style="flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; padding: 0 16px; padding-bottom: 80px;">
        {/* 输入区 */}
        <div style="margin-bottom: 16px;">
          <textarea
            class="input-field"
            value={inputText}
            onInput={(e: any) => setInputText(e.target.value)}
            placeholder="此刻在想什么？写下来吧..."
            rows={4}
            style="resize: none; min-height: 100px;"
            disabled={saveState === 'saving'}
          />
          <div style="display: flex; justify-content: flex-end; margin-top: 8px;">
            <button
              class={`btn btn-primary btn-sm ${saveState === 'success' ? '' : ''}`}
              onClick={saveState === 'error' ? handleRetry : handleSave}
              disabled={saveState === 'saving' || !inputText.trim()}
              style={`
                ${saveState === 'success' ? 'background: var(--color-green);' : ''}
                ${saveState === 'error' ? 'background: var(--color-red);' : ''}
              `}
            >
              {saveButtonLabel()}
            </button>
          </div>
        </div>

        {/* 今日记录 */}
        <div style="margin-bottom: 8px; font-size: 13px; color: var(--color-text-secondary);">
          今天已记录 {entries.length} 条
        </div>

        {entries.length === 0 ? (
          <EmptyState icon="✏️" text="今天还没有记录" />
        ) : (
          entries.slice().reverse().map((entry, i) => (
            <EntryCard
              key={i}
              time={entry.time}
              content={entry.content}
              refinedContent={entry.refinedContent}
              category={entry.category}
            />
          ))
        )}
      </div>

      {/* 错误提示 */}
      {saveState === 'error' && errorMsg && (
        <div class="snackbar error">
          {errorMsg}
          <button onClick={() => { setSaveState('idle'); setErrorMsg('') }}
                  style="margin-left: 12px; color: #fff; font-weight: 600;">关闭</button>
        </div>
      )}
    </div>
  )
}
