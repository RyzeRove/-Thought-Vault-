import { h } from 'preact'
import { useEffect, useState } from 'preact/hooks'
import { HomePage } from './pages/HomePage'
import { HistoryPage } from './pages/HistoryPage'
import { DetailPage } from './pages/DetailPage'
import { SettingsPage } from './pages/SettingsPage'
import { TodoPage } from './pages/TodoPage'
import { SummaryPage } from './pages/SummaryPage'
import { TabBar } from './components/TabBar'
import { initialize } from './signals/app-state'
import { today } from './lib/date-utils'

type Route =
  | { page: 'home' }
  | { page: 'history' }
  | { page: 'detail'; date: string }
  | { page: 'settings' }
  | { page: 'todos' }
  | { page: 'summary' }

function parseRoute(hash: string): Route {
  const path = hash.replace(/^#/, '') || '/home'
  if (path.startsWith('/detail/')) {
    return { page: 'detail', date: path.split('/detail/')[1] }
  }
  switch (path) {
    case '/history': return { page: 'history' }
    case '/settings': return { page: 'settings' }
    case '/todos': return { page: 'todos' }
    case '/summary': return { page: 'summary' }
    default: return { page: 'home' }
  }
}

export function App() {
  const [route, setRoute] = useState<Route>(parseRoute(location.hash))
  const [ready, setReady] = useState(false)

  useEffect(() => {
    initialize().then(() => setReady(true))

    const onHashChange = () => setRoute(parseRoute(location.hash))
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  const navigate = (hash: string) => {
    location.hash = hash
  }

  const goBack = () => {
    history.back()
  }

  if (!ready) {
    return (
      <div class="page" style="align-items: center; justify-content: center;">
        <div class="spinner" style="width: 32px; height: 32px;" />
        <div style="margin-top:16px; color: var(--color-text-secondary); font-size: 15px;">加载中...</div>
      </div>
    )
  }

  const renderPage = () => {
    switch (route.page) {
      case 'home':
        return <HomePage />
      case 'history':
        return <HistoryPage onNavigate={navigate} />
      case 'detail':
        return <DetailPage date={route.date} onBack={goBack} />
      case 'settings':
        return <SettingsPage />
      case 'todos':
        return <TodoPage />
      case 'summary':
        return <SummaryPage />
    }
  }

  const showTabBar = route.page === 'home' || route.page === 'history' ||
                     route.page === 'settings' || route.page === 'todos' || route.page === 'summary'

  return (
    <div style="height: 100%; display: flex; flex-direction: column; background: var(--color-surface);">
      <div style="flex: 1; overflow: hidden; position: relative; transform: translateZ(0);">
        {renderPage()}
      </div>
      {showTabBar && (
        <TabBar
          selectedDate={today()}
          onSelectDate={() => {
            setRoute({ page: 'home' })
          }}
        />
      )}
    </div>
  )
}
