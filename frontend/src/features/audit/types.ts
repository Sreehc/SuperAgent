export interface AuditLogItem {
  id: number
  actorId: number | null
  action: string
  resourceType: string
  resourceId: number | null
  detail: Record<string, unknown>
  createdAt: string
}

export interface AuditPagedResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}
