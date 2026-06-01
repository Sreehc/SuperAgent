import { apiGet } from '../../api/http'
import type { TraceDetail, TraceListItem, TracePagedResult } from './types'

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

export function listAdminTraces(params?: Record<string, string | number | undefined>) {
  return apiGet<TracePagedResult<TraceListItem>>(`/admin/traces${buildQuery(params)}`)
}

export function getAdminTrace(exchangeId: number) {
  return apiGet<TraceDetail>(`/admin/traces/${exchangeId}`)
}
