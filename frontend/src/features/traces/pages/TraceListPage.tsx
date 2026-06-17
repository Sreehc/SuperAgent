import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { createColumnHelper } from '@tanstack/react-table'
import { Badge, Button, ConsolePage, DataTable } from '@/shared/ui'
import { LoadingSpinner } from '@/components/feedback'
import { listAdminTraces } from '../api'
import type { TraceListItem } from '../types'

const column = createColumnHelper<TraceListItem>()

function statusTone(status: string) {
  if (status === 'success') return 'success' as const
  if (status === 'failed' || status === 'error') return 'danger' as const
  return 'neutral' as const
}

export function TraceListPage() {
  const navigate = useNavigate()
  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['admin-traces'],
    queryFn: () => listAdminTraces({ pageSize: 50 }),
  })

  const rows = data?.data.items ?? []

  const columns = useMemo(
    () => [
      column.accessor('exchangeId', { header: 'Exchange', cell: (info) => <code>#{info.getValue()}</code> }),
      column.accessor('sessionId', { header: '会话', cell: (info) => <code>{info.getValue()}</code> }),
      column.accessor('executionMode', { header: '模式' }),
      column.accessor('status', { header: '状态', cell: (info) => <Badge tone={statusTone(info.getValue())}>{info.getValue()}</Badge> }),
      column.accessor('durationMs', { header: '耗时(ms)', cell: (info) => <span className="numeric">{info.getValue()}</span> }),
      column.accessor('startedAt', { header: '开始时间' }),
    ],
    [],
  )

  return (
    <ConsolePage
      title="Trace"
      description="检索与执行链路的全量追踪记录。"
      actions={
        <Button data-testid="trace-refresh" variant="secondary" onClick={() => refetch()} loading={isFetching}>
          刷新
        </Button>
      }
    >
      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <DataTable
          columns={columns as never}
          data={rows}
          rowTestId={(row) => `trace-row-${row.exchangeId}`}
          onRowClick={(row) => navigate(`/traces/${row.exchangeId}`)}
          emptyLabel="暂无 Trace 记录"
        />
      )}
    </ConsolePage>
  )
}
