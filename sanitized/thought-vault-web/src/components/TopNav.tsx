import { h } from 'preact'

interface TopNavProps {
  title: string
  onBack?: () => void
  rightAction?: { label: string; onClick: () => void }
}

export function TopNav({ title, onBack, rightAction }: TopNavProps) {
  return (
    <div class="nav-bar safe-top" style={`height: calc(var(--nav-height) + var(--safe-area-top)); padding-top: var(--safe-area-top)`}>
      {onBack && (
        <button class="nav-bar-back" onClick={onBack}>
          ←
        </button>
      )}
      <span class="nav-bar-title">{title}</span>
      {rightAction && (
        <button class="nav-bar-action" onClick={rightAction.onClick}>
          {rightAction.label}
        </button>
      )}
    </div>
  )
}
