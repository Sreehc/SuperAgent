export interface ModelSettings {
  provider: string
  baseUrl: string
  chatModel: string
  embeddingModel: string
  apiKeySet: boolean
}

export interface RagSettings {
  rewriteEnabled: boolean
  subQuestionEnabled: boolean
  maxSubQuestions: number
  vectorTopK: number
  keywordTopK: number
  rrfK: number
  rerankEnabled: boolean
  evidenceLimit: number
  perQuestionEvidenceCharLimit: number
  totalEvidenceCharLimit: number
  minRelevanceScore: number
}

export interface RerankSettings {
  enabled: boolean
  provider: string
  baseUrl: string | null
  model: string | null
  apiKeySet: boolean
}

export interface AgentSettings {
  enabled: boolean
  maxModelSteps: number
  maxToolCalls: number
  checkpointEnabled: boolean
  defaultMemoryStrategy: string
  webSearchEnabled: boolean
  httpToolEnabled: boolean
  graphToolEnabled: boolean
  codeExecutionEnabled: boolean
  toolTimeoutMs: number
  allowedHttpDomains: string[]
}

export interface ToolSettings {
  webSearchEnabled: boolean
  httpToolEnabled: boolean
  graphToolEnabled: boolean
  codeExecutionEnabled: boolean
  toolTimeoutMs: number
  searchProvider: string
  allowedHttpDomains: string[]
}

export interface UpdateResponse {
  updated: boolean
}

export interface SecretUpdateResponse extends UpdateResponse {
  apiKeySet: boolean
}

export interface ValidationFieldError {
  field?: string
  parameter?: string
  message: string
}

export interface UpdateModelSettingsRequest {
  baseUrl?: string
  chatModel?: string
  embeddingModel?: string
  apiKey?: string
}

export interface UpdateRagSettingsRequest {
  rewriteEnabled?: boolean
  subQuestionEnabled?: boolean
  maxSubQuestions?: number
  vectorTopK?: number
  keywordTopK?: number
  rrfK?: number
  rerankEnabled?: boolean
  evidenceLimit?: number
  perQuestionEvidenceCharLimit?: number
  totalEvidenceCharLimit?: number
  minRelevanceScore?: number
}

export interface UpdateRerankSettingsRequest {
  enabled?: boolean
  provider?: string
  baseUrl?: string
  model?: string
  apiKey?: string
}

export interface UpdateAgentSettingsRequest {
  enabled?: boolean
  maxModelSteps?: number
  maxToolCalls?: number
  checkpointEnabled?: boolean
  defaultMemoryStrategy?: string
  webSearchEnabled?: boolean
  httpToolEnabled?: boolean
  graphToolEnabled?: boolean
  codeExecutionEnabled?: boolean
  toolTimeoutMs?: number
  allowedHttpDomains?: string[]
}

export interface UpdateToolSettingsRequest {
  webSearchEnabled?: boolean
  httpToolEnabled?: boolean
  graphToolEnabled?: boolean
  codeExecutionEnabled?: boolean
  toolTimeoutMs?: number
  searchProvider?: string
  allowedHttpDomains?: string[]
}
