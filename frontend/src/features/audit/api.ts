import { apiGet } from '../../api/http'
import type { AuditLogItem, AuditPagedResult } from './types'

function buildQuery(params?: Record<string, string | number | undefined>) {
  if (!params) {
    return ''
  }
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && `${value}`.trim() !== '') {
      query.set(key, `${value}`)
    }
  })
  const serialized = query.toString()
  return serialized ? `?${serialized}` : ''
}

export function listAuditLogs(params?: Record<string, string | number | undefined>) {
  return apiGet<AuditPagedResult<AuditLogItem>>(`/admin/audit-logs${buildQuery(params)}`)
}
