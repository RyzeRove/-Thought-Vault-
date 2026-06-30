import { h } from 'preact'

/** 极简 Markdown 渲染器（用于日报展示） */
interface MarkdownViewerProps {
  content: string
}

export function MarkdownViewer({ content }: MarkdownViewerProps) {
  const html = renderMarkdown(content)
  return <div class="markdown-body" dangerouslySetInnerHTML={{ __html: html }} />
}

function renderMarkdown(md: string): string {
  const lines = md.split('\n')
  let html = ''
  let inTable = false
  let inCodeBlock = false

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const trimmed = line.trim()

    // Code block
    if (trimmed.startsWith('```')) {
      if (inCodeBlock) {
        html += '</pre>\n'
        inCodeBlock = false
      } else {
        html += '<pre style="background:var(--color-surface);padding:12px;border-radius:8px;overflow-x:auto;font-size:13px;">\n'
        inCodeBlock = true
      }
      continue
    }

    if (inCodeBlock) {
      html += escapeHtml(line) + '\n'
      continue
    }

    // Table
    if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
      if (!inTable) {
        html += '<table style="width:100%;border-collapse:collapse;font-size:14px;margin:12px 0">\n'
        inTable = true
      }
      html += '<tr>' + trimmed.split('|').slice(1, -1).map(cell => {
        const isHeader = cell.includes('---')
        const tag = isHeader ? 'th' : 'td'
        const style = 'padding:8px 12px;border-bottom:1px solid var(--color-border);text-align:left;' +
          (isHeader ? 'font-weight:600;' : '')
        return `<${tag} style="${style}">${cell.trim()}</${tag}>`
      }).join('') + '</tr>\n'
      continue
    } else if (inTable) {
      html += '</table>\n'
      inTable = false
    }

    // H1
    if (line.startsWith('# ') && !line.startsWith('## ')) {
      html += `<h1 style="font-size:22px;font-weight:700;margin:20px 0 12px;color:var(--color-text)">${processInline(line.slice(2))}</h1>\n`
      continue
    }
    // H2
    if (line.startsWith('## ')) {
      html += `<h2 style="font-size:19px;font-weight:600;margin:16px 0 10px;color:var(--color-text)">${processInline(line.slice(3))}</h2>\n`
      continue
    }
    // H3
    if (line.startsWith('### ')) {
      html += `<h3 style="font-size:16px;font-weight:600;margin:12px 0 8px;color:var(--color-text-secondary)">${processInline(line.slice(4))}</h3>\n`
      continue
    }

    // Blockquote
    if (line.startsWith('> ')) {
      html += `<blockquote style="border-left:3px solid var(--color-primary);padding:8px 16px;margin:8px 0;background:var(--color-primary-light);border-radius:0 8px 8px 0;color:var(--color-text-secondary)">${processInline(line.slice(2))}</blockquote>\n`
      continue
    }

    // Horizontal rule
    if (trimmed === '---' || trimmed === '***') {
      html += '<hr style="border:none;height:0.5px;background:var(--color-separator);margin:16px 0">\n'
      continue
    }

    // Unordered list
    if (/^[-*]\s/.test(trimmed)) {
      html += `<li style="margin-bottom:6px;font-size:15px;line-height:1.6">${processInline(trimmed.slice(2))}</li>\n`
      continue
    }

    // Empty line
    if (!trimmed) {
      html += '<div style="height:8px"></div>\n'
      continue
    }

    // Regular paragraph
    html += `<p style="font-size:15px;line-height:1.6;margin:0 0 8px;color:var(--color-text)">${processInline(trimmed)}</p>\n`
  }

  if (inTable) html += '</table>\n'
  if (inCodeBlock) html += '</pre>\n'

  return html
}

function processInline(text: string): string {
  // Bold
  text = text.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  // Italic
  text = text.replace(/\*(.+?)\*/g, '<em>$1</em>')
  // Inline code
  text = text.replace(/`([^`]+)`/g, '<code style="background:var(--color-surface);padding:2px 6px;border-radius:4px;font-size:13px;font-family:var(--font-mono)">$1</code>')
  return text
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
