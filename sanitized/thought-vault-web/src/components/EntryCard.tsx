import { h } from 'preact'

interface EntryCardProps {
  time: string
  content: string
  refinedContent?: string
  category?: string
}

export function EntryCard({ time, content, refinedContent, category }: EntryCardProps) {
  const display = refinedContent || content
  return (
    <div class="card" style="margin-bottom: 8px; display: flex; gap: 14px;">
      <div style="font-family: var(--font-mono); font-size: 14px; color: var(--color-primary);
                  font-weight: 500; min-width: 44px; padding-top: 2px;">
        {time}
      </div>
      <div style="flex: 1; min-width: 0;">
        {category && (
          <div style="font-size: 11px; font-weight: 600; color: var(--color-primary);
                      text-transform: uppercase; margin-bottom: 4px; letter-spacing: 0.5px;">
            {category}
          </div>
        )}
        <div style="font-size: 15px; line-height: 1.5; color: var(--color-text);
                    overflow-wrap: break-word; word-break: break-word; white-space: pre-wrap;">
          {display}
        </div>
      </div>
    </div>
  )
}
