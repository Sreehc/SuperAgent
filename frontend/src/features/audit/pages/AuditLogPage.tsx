import { useQuery } from '@tanstack/react-query'
import type { ColumnDef } from '@tanstack/react-table'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listAuditLogs } from '../api'
import type { AuditLogItem } from '../types'
import { ConsolePage } from '../../../shared/ui/console-page'
import { DataTable } from '../../../shared/ui/data-table'
import { formatDateTime } from '@/shared/lib/format'
import { Button, DetailDrawer } from '@/shared/ui'

const INITIAL_FILTERS = {
  action: '',
  resourceType: '',
  resourceId: '',
  actorId: '',
  from: '',
  to: '',
}

type AuditFilters = typeof INITIAL_FILTERS

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

function compactAuditParams(filters: AuditFilters) {
  const hasFilter = Object.values(filters).some((value) => value.trim() !== '')

  if (!hasFilter) {
    return { pageSize: 50 }
  }

  return {
    pageSize: 50,
    action: filters.action.trim() || undefined,
    resourceType: filters.resourceType.trim() || undefined,
    resourceId: toNumberFilter(filters.resourceId),
    actorId: toNumberFilter(filters.actorId),
    from: toIsoFilter(filters.from),
    to: toIsoFilter(filters.to),
  }
}

function optionalText(value: string | number | null | undefined, fallback = '—') {
  if (value === undefined || value === null || value === '') return fallback
  return `${value}`
}

function resourceLabel(log: AuditLogItem) {
  if (log.resourceId == null) return log.resourceType
  return `${log.resourceType} #${log.resourceId}`
}

function resourceLink(log: AuditLogItem) {
  if (log.resourceId == null) return null

  if (log.resourceType === 'knowledge_document') {
    return {
      href: `/documents/${log.resourceId}`,
      label: `打开文档 ${log.resourceId}`,
    }
  }

  if (log.resourceType === 'conversation_exchange') {
    return {
      href: `/traces/${log.resourceId}`,
      label: `打开 Trace ${log.resourceId}`,
    }
  }

  return null
}

function auditColumns(onOpenDetail: (log: AuditLogItem) => void): ColumnDef<AuditLogItem, unknown>[] {
  return [
    { accessorKey: 'id', header: 'ID', cell: (c) => <span className="mono">{c.row.original.id}</span> },
    { accessorKey: 'action', header: '操作' },
    { accessorKey: 'resourceType', header: '资源类型' },
    { accessorKey: 'resourceId', header: '资源 ID', cell: (c) => c.row.original.resourceId ?? '—' },
    { accessorKey: 'actorId', header: '操作者', cell: (c) => c.row.original.actorId ?? '系统' },
    { accessorKey: 'createdAt', header: '时间', cell: (c) => formatDateTime(c.row.original.createdAt) },
    {
      id: 'actions',
      header: '详情',
      enableSorting: false,
      cell: (c) => (
        <Button size="sm" variant="secondary" onClick={() => onOpenDetail(c.row.original)}>
          查看审计 {c.row.original.id}
        </Button>
      ),
    },
  ]
}

