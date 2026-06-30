/** 日期工具函数 — 对标 Android 版 date-utils */

export function today(): string {
  return formatDate(new Date())
}

export function formatDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function formatTime(timeStr: string): string {
  return timeStr // 已经是 HH:mm
}

export function nowTime(): string {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const WEEKDAYS_CN = ['日', '一', '二', '三', '四', '五', '六']

export function weekdayCn(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00')
  return `星期${WEEKDAYS_CN[d.getDay()]}`
}

export function formatMonth(year: number, month: number): string {
  return `${year}年 ${month}月`
}

export function formatDateFull(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00')
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${weekdayCn(dateStr)}`
}

export function getYearMonth(dateStr: string): { year: number; month: number } {
  const d = new Date(dateStr + 'T00:00:00')
  return { year: d.getFullYear(), month: d.getMonth() + 1 }
}

export function getDaysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
}

export function getFirstDayOfWeek(year: number, month: number): number {
  return new Date(year, month - 1, 1).getDay()
}

export function parseDate(dateStr: string): Date {
  return new Date(dateStr + 'T00:00:00')
}
