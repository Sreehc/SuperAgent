import type { RunTimelineItem } from '../types'

interface ExecutionSummaryProps {
  items: RunTimelineItem[]
  limit?: number
}

export function ExecutionSummary({ items, limit = 6 }: ExecutionSummaryProps) {
  if (items.length === 0) {
    return null
  }

  const visibleItems = items.slice(-limit)
  const hiddenCount = Math.max(0, items.length - visibleItems.length)

  return (
    <section className="execution-summary" aria-label="执行摘要">
      <div className="execution-summary__header">
        <span>执行摘要</span>
        {hiddenCount > 0 && <span>还有 {hiddenCount} 个执行事件</span>}
      </div>
      <div className="execution-summary__list">
        {visibleItems.map((item, index) => (
          <div key={`${item.type}-${item.title}-${index}`} className={`execution-summary__item execution-summary__item--${item.type}`}>
            <span className="execution-summary__marker" aria-hidden="true" />
            <div className="execution-summary__body">
              <strong>{item.title}</strong>
              <p>{item.summary}</p>
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}
