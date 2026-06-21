import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { createColumnHelper } from '@tanstack/react-table'
import { Badge, Button, ConsolePage, DataTable } from '@/shared/ui'
import { listAdminTraces } from '../api'
import type { TraceListItem } from '../types'
import { formatDateTime, formatDurationMs } from '@/shared/lib/format'

const column = createColumnHelper<TraceListItem>()
const INITIAL_FILTERS = {
  status: '',
  executionMode: '',
  from: '',
  to: '',
  sessionId: '',
  userId: '',
}

function toNumberFilter(value: string) {
  const trimmed = value.trim()
  if (!trimmed) return undefined

  const numericValue = Number(trimmed)
  return Number.isFinite(numericValue) ? numericValue : undefined
}

function toIsoFilter(value: string) {
  if (!value) return undefined

  const date = new Date(value)
  return Number.isFinite(date.getTime()) ? date.toISOString() : undefined
}

function statusTone(status: string) {
  if (status === 'success') return 'success' as const
  if (status === 'failed' || status === 'error') return 'danger' as const
  return 'neutral' as const
}

export function TraceListPage() {
  const navigate = useNavigate()
  const [filters, setFilters] = useState(INITIAL_FILTERS)
  const traceQueryParams = useMemo(
    () => ({
      pageSize: 50,
      status: filters.status || undefined,
      executionMode: filters.executionMode || undefined,
      from: toIsoFilter(filters.from),
      to: toIsoFilter(filters.to),
      sessionId: toNumberFilter(filters.sessionId),
      userId: toNumberFilter(filters.userId),
    }),
    [filters],
  )
  const { data, isLoading, refetch, isFetching, isError } = useQuery({
    queryKey: ['admin-traces', traceQueryParams],
    queryFn: () => listAdminTraces(traceQueryParams),
  })

  const rows = data?.data.items ?? []

  const columns = useMemo(
    () => [
      column.accessor('exchangeId', { header: 'Exchange', cell: (info) => <code>#{info.getValue()}</code> }),
      column.accessor('sessionId', { header: '会话', cell: (info) => <code>{info.getValue()}</code> }),
      column.accessor('executionMode', { header: '模式' }),
      column.accessor('status', { header: '状态', cell: (info) => <Badge tone={statusTone(info.getValue())}>{info.getValue()}</Badge> }),
      column.accessor('durationMs', { header: '耗时', cell: (info) => <span className="numeric">{formatDurationMs(info.getValue())}</span> }),
      column.accessor('startedAt', { header: '开始时间', cell: (info) => formatDateTime(info.getValue()) }),
    ],
    [],
  )

  return (
    <ConsolePage
      title="Trace"
      description="检索与执行链路的全量追踪记录。"
      actions={
        <Button
          data-testid="trace-refresh"
          variant="secondary"
          onClick={() => refetch()}
          loading={isFetching}
          disabled={false}
        >
          刷新
        </Button>
      }
      error={isError ? '加载 Trace 列表失败。' : undefined}
    >
      <div className="filter-row">
        <label className="field" style={{ flex: '0 1 160px' }}>
          <span>状态</span>
          <select
            value={filters.status}
            onChange={(event) => setFilters((value) => ({ ...value, status: event.target.value }))}
          >
            <option value="">全部</option>
            <option value="success">success</option>
            <option value="failed">failed</option>
            <option value="error">error</option>
          </select>
        </label>
        <label className="field" style={{ flex: '0 1 180px' }}>
          <span>执行模式</span>
          <select
            value={filters.executionMode}
            onChange={(event) => setFilters((value) => ({ ...value, executionMode: event.target.value }))}
          >
            <option value="">全部</option>
            <option value="CLARIFICATION">CLARIFICATION</option>
            <option value="RAG_QA">RAG_QA</option>
            <option value="REACT_AGENT">REACT_AGENT</option>
          </select>
        </label>
        <label className="field" style={{ flex: '1 1 190px' }}>
          <span>开始时间</span>
          <input
            type="datetime-local"
            value={filters.from}
            onChange={(event) => setFilters((value) => ({ ...value, from: event.target.value }))}
          />
        </label>
        <label className="field" style={{ flex: '1 1 190px' }}>
          <span>结束时间</span>
          <input
            type="datetime-local"
            value={filters.to}
            onChange={(event) => setFilters((value) => ({ ...value, to: event.target.value }))}
          />
        </label>
        <label className="field" style={{ flex: '0 1 140px' }}>
          <span>会话 ID</span>
          <input
            inputMode="numeric"
            value={filters.sessionId}
            onChange={(event) => setFilters((value) => ({ ...value, sessionId: event.target.value }))}
            placeholder="Session"
          />
        </label>
        <label className="field" style={{ flex: '0 1 140px' }}>
          <span>用户 ID</span>
          <input
            inputMode="numeric"
            value={filters.userId}
            onChange={(event) => setFilters((value) => ({ ...value, userId: event.target.value }))}
            placeholder="User"
          />
        </label>
        <Button variant="ghost" onClick={() => setFilters(INITIAL_FILTERS)}>
          清空筛选
        </Button>
      </div>
      <DataTable
        columns={columns as never}
        data={rows}
        rowTestId={(row) => `trace-row-${row.exchangeId}`}
        onRowClick={(row) => navigate(`/traces/${row.exchangeId}`)}
        emptyLabel="暂无 Trace 记录"
        loading={isLoading}
      />
    </ConsolePage>
  )
}
