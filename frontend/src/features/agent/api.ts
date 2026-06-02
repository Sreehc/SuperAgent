import { apiGet, http } from '../../api/http'
import type {
  AgentPagedResult,
  AgentRunDetail,
  AgentRunSummary,
  PluginItem,
  ToolCallDetail,
} from './types'

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

export function listAgentRuns(params?: Record<string, string | number | undefined>) {
  return apiGet<AgentPagedResult<AgentRunSummary>>(`/admin/agent-runs${buildQuery(params)}`)
}

export function getAgentRunDetail(runId: number) {
  return apiGet<AgentRunDetail>(`/admin/agent-runs/${runId}/detail`)
}

export function getAgentRunByExchange(exchangeId: number) {
  return apiGet<AgentRunSummary>(`/admin/agent-runs/by-exchange/${exchangeId}`)
}

export function listToolCalls(params?: Record<string, string | number | undefined>) {
  return apiGet<ToolCallDetail[]>(`/admin/tool-calls${buildQuery(params)}`)
}

export function listPlugins() {
  return apiGet<PluginItem[]>('/admin/plugins')
}

export function updatePlugin(pluginId: number, enabled: boolean) {
  return http.patch(`/admin/plugins/${pluginId}`, { enabled })
}
