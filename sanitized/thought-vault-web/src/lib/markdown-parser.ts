import { type Entry, type TodoItem } from './types'

/** 解析原始 Markdown 为 Entry 列表 — 格式: ## HH:MM\n\ncontent */
export function parseRawEntries(md: string): Entry[] {
  const entries: Entry[] = []
  const regex = /^## (\d{2}:\d{2})\s*$/gm
  const lines = md.split('\n')
  const headingIndices: { index: number; time: string }[] = []

  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/^## (\d{2}:\d{2})\s*$/)
    if (m) {
      headingIndices.push({ index: i, time: m[1] })
    }
  }

  for (let i = 0; i < headingIndices.length; i++) {
    const { index, time } = headingIndices[i]
    const nextIndex = i + 1 < headingIndices.length ? headingIndices[i + 1].index : lines.length
    const content = lines.slice(index + 1, nextIndex).join('\n').trim()
    if (content) {
      entries.push({ time, content })
    }
  }

  return entries
}

/** 序列化 Entry 列表为原始 Markdown */
export function serializeEntries(dateStr: string, entries: Entry[]): string {
  const lines: string[] = []
  lines.push(`# ${dateStr} 原始记录`)
  lines.push('')
  lines.push('---')
  lines.push('')
  for (const e of entries) {
    lines.push(`## ${e.time}`)
    lines.push('')
    lines.push(e.content)
    lines.push('')
  }
  return lines.join('\n').trimEnd()
}

/** 解析 tasks.md 为 TodoItem 列表 */
export function parseTodos(md: string): TodoItem[] {
  const result: TodoItem[] = []
  let section: 'day' | 'long' | 'done' | null = null

  for (const line of md.split('\n')) {
    if (line.startsWith('## 近期待办')) {
      section = 'day'
    } else if (line.startsWith('## 长期计划')) {
      section = 'long'
    } else if (line.startsWith('## 已完成')) {
      section = 'done'
    } else if (section !== null) {
      const m = line.match(/^- \[([ x])\] (.+?) \| (.+)$/)
      if (m) {
        const done = m[1] === 'x' || section === 'done'
        result.push({
          content: m[2],
          date: m[3],
          isLongTerm: section === 'long',
          isDone: done,
        })
      }
    }
  }

  return result
}

/** 序列化 TodoItem 列表为 tasks.md */
export function serializeTodos(items: TodoItem[]): string {
  const day = items.filter(t => !t.isLongTerm && !t.isDone)
  const long = items.filter(t => t.isLongTerm && !t.isDone)
  const done = items.filter(t => t.isDone)

  const lines: string[] = []
  lines.push('# 待办事项')
  lines.push('')
  lines.push('## 近期待办')
  day.forEach(t => lines.push(`- [ ] ${t.content} | ${t.date}`))
  lines.push('')
  lines.push('## 长期计划')
  long.forEach(t => lines.push(`- [ ] ${t.content} | ${t.date}`))
  lines.push('')
  lines.push('## 已完成')
  done.forEach(t => lines.push(`- [x] ${t.content} | ${t.date}`))

  return lines.join('\n')
}
