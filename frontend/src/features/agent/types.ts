export interface AgentPagedResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export interface AgentRunSummary {
  runId: number
  sessionId: number
  exchangeId: number | null
  status: string
  memoryStrategy: string
  routeReason: string | null
  modelStepCount: number
  toolCallCount: number
  latestCheckpointNo: number
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface AgentRunStep {
  id: number
  stepNo: number
  phase: string
  status: string
  decisionSummary: string | null
  observationSummary: string | null
  selectedToolId: string | null
  selectedToolReason: string | null
  errorMessage: string | null
  metadata: Record<string, unknown>
  startedAt: string | null
  finishedAt: string | null
}

export interface AgentCheckpoint {
  id: number
  checkpointNo: number
  stepId: number | null
  checkpointType: string
  stable: boolean
  payload: Record<string, unknown>
  createdAt: string
}

export interface ToolCallDetail {
  id: number
  agentRunId: number
  toolId: string
  pluginId: number | null
  pluginVersion: string | null
  requestSummary: string | null
  responseSummary: string | null
  status: string
  latencyMs: number | null
  errorMessage: string | null
  metadata: Record<string, unknown>
  createdAt: string
}

export interface PluginItem {
  pluginId: number
  pluginKey: string
  version: string
  displayName: string
  enabled: boolean
  status: string
  manifest: Record<string, unknown>
  installationConfig: Record<string, unknown>
  enabledTools: string[]
  secretKeys: string[]
  recentErrorCount: number
  updatedAt: string
}

export interface ToolCapabilityItem {
  toolId: string
  name: string
  kind: string
  riskLevel: string
  enabled: boolean
  executable: boolean
  requiresConfirmation: boolean
  reason: string
  description: string
  configuredSecrets: string[]
}

export interface ToolCapabilityResponse {
  tools: ToolCapabilityItem[]
}

export interface AgentRunDetail {
  summary: AgentRunSummary
  steps: AgentRunStep[]
  checkpoints: AgentCheckpoint[]
  toolCalls: ToolCallDetail[]
}