export function AuditLogPage() {
  const [filters, setFilters] = useState(INITIAL_FILTERS)
  const [selectedLog, setSelectedLog] = useState<AuditLogItem | null>(null)
  const auditQueryParams = useMemo(() => compactAuditParams(filters), [filters])
  const columns = useMemo(() => auditColumns(setSelectedLog), [])
  const { data, isLoading } = useQuery({
    queryKey: ['audit-logs', auditQueryParams],
    queryFn: () => listAuditLogs(auditQueryParams),
  })

  return (
    <ConsolePage title="审计日志" description="记录租户内的管理操作与关键资源变更，用于安全审计与回溯。">
      <div className="filter-row">
        <label className="field" style={{ flex: '1 1 220px' }}>
          <span>操作</span>
          <input
            value={filters.action}
            onChange={(event) => setFilters((value) => ({ ...value, action: event.target.value }))}
            placeholder="settings.rag.updated"
          />
        </label>
        <label className="field" style={{ flex: '1 1 180px' }}>
          <span>资源类型</span>
          <input
            value={filters.resourceType}
            onChange={(event) => setFilters((value) => ({ ...value, resourceType: event.target.value }))}
            placeholder="runtime_setting"
          />
        </label>
        <label className="field" style={{ flex: '0 1 140px' }}>
          <span>资源 ID</span>
          <input
            inputMode="numeric"
            value={filters.resourceId}
            onChange={(event) => setFilters((value) => ({ ...value, resourceId: event.target.value }))}
            placeholder="Resource"
          />
        </label>
        <label className="field" style={{ flex: '0 1 140px' }}>
          <span>操作者 ID</span>
          <input
            inputMode="numeric"
            value={filters.actorId}
            onChange={(event) => setFilters((value) => ({ ...value, actorId: event.target.value }))}
            placeholder="Actor"
          />
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
        <Button variant="ghost" onClick={() => setFilters(INITIAL_FILTERS)}>
          清空筛选
        </Button>
      </div>
      <DataTable
        columns={columns}
        data={data?.data.items ?? []}
        emptyLabel="暂无审计记录"
        loading={isLoading}
        rowTestId={(log) => `audit-log-${log.id}`}
      />
      <DetailDrawer
        open={selectedLog != null}
        title={`审计日志 #${selectedLog?.id ?? ''}`}
        description={selectedLog ? `${selectedLog.action} · ${formatDateTime(selectedLog.createdAt)}` : undefined}
        onOpenChange={(open) => {
          if (!open) setSelectedLog(null)
        }}
      >
        {selectedLog ? <AuditLogDetailView log={selectedLog} /> : null}
      </DetailDrawer>
    </ConsolePage>
  )
}

function AuditLogDetailView({ log }: { log: AuditLogItem }) {
  const link = resourceLink(log)

  return (
    <div className="trace-card-list">
      <article className="trace-observation">
        <div className="trace-observation__header">
          <div>
            <div className="meta-row">
              <span className="mono">Actor {log.actorId == null ? '系统' : `#${log.actorId}`}</span>
              <span className="mono">Audit #{log.id}</span>
            </div>
            <strong>{log.action}</strong>
          </div>
          <div className="meta-row">
            <span className="metric-chip">{formatDateTime(log.createdAt)}</span>
          </div>
        </div>

        <dl className="reference-detail trace-detail-list">
          <div>
            <dt>操作</dt>
            <dd>{log.action}</dd>
          </div>
          <div>
            <dt>操作者</dt>
            <dd>{log.actorId == null ? '系统' : <span className="mono">Actor #{log.actorId}</span>}</dd>
          </div>
          <div>
            <dt>资源</dt>
            <dd>
              <div className="meta-row">
                <span className="mono">{resourceLabel(log)}</span>
                {link ? (
                  <Button asChild size="sm" variant="text">
                    <Link to={link.href}>{link.label}</Link>
                  </Button>
                ) : (
                  <span className="metadata">{log.resourceId == null ? '资源不可用' : '资源暂无跳转入口'}</span>
                )}
              </div>
            </dd>
          </div>
          <div>
            <dt>资源类型</dt>
            <dd>{log.resourceType}</dd>
          </div>
          <div>
            <dt>资源 ID</dt>
            <dd className="mono">{optionalText(log.resourceId)}</dd>
          </div>
          <div>
            <dt>时间</dt>
            <dd className="mono">{formatDateTime(log.createdAt)}</dd>
          </div>
          <div>
            <dt>Detail</dt>
            <dd>
              <pre className="metadata-pre">{JSON.stringify(log.detail ?? {}, null, 2)}</pre>
            </dd>
          </div>
        </dl>
      </article>
    </div>
  )
}

export default AuditLogPage
