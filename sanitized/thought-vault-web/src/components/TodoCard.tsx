import { h } from 'preact'
import type { TodoItem } from '../lib/types'

interface TodoCardProps {
  item: TodoItem
  onToggle: () => void
  onDelete: () => void
}

export function TodoCard({ item, onToggle, onDelete }: TodoCardProps) {
  if (item.isDone) {
    return (
      <div class="card" style="margin-bottom: 8px; opacity: 0.5; display: flex; align-items: center; gap: 12px;">
        <span style="font-size: 20px;">✅</span>
        <div style="flex: 1; text-decoration: line-through; color: var(--color-text-secondary); font-size: 15px;">
          {item.content}
        </div>
        <button onClick={onDelete} style="padding: 4px 8px; color: var(--color-text-tertiary); font-size: 14px;">
          ✕
        </button>
      </div>
    )
  }

  return (
    <div class="card" style={`margin-bottom: 8px; display: flex; align-items: center; gap: 12px;
                background: ${item.isLongTerm ? 'var(--color-primary-light)' : 'var(--color-card)'}`}>
      <button onClick={onToggle} style="padding: 4px; font-size: 22px; color: var(--(--color-border)); line-height: 1;">
        ○
      </button>
      <div style="flex: 1; min-width: 0;">
        <div style="font-size: 15px; color: var(--color-text); word-break: break-all;">
          {item.content}
        </div>
        <div style="font-size: 12px; color: var(--color-text-tertiary); margin-top: 2px;">
          {item.date}
        </div>
      </div>
      <button onClick={onDelete} style="padding: 4px 8px; color: var(--color-text-tertiary); font-size: 14px;">
        ✕
      </button>
    </div>
  )
}
