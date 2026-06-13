import { apiGet, http } from '../../api/http'
import type { EvalCase, EvalPagedResult, EvalRun, EvalRunStatus, EvalSuite, EvalSuiteDetail } from './types'

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

export function listEvalSuites(params?: Record<string, string | number | undefined>) {
  return apiGet<EvalPagedResult<EvalSuite>>(`/admin/evals/suites${buildQuery(params)}`)
}

export function getEvalSuite(suiteId: number) {
  return apiGet<EvalSuiteDetail>(`/admin/evals/suites/${suiteId}`)
}

export function createEvalSuite(payload: { suiteKey: string; name: string; description?: string }) {
  return http.post<{ success: boolean; code: string; message: string; data: EvalSuite; traceId: string }>('/admin/evals/suites', payload)
}

export function createEvalCase(suiteId: number, payload: { caseKey: string; input: Record<string, unknown>; expected: Record<string, unknown> }) {
  return http.post<{ success: boolean; code: string; message: string; data: EvalCase; traceId: string }>(`/admin/evals/suites/${suiteId}/cases`, payload)
}

export function deleteEvalCase(caseId: number) {
  return http.delete<{ success: boolean; code: string; message: string; data: { deleted: boolean }; traceId: string }>(`/admin/evals/cases/${caseId}`)
}

export function listEvalRuns(params?: { page?: number; pageSize?: number; suiteId?: number; status?: EvalRunStatus | '' }) {
  return apiGet<EvalPagedResult<EvalRun>>(`/admin/evals/runs${buildQuery(params)}`)
}

export function createEvalRun(
  suiteId: number,
  payload: { status?: EvalRunStatus | ''; passedCount?: number; failedCount?: number; report: Record<string, unknown> },
) {
  return http.post<{ success: boolean; code: string; message: string; data: EvalRun; traceId: string }>(`/admin/evals/suites/${suiteId}/runs`, payload)
}
