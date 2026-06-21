import { EmptyState, ErrorState, LoadingState, TableStateRow } from '@/shared/ui/status'

export { EmptyState, ErrorState, LoadingState, TableStateRow }

export function LoadingSpinner({ label = '加载中…' }: { label?: string }) {
  return <LoadingState label={label} size="compact" lines={2} />
}

export function SkeletonCard({ lines = 3 }: { lines?: number }) {
  return <LoadingState label="加载中…" lines={lines} />
}
