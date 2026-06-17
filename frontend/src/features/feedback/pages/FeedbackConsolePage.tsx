import { useQuery } from '@tanstack/react-query'
import { listAdminFeedbacks } from '../api'
import type { ConversationFeedback, FeedbackRating } from '../types'
import { useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { ConsolePage } from '../../../shared/ui/console-page'
import { DataTable } from '../../../shared/ui/data-table'
import { Badge } from '../../../shared/ui/badge'

const RATING_LABEL: Record<FeedbackRating, string> = {
  up: '赞同',
  down: '不满意',
  correction: '更正',
}

const RATING_TONE: Record<FeedbackRating, 'success' | 'warning' | 'accent'> = {
  up: 'success',
  down: 'warning',
  correction: 'accent',
}

const columns: ColumnDef<ConversationFeedback, unknown>[] = [
  { accessorKey: 'messageId', header: '消息 ID', cell: (c) => <span className="mono">{c.row.original.messageId}</span> },
  {
    accessorKey: 'rating',
    header: '评价',
    cell: (c) => <Badge tone={RATING_TONE[c.row.original.rating]}>{RATING_LABEL[c.row.original.rating]}</Badge>,
  },
  { accessorKey: 'comment', header: '评论', cell: (c) => c.row.original.comment || '—' },
  { accessorKey: 'correction', header: '更正建议', cell: (c) => c.row.original.correction || '—' },
  { accessorKey: 'createdAt', header: '时间', cell: (c) => new Date(c.row.original.createdAt).toLocaleString('zh-CN') },
]

export function FeedbackConsolePage() {
  const [rating, setRating] = useState<FeedbackRating | ''>('')
  const { data, isLoading } = useQuery({
    queryKey: ['admin-feedbacks', rating],
    queryFn: () => listAdminFeedbacks({ rating, pageSize: 50 }),
  })

  return (
    <ConsolePage title="反馈" description="汇总用户对回答的赞同、不满意与更正建议，用于质量分析与评测取数。">
      <div className="filter-row">
        <label className="field" style={{ maxWidth: 220 }}>
          <span>评价类型</span>
          <select value={rating} onChange={(e) => setRating(e.target.value as FeedbackRating | '')}>
            <option value="">全部</option>
            <option value="up">赞同</option>
            <option value="down">不满意</option>
            <option value="correction">更正</option>
          </select>
        </label>
      </div>
      <DataTable
        columns={columns}
        data={data?.data.items ?? []}
        emptyLabel={isLoading ? '加载中…' : '暂无反馈记录'}
      />
    </ConsolePage>
  )
}

export default FeedbackConsolePage
