import { h } from 'preact'
import { useState, useEffect } from 'preact/hooks'
import { TopNav } from '../components/TopNav'
import { TodoCard } from '../components/TodoCard'
import { EmptyState } from '../components/EmptyState'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { getApi, config } from '../signals/app-state'
import { parseTodos, serializeTodos } from '../lib/markdown-parser'
import { today } from '../lib/date-utils'
import type { TodoItem } from '../lib/types'

export function TodoPage() {
  const [items, setItems] = useState<TodoItem[]>([])
  const [loading, setLoading] = useState(true)
  const [showAdd, setShowAdd] = useState(false)
  const [addText, setAddText] = useState('')
  const [addIsLong, setAddIsLong] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')

  const load = async () => {
    const api = getApi()
    if (!api) { setLoading(false); return }
    setLoading(true)
    const result = await api.getTodosFile()
    if (result.success) {
      if (result.data) {
        setItems(parseTodos(result.data))
      } else {
        setItems([])
      }
    } else {
      setErrorMsg(result.error || '加载失败')
    }
    setLoading(false)
  }

  useEffect(() => { if (config.value) load() }, [])

  const saveItems = async (newItems: TodoItem[]) => {
    const api = getApi()
    if (!api) return
    setItems(newItems)
    const md = serializeTodos(newItems)
    const result = await api.putTodosFile(md)
    if (!result.success) {
      setErrorMsg(result.error || '保存失败')
    }
  }

  const toggleDone = async (idx: number) => {
    const newItems = [...items]
    newItems[idx] = { ...newItems[idx], isDone: !newItems[idx].isDone }
    await saveItems(newItems)
  }

  const deleteItem = async (idx: number) => {
    const newItems = items.filter((_, i) => i !== idx)
    await saveItems(newItems)
  }

  const addTodo = async () => {
    const text = addText.trim()
    if (!text) return
    const newItem: TodoItem = { content: text, date: today(), isLongTerm: addIsLong, isDone: false }
    await saveItems([...items, newItem])
    setAddText('')
    setAddIsLong(false)
    setShowAdd(false)
  }

  const dayItems = items.filter(t => !t.isLongTerm && !t.isDone)
  const longItems = items.filter(t => t.isLongTerm && !t.isDone)
  const doneItems = items.filter(t => t.isDone)

  return (
    <div style="display: flex; flex-direction: column; height: 100%; overflow: hidden;">
      <TopNav title="待办事项" />

      {/* 添加区域 — 固定 */}
      <div style="display: flex; gap: 8px; padding: 12px 16px; flex-shrink: 0;">
        <input
          class="input-field"
          value={addText}
          onInput={(e: any) => setAddText(e.target.value)}
          placeholder="输入新待办..."
          onKeyDown={(e: any) => { if (e.key === 'Enter') addTodo() }}
          style="flex: 1; margin-bottom: 0;"
        />
        <div style="display: flex; gap: 4px;">
          <button class={`chip ${!addIsLong ? 'active' : ''}`} onClick={() => setAddIsLong(false)}>
            近期
          </button>
          <button class={`chip ${addIsLong ? 'active' : ''}`} onClick={() => setAddIsLong(true)}>
            长期
          </button>
        </div>
        <button class="btn btn-primary btn-sm" onClick={addTodo} disabled={!addText.trim()}>添加</button>
      </div>

      {/* 可滚动内容区 */}
      <div style="flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; padding: 0 16px; padding-bottom: 80px;">
        {loading ? <LoadingSpinner /> : (
          <>
            {dayItems.length > 0 && (
              <>
                <div style="font-size: 13px; font-weight: 600; color: var(--color-primary); padding: 12px 4px 8px;">
                  近期待办
                </div>
                {dayItems.map((item, i) => (
                  <TodoCard
                    key={`day-${i}`}
                    item={item}
                    onToggle={() => toggleDone(items.indexOf(item))}
                    onDelete={() => deleteItem(items.indexOf(item))}
                  />
                ))}
              </>
            )}

            {longItems.length > 0 && (
              <>
                <div style="font-size: 13px; font-weight: 600; color: var(--color-purple); padding: 12px 4px 8px;">
                  长期计划
                </div>
                {longItems.map((item, i) => (
                  <TodoCard
                    key={`long-${i}`}
                    item={item}
                    onToggle={() => toggleDone(items.indexOf(item))}
                    onDelete={() => deleteItem(items.indexOf(item))}
                  />
                ))}
              </>
            )}

            {doneItems.length > 0 && (
              <>
                <div class="separator" style="margin: 16px 0;"/>
                <div style="font-size: 13px; font-weight: 600; color: var(--color-text-tertiary); padding: 4px 4px 8px;">
                  已完成
                </div>
                {doneItems.map((item, i) => (
                  <TodoCard
                    key={`done-${i}`}
                    item={item}
                    onToggle={() => toggleDone(items.indexOf(item))}
                    onDelete={() => deleteItem(items.indexOf(item))}
                  />
                ))}
              </>
            )}

            {items.length === 0 && <EmptyState icon="✅" text="暂无待办事项" />}
          </>
        )}
      </div>

      {errorMsg && (
        <div class="snackbar error">
          {errorMsg}
          <button onClick={() => setErrorMsg('')} style="margin-left: 12px; color: #fff; font-weight: 600;">关闭</button>
        </div>
      )}
    </div>
  )
}
