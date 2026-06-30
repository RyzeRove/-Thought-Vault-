import { h } from 'preact'
import { today } from '../lib/date-utils'

interface Props {
  selectedDate: string
  onSelectDate: (d: string) => void
}

function TabButton({ icon, label, href, isActive, onClick }: {
  icon: string; label: string; href?: string; isActive: boolean; onClick?: () => void
}) {
  const attrs: any = {
    class: `tab-item${isActive ? ' active' : ''}`,
  }
  if (href) {
    attrs.href = href
  } else {
    attrs.onClick = onClick
  }
  // 用 div 而不是 a/button 避免 link 导航延迟
  const Tag = href ? 'a' : 'button'
  return (
    <Tag {...attrs}>
      <span class="tab-item-icon">{icon}</span>
      <span class="tab-item-label">{label}</span>
    </Tag>
  )
}

export function TabBar({ selectedDate, onSelectDate }: Props) {
  const t = today()
  const isHome = selectedDate === t
  return (
    <div class="tab-bar">
      <TabButton icon="✏️" label="今日" isActive={isHome} onClick={() => onSelectDate(t)} />
      <TabButton icon="📅" label="历史" isActive={false} href="#/history" />
      <TabButton icon="📊" label="汇总" isActive={false} href="#/summary" />
      <TabButton icon="✅" label="待办" isActive={false} href="#/todos" />
      <TabButton icon="⚙️" label="设置" isActive={false} href="#/settings" />
    </div>
  )
}
