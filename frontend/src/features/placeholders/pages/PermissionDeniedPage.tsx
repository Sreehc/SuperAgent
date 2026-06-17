import { useNavigate } from 'react-router-dom'
import { ShieldWarning } from '@phosphor-icons/react'
import { Button } from '../../../shared/ui/button'

export function PermissionDeniedPage() {
  const navigate = useNavigate()
  return (
    <div className="workspace-page">
      <section className="surface-box" style={{ display: 'grid', gap: 14, justifyItems: 'start', padding: 28 }}>
        <ShieldWarning size={40} weight="duotone" aria-hidden="true" />
        <div className="workspace-title">
          <h1>无权访问</h1>
          <p>当前租户角色无权访问这个页面。如需访问请联系租户管理员调整权限，或切换到有权限的租户。</p>
        </div>
        <Button onClick={() => navigate('/chat')}>返回对话工作台</Button>
      </section>
    </div>
  )
}

export default PermissionDeniedPage
