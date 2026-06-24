import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

type StateSize = 'default' | 'compact'

interface LoadingStateProps {
  label?: string
  lines?: number
  size?: StateSize
  className?: string
}

interface EmptyStateProps {
  title: string
  description?: ReactNode
  action?: ReactNode
  size?: StateSize
  className?: string
}

interface ErrorStateProps {
  title?: string
  description?: ReactNode
  action?: ReactNode
  size?: StateSize
  className?: string
}

interface TableStateRowProps {
  state: 'loading' | 'empty' | 'error'
  colSpan: number
  title?: string
  label?: string
  description?: ReactNode
  action?: ReactNode
  skeletonRows?: number
}

function clampCount(value: number, fallback: number) {
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : fallback
}

export function LoadingState({ label = '加载中…', lines = 3, size = 'default', className }: LoadingStateProps) {
  const count = clampCount(lines, 3)

  return (
    <div className={cn('state-card state-card--loading', size === 'compact' && 'state-card--compact', className)} role="status" aria-live="polite" aria-label={label}>
      <div className="state-card__content">
        <span className="state-card__title">{label}</span>
        <div className="state-card__skeleton" aria-hidden="true">
          {Array.from({ length: count }).map((_, index) => (
            <span key={index} className="skeleton-line" data-testid="skeleton-line" style={{ width: index === 0 ? '42%' : undefined }} />
          ))}
        </div>
      </div>
    </div>
  )
}

export function EmptyState({ title, description, action, size = 'default', className }: EmptyStateProps) {
  return (
    <div className={cn('state-card state-card--empty', size === 'compact' && 'state-card--compact', className)} role="status" aria-label={title}>
      <div className="state-card__content">
        <strong className="state-card__title">{title}</strong>
        {description && <p className="state-card__description">{description}</p>}
        {action && <div className="state-card__actions">{action}</div>}
      </div>
    </div>
  )
}

export function ErrorState({ title = '加载失败', description, action, size = 'default', className }: ErrorStateProps) {
  return (
    <div className={cn('state-card state-card--error', size === 'compact' && 'state-card--compact', className)} role="alert">
      <div className="state-card__content">
        <strong className="state-card__title">{title}</strong>
        {description && <p className="state-card__description">{description}</p>}
        {action && <div className="state-card__actions">{action}</div>}
      </div>
    </div>
  )
}

export function TableStateRow({
  state,
  colSpan,
  title,
  label,
  description,
  action,
  skeletonRows = 3,
}: TableStateRowProps) {
  const safeColSpan = Math.max(1, colSpan)
  const count = clampCount(skeletonRows, 3)

  if (state === 'loading') {
    const loadingLabel = label ?? title ?? '加载中…'
    return (
      <tr className="table-state-row table-state-row--loading">
        <td colSpan={safeColSpan}>
          <div className="table-state table-state--loading" role="status" aria-live="polite" aria-label={loadingLabel}>
            <span className="table-state__label">{loadingLabel}</span>
            <div className="table-state__skeleton" aria-hidden="true">
              {Array.from({ length: count }).map((_, index) => (
                <span key={index} className="skeleton-line" data-testid="table-skeleton-row" />
              ))}
            </div>
          </div>
        </td>
      </tr>
    )
  }

  return (
    <tr className={`table-state-row table-state-row--${state}`}>
      <td colSpan={safeColSpan}>
        {state === 'error' ? (
          <ErrorState title={title} description={description} action={action} size="compact" />
        ) : (
          <EmptyState title={title ?? '暂无数据'} description={description} action={action} size="compact" />
        )}
      </td>
    </tr>
  )
}
