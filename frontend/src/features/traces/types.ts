export interface TracePagedResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export interface TraceListItem {
  exchangeId: number
  sessionId: number
  userId: number
  executionMode: string
  status: string
  startedAt: string
  finishedAt: string | null
  durationMs: number
}

export interface TraceStageDetail {
  stageId: number
  stageCode: string
  status: string
  inputSummary: string | null
  outputSummary: string | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  durationMs: number
}

export interface TraceModelCallDetail {
  id: number
  stageId: number | null
  provider: string
  model: string
  callType: string
  promptSummary: string | null
  outputSummary: string | null
  inputTokens: number | null
  outputTokens: number | null
  latencyMs: number | null
  status: string
  errorMessage: string | null
  metadata: Record<string, unknown>
  createdAt: string
}

export interface TraceRetrievalItemDetail {
  id: number
  documentId: number
  chunkId: number
  rankNo: number
  rawScore: number | null
  fusedScore: number | null
  selected: boolean
  metadata: Record<string, unknown>
  createdAt: string
}

export interface TraceRetrievalDetail {
  id: number
  stageId: number | null
  subQuestionNo: number
  channel: string
  queryText: string
  filters: Record<string, unknown>
  resultCount: number
  selectedCount: number
  latencyMs: number | null
  createdAt: string
  items: TraceRetrievalItemDetail[]
}

export interface TraceRerankDetail {
  id: number
  provider: string | null
  model: string | null
  enabled: boolean
  skippedReason: string | null
  inputCount: number
  outputCount: number
  latencyMs: number | null
  status: string
  errorMessage: string | null
  metadata: Record<string, unknown>
  createdAt: string
}

export interface TraceDetail {
  exchangeId: number
  sessionId: number
  userId: number
  executionMode: string
  status: string
  routeReason: string | null
  agentRunId: number | null
  agentRunStatus: string | null
  startedAt: string
  finishedAt: string | null
  durationMs: number
  stages: TraceStageDetail[]
  modelCalls: TraceModelCallDetail[]
  retrievals: TraceRetrievalDetail[]
  reranks: TraceRerankDetail[]
}
