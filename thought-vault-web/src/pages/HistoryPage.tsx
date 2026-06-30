import { h } from 'preact'
import { useState, useEffect } from 'preact/hooks'
import { TopNav } from '../components/TopNav'
import { DayCell } from '../components/DayCell'
import { EmptyState } from '../components/EmptyState'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { getApi, config } from '../signals/app-state'
import { today, getDaysInMonth, getFirstDayOfWeek } from '../lib/date-utils'

interface Props {
  onNavigate: (route: string) => void
}

export function HistoryPage({ onNavigate }: Props) {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [daysWithEntries, setDaysWithEntries] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(false)

  const loadMonth = async () => {
    const api = getApi()
    if (!api) return
    setLoading(true)
    const result = await api.listMonthDays(year, month)
    if (result.success && result.data) {
      const days = parsePropfindDates(result.data)
      setDaysWithEntries(days)
    }
    setLoading(false)
  }

  useEffect(() => {
    if (config.value) loadMonth()
  }, [year, month])

  const prevMonth = () => {
    if (month === 1) { setMonth(12); setYear(y => y - 1) }
    else setMonth(m => m - 1)
  }

  const nextMonth = () => {
    if (month === 12) { setMonth(1); setYear(y => y + 1) }
    else setMonth(m => m + 1)
  }

  const daysInMonth = getDaysInMonth(year, month)
  const firstDay = getFirstDayOfWeek(year, month)
  const todayStr = today()

  const cells: number[] = []
  for (let i = 0; i < firstDay; i++) cells.push(0)
  for (let d = 1; d <= daysInMonth; d++) cells.push(d)

  const onDayClick = (day: number) => {
    const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    onNavigate(`#/detail/${dateStr}`)
  }

  return (
    <div style="display: flex; flex-direction: column; height: 100%; overflow: hidden;">
      <TopNav title="历史记录" />

      {/* 月份选择器 */}
      <div style="display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; flex-shrink: 0;">
        <button class="btn btn-sm btn-secondary" onClick={prevMonth} style="font-size: 18px;">←</button>
        <span style="font-size: 18px; font-weight: 600; letter-spacing: -0.01em;">
          {year}年 {month}月
        </span>
        <button class="btn btn-sm btn-secondary" onClick={nextMonth} style="font-size: 18px;"
                disabled={year === now.getFullYear() && month === now.getMonth() + 1}>→</button>
      </div>

      {/* 星期标题 */}
      <div style="display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; padding: 0 12px;
                  font-size: 12px; color: var(--color-text-tertiary); text-align: center; margin-bottom: 8px; flex-shrink: 0;">
        {['日','一','二','三','四','五','六'].map(w => <div key={w}>{w}</div>)}
      </div>

      {/* 可滚动日历区 */}
      <div style="flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; padding: 0 12px; padding-bottom: 80px;">
        {loading ? <LoadingSpinner /> : (
          <div style="display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px;">
            {cells.map((d, i) => {
              if (d === 0) return <div key={`e-${i}`} style="aspect-ratio:1;" />
              const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`
              const hasEntry = daysWithEntries.has(dateStr)
              const isToday = dateStr === todayStr
              return (
                <DayCell
                  key={dateStr}
                  day={d}
                  hasEntries={hasEntry}
                  isToday={isToday}
                  isSelected={false}
                  onClick={() => onDayClick(d)}
                />
              )
            })}
          </div>
        )}

        {!loading && daysWithEntries.size === 0 && (
          <EmptyState icon="📅" text={`${month}月暂无记录`} />
        )}
      </div>
    </div>
  )
}

/** 解析 PROPFIND XML 提取 YYYY-MM-DD.md 文件名 */
function parsePropfindDates(xml: string): Set<string> {
  const days = new Set<string>()
  const regex = /<D:displayname>(\d{4}-\d{2}-\d{2})\.md<\/D:displayname>/g
  let m
  while ((m = regex.exec(xml)) !== null) {
    days.add(m[1])
  }
  return days
}
