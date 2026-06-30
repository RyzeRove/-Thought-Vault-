import { h } from 'preact'
import { useState, useEffect } from 'preact/hooks'
import { TopNav } from '../components/TopNav'
import { MarkdownViewer } from '../components/MarkdownViewer'
import { EmptyState } from '../components/EmptyState'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { getApi, config } from '../signals/app-state'

type Period = 'weekly' | 'monthly' | 'quarterly' | 'yearly'

interface SummaryItem {
  id: string
  label: string
}

const PERIOD_LABELS: { key: Period; label: string }[] = [
  { key: 'weekly', label: '周报' },
  { key: 'monthly', label: '月报' },
  { key: 'quarterly', label: '季报' },
  { key: 'yearly', label: '年报' },
]

/** 解析 PROPFIND XML 中 .md 文件名（从 D:href 路径中提取） */
function parseFileList(xml: string): SummaryItem[] {
  const items: SummaryItem[] = []
  // 匹配 href 中的 .md 文件路径，例如 <D:href>/.../2026-W22.md</D:href>
  const regex = /<D:href>([^<]+\.md)<\/D:href>/gi
  let m
  while ((m = regex.exec(xml)) !== null) {
    const fullPath = m[1]
    const name = fullPath.split('/').pop()?.replace('.md', '') || fullPath.replace('.md', '')
    items.push({ id: name, label: name })
  }
  items.sort((a, b) => b.id.localeCompare(a.id))
  return items
}

export function SummaryPage() {
  const [period, setPeriod] = useState<Period>('weekly')
  const [files, setFiles] = useState<SummaryItem[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [content, setContent] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadingContent, setLoadingContent] = useState(false)

  const api = getApi()

  const loadFileList = async (p: Period) => {
    if (!api) return
    setLoading(true)
    setFiles([])
    setSelected(null)
    setContent(null)

    let result
    switch (p) {
      case 'weekly': result = await api.listWeeklyFiles(); break
      case 'monthly': result = await api.listMonthlyFiles(); break
      case 'quarterly': result = await api.listQuarterlyFiles(); break
      case 'yearly': result = await api.listYearlyFiles(); break
    }

    if (result?.success && result.data) {
      const items = parseFileList(result.data)
      setFiles(items)
      if (items.length > 0) {
        selectFile(items[0].id, p)
      }
    }
    setLoading(false)
  }

  const selectFile = async (id: string, p: Period) => {
    if (!api) return
    setSelected(id)
    setLoadingContent(true)

    let path = ''
    const y = id.slice(0, 4)
    switch (p) {
      case 'weekly': path = api.weeklyFilePath(id); break
      case 'monthly': path = api.monthlyFilePath(id); break
      case 'quarterly': path = api.quarterlyFilePath(id); break
      case 'yearly': path = api.yearlyFilePath(id); break
    }

    const result = await api.getSummaryContent(path)
    if (result.success && result.data) {
      setContent(result.data)
    } else {
      setContent(null)
    }
    setLoadingContent(false)
  }

  useEffect(() => {
    if (api) loadFileList(period)
  }, [period])

  return (
    <div style="display: flex; flex-direction: column; height: 100%; overflow: hidden;">
      <TopNav title="汇总分析" />

      {/* 周期选择器 */}
      <div style="display: flex; flex-shrink: 0; border-bottom: 1px solid var(--color-separator);">
        {PERIOD_LABELS.map(({ key, label }) => (
          <button
            key={key}
            onClick={() => { setPeriod(key); loadFileList(key) }}
            style={`flex:1; padding: 12px; text-align: center; font-size: 15px; font-weight: 500;
                     color: ${period === key ? 'var(--color-primary)' : 'var(--color-text-secondary)'};
                     border-bottom: 2px solid ${period === key ? 'var(--color-primary)' : 'transparent'};
                     transition: all 0.2s;`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* 文件列表 + 内容 */}
      <div style="flex: 1; display: flex; overflow: hidden;">
        {/* 左侧文件列表 */}
        <div style="width: 120px; flex-shrink: 0; border-right: 1px solid var(--color-separator);
                    overflow-y: auto; -webkit-overflow-scrolling: touch; padding-bottom: 80px;">
          {loading ? <LoadingSpinner /> : (
            files.length === 0 ? (
              <div style="padding: 24px 12px; text-align: center; font-size: 13px; color: var(--color-text-tertiary);">
                暂无数据
              </div>
            ) : (
              files.map(f => (
                <button
                  key={f.id}
                  onClick={() => selectFile(f.id, period)}
                  style={`display: block; width: 100%; padding: 12px 10px; text-align: center;
                          font-size: 13px; border-bottom: 0.5px solid var(--color-separator);
                          background: ${selected === f.id ? 'var(--color-primary-light)' : 'transparent'};
                          color: ${selected === f.id ? 'var(--color-primary)' : 'var(--color-text-secondary)'};
                          font-weight: ${selected === f.id ? 600 : 400};`}
                >
                  {f.label}
                </button>
              ))
            )
          )}
        </div>

        {/* 右侧内容区 */}
        <div style="flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; padding: 16px; padding-bottom: 80px;">
          {loadingContent ? <LoadingSpinner /> : (
            content ? <MarkdownViewer content={content} /> : (
              selected ? <EmptyState icon="📄" text="暂无内容" /> : <EmptyState icon="📊" text="选择一份报告查看" />
            )
          )}
        </div>
      </div>
    </div>
  )
}
