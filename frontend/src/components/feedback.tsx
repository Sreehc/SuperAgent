import type { ReactNode } from 'react'

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      {description && <p>{description}</p>}
      {action}
    </div>
  )
}

export function LoadingSpinner({ label = '加载中…' }: { label?: string }) {
  return (
    <div className="loading-spinner" role="status">
      <span className="loading-spinner__dot" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

export function SkeletonCard({ lines = 3 }: { lines?: number }) {
  return (
    <div className="surface-box" aria-hidden="true" style={{ display: 'grid', gap: 10 }}>
      <span className="skeleton-line" style={{ width: '40%' }} />
      {Array.from({ length: lines }).map((_, index) => (
        <span key={index} className="skeleton-line" />
      ))}
    </div>
  )
}
