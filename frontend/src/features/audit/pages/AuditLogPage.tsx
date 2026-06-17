import { useQuery } from '@tanstack/react-query'
import type { ColumnDef } from '@tanstack/react-table'
import { listAuditLogs } from '../api'
import type { AuditLogItem } from '../types'
import { ConsolePage } from '../../../shared/ui/console-page'
import { DataTable } from '../../../shared/ui/data-table'

const columns: ColumnDef<AuditLogItem, unknown>[] = [
  { accessorKey: 'id', header: 'ID', cell: (c) => <span className="mono">{c.row.original.id}</span> },
  { accessorKey: 'action', header: '操作' },
  { accessorKey: 'resourceType', header: '资源类型' },
  { accessorKey: 'resourceId', header: '资源 ID', cell: (c) => c.row.original.resourceId ?? '—' },
  { accessorKey: 'actorId', header: '操作者', cell: (c) => c.row.original.actorId ?? '系统' },
  { accessorKey: 'createdAt', header: '时间', cell: (c) => new Date(c.row.original.createdAt).toLocaleString('zh-CN') },
]

export function AuditLogPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['audit-logs'],
    queryFn: () => listAuditLogs({ pageSize: 50 }),
  })

  return (
    <ConsolePage title="审计日志" description="记录租户内的管理操作与关键资源变更，用于安全审计与回溯。">
      <DataTable
        columns={columns}
        data={data?.data.items ?? []}
        emptyLabel={isLoading ? '加载中…' : '暂无审计记录'}
      />
    </ConsolePage>
  )
}

export default AuditLogPage
