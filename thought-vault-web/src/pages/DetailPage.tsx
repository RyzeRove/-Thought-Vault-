import { h } from 'preact'
import { useState, useEffect } from 'preact/hooks'
import { TopNav } from '../components/TopNav'
import { EntryCard } from '../components/EntryCard'
import { MarkdownViewer } from '../components/MarkdownViewer'
import { EmptyState } from '../components/EmptyState'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { getApi, config } from '../signals/app-state'
import { formatDateFull } from '../lib/date-utils'
import { parseRawEntries } from '../lib/markdown-parser'
import type { Entry } from '../lib/types'

interface Props {
  date: string
  onBack: () => void
}

export function DetailPage({ date, onBack }: Props) {
  const [entries, setEntries] = useState<Entry[]>([])
  const [rawMd, setRawMd] = useState<string | null>(null)
  const [dailyReport, setDailyReport] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState<'raw' | 'daily'>('raw')
  const [isEditing, setIsEditing] = useState(false)
  const [editContent, setEditContent] = useState('')
  const [saving, setSaving] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')
  const [hasDailyReport, setHasDailyReport] = useState(false)

  const load = async () => {
    const api = getApi()
    if (!api) return
    setLoading(true)

    const [rawResult, reportResult] = await Promise.all([
      api.getRawEntries(date),
      api.getDailyReport(date),
    ])

    if (rawResult.success && rawResult.data) {
      setRawMd(rawResult.data)
      setEntries(parseRawEntries(rawResult.data))
    } else {
      setRawMd(null)
      setEntries([])
    }

    if (reportResult.success && reportResult.data) {
      setDailyReport(reportResult.data)
      setHasDailyReport(true)
      setTab('daily') // 默认显示 AI 日报
    } else {
      setDailyReport(null)
      setHasDailyReport(false)
      setTab('raw')
    }

    setLoading(false)
  }

  useEffect(() => { load() }, [date])

  const startEdit = () => {
    setEditContent(rawMd || `# ${date} 原始记录\n\n---\n`)
    setIsEditing(true)
  }

  const cancelEdit = () => {
    setIsEditing(false)
    setEditContent('')
  }

  const saveEdit = async () => {
    const api = getApi()
    if (!api) return
    setSaving(true)
    setErrorMsg('')
    const result = await api.putRawFile(date, editContent)
    if (result.success) {
      setIsEditing(false)
      await load()
    } else {
      setErrorMsg(result.error || '保存失败')
    }
    setSaving(false)
  }

  const startEditReport = () => {
    setEditContent(dailyReport || '')
    setIsEditing(true)
  }

  const saveReport = async () => {
    const api = getApi()
    if (!api) return
    setSaving(true)
    setErrorMsg('')
    const result = await api.putDailyReport(date, editContent)
    if (result.success) {
      setIsEditing(false)
      setDailyReport(editContent)
      setHasDailyReport(true)
    } else {
      setErrorMsg(result.error || '保存失败')
    }
    setSaving(false)
  }

  return (
    <div style="display: flex; flex-direction: column; height: 100%; overflow: hidden;">
      <TopNav
        title={formatDateFull(date)}
        onBack={onBack}
        rightAction={!isEditing ? { label: loading ? '' : '编辑', onClick: tab === 'raw' ? startEdit : startEditReport } : undefined}
      />

      {isEditing ? (
        <div style="flex: 1; overflow-y: auto; padding: 16px; padding-bottom: 80px;">
          <textarea
            class="input-field"
            value={editContent}
            onInput={(e: any) => setEditContent(e.target.value)}
            rows={20}
            style="resize: vertical; min-height: 300px; font-family: var(--font-mono); font-size: 14px;"
          />
          <div style="display: flex; gap: 12px; margin-top: 12px;">
            <button class="btn btn-secondary" onClick={cancelEdit} style="flex: 1;">取消</button>
            <button class="btn btn-primary" onClick={saveEdit} disabled={saving} style="flex: 1;">
              {saving ? '保存中...' : '保存'}
            </button>
          </div>
          {errorMsg && <div style="color: var(--color-red); font-size: 14px; margin-top: 8px; text-align: center;">{errorMsg}</div>}
        </div>
      ) : (
        <>
          {/* Tab 切换 */}
          <div style="display: flex; flex-shrink: 0; border-bottom: 1px solid var(--color-separator);">
            <button
              onClick={() => setTab('raw')}
              style={`flex:1; padding: 12px; text-align: center; font-size: 15px; font-weight: 500;
                       color: ${tab === 'raw' ? 'var(--color-primary)' : 'var(--color-text-secondary)'};
                       border-bottom: 2px solid ${tab === 'raw' ? 'var(--color-primary)' : 'transparent'};
                       transition: all 0.2s;`}
            >
              原始记录 ({entries.length})
            </button>
            <button
              onClick={() => setTab('daily')}
              style={`flex:1; padding: 12px; text-align: center; font-size: 15px; font-weight: 500;
                       color: ${tab === 'daily' ? 'var(--color-primary)' : 'var(--color-text-secondary)'};
                       border-bottom: 2px solid ${tab === 'daily' ? 'var(--color-primary)' : 'transparent'};
                       transition: all 0.2s;`}
            >
              AI 日报
            </button>
          </div>

          {/* 可滚动内容区 */}
          <div style="flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; padding: 16px; padding-bottom: 80px;">
            {loading ? <LoadingSpinner /> : (
              <>
                {tab === 'raw' && (
                  entries.length === 0 ? <EmptyState icon="📝" text="当天暂无记录" /> : (
                    entries.map((entry, i) => (
                      <EntryCard key={i} time={entry.time} content={entry.content}
                                 refinedContent={entry.refinedContent} category={entry.category} />
                    ))
                  )
                )}
                {tab === 'daily' && (
                  dailyReport ? <MarkdownViewer content={dailyReport} /> : <EmptyState icon="📄" text="暂无 AI 日报" />
                )}
              </>
            )}
          </div>
        </>
      )}
    </div>
  )
}
