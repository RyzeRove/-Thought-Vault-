import { h } from 'preact'

interface EmptyStateProps {
  icon?: string
  text?: string
}

export function EmptyState({ icon = '📝', text = '暂无记录' }: EmptyStateProps) {
  return (
    <div class="empty-state">
      <div class="empty-state-icon">{icon}</div>
      <div class="empty-state-text">{text}</div>
    </div>
  )
}
