export type EvalRunStatus = 'pending' | 'running' | 'success' | 'failed'

export interface EvalSuite {
  id: number
  tenantId: number | null
  suiteKey: string
  name: string
  description: string | null
  caseCount: number
  runCount: number
  createdAt: string
  updatedAt: string
}

export interface EvalCase {
  id: number
  suiteId: number
  caseKey: string
  input: Record<string, unknown>
  expected: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface EvalRun {
  id: number
  suiteId: number
  suiteKey: string
  status: EvalRunStatus
  passedCount: number
  failedCount: number
  report: Record<string, unknown>
  createdAt: string
  updatedAt: string
  finishedAt: string | null
}

export interface EvalSuiteDetail {
  suite: EvalSuite
  cases: EvalCase[]
  recentRuns: EvalRun[]
}

export interface EvalPagedResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}
