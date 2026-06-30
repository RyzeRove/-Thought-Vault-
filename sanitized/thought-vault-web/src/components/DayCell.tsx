import { h } from 'preact'

interface DayCellProps {
  day: number
  hasEntries: boolean
  isToday: boolean
  isSelected: boolean
  onClick: () => void
}

export function DayCell({ day, hasEntries, isToday, isSelected, onClick }: DayCellProps) {
  if (day === 0) {
    return <div style="aspect-ratio: 1;" />
  }

  return (
    <button
      onClick={onClick}
      style={`
        aspect-ratio: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        position: relative;
        border-radius: 50%;
        font-size: 16px;
        font-weight: ${isToday ? 600 : 400};
        color: ${isSelected ? '#fff' : isToday ? 'var(--color-primary)' : 'var(--color-text)'};
        background: ${isSelected ? 'var(--color-primary)' : 'transparent'};
        transition: all 0.15s;
      `}
    >
      {day}
      {hasEntries && !isSelected && (
        <span style={`
          position: absolute;
          bottom: 4px;
          width: 5px;
          height: 5px;
          border-radius: 50%;
          background: var(--color-primary);
          left: 50%;
          transform: translateX(-50%);
        `} />
      )}
    </button>
  )
}
