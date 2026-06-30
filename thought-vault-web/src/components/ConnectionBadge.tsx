import { h, Fragment } from 'preact'
import { connectionStatus, config } from '../signals/app-state'

export function ConnectionBadge() {
  const status = connectionStatus.value
  const cfg = config.value

  if (!cfg || status === 'not_configured') {
    return <span class="badge badge-unknown">未配置</span>
  }
  if (status === 'connected') {
    return <span class="badge badge-connected">已连接</span>
  }
  if (status === 'error') {
    return <span class="badge badge-error">连接失败</span>
  }
  return <span class="badge badge-unknown">检测中...</span>
}
