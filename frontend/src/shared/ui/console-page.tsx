import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from './button'
import { LoadingSpinner } from '@/components/feedback'

interface ConsolePageProps {
  title: string
  description?: string
  actions?: ReactNode
  /** Optional path for a back button rendered next to the title actions. */
  backTo?: string
  loading?: boolean
  error?: string
  children: ReactNode
}

export function ConsolePage({ title, description, actions, backTo, loading, error, children }: ConsolePageProps) {
  const navigate = useNavigate()
  return (
    <div className="workspace-page">
      <div className="workspace-strip">
        <div className="workspace-title">
          <h1>{title}</h1>
          {description ? <p>{description}</p> : null}
        </div>
        <div className="action-row">
          {backTo ? (
            <Button variant="ghost" onClick={() => navigate(backTo)}>
              返回
            </Button>
          ) : null}
          {actions}
        </div>
      </div>
      {error ? <p className="error-banner">{error}</p> : null}
      {loading ? <LoadingSpinner /> : children}
    </div>
  )
}
