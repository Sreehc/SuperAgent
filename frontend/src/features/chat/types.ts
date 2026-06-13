export type MemoryStrategy = 'NONE' | 'SLIDING_WINDOW' | 'SUMMARY_WINDOW' | 'SUMMARY_PLUS_WINDOW'

export type RequestedExecutionMode = 'AUTO' | 'RAG_QA' | 'REACT_AGENT'

export type ConversationStatus = 'active' | 'archived' | 'deleted'

export type MessageRole = 'user' | 'assistant' | 'system'

export interface ConversationSummary {
  id: number
  title: string
  status: ConversationStatus
  lastMessageAt: string | null
}

export interface ConversationDetail {
  id: number
  title: string
  knowledgeBaseId: number | null
  memoryStrategy: MemoryStrategy
  status: ConversationStatus
  createdAt: string
  updatedAt: string
}

export interface ConversationMessage {
  id: number
  role: MessageRole
  content: string
  status: string
  createdAt: string
}

export type FeedbackRating = 'up' | 'down' | 'correction'

export interface ConversationFeedback {
  id: number
  sessionId: number
  exchangeId: number | null
  messageId: number
  actorUserId: number
  rating: FeedbackRating
  comment: string | null
  correction: string | null
  metadata: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface FeedbackRequest {
  rating: FeedbackRating
  comment?: string | null
  correction?: string | null
}

export interface DeleteFeedbackResponse {
  deleted: boolean
  messageId: number
}

export interface DisplayReference {
  ordinal: number
  documentId: number
  chunkId: number
  title: string
  quote: string
  score: number | null
}

export interface DisplayMessage extends ConversationMessage {
  references: DisplayReference[]
  feedback: ConversationFeedback | null
}

export interface RunTimelineItem {
  type: 'agent_step' | 'tool_start' | 'tool_result' | 'checkpoint' | 'resume'
  title: string
  summary: string
}

export interface StreamState {
  exchangeId: number | null
  runId: number | null
  stage: string | null
  recommendations: string[]
  error: string
  stopped: boolean
  timeline: RunTimelineItem[]
}

export interface PagedResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export interface CreateConversationRequest {
  title?: string
  knowledgeBaseId?: number | null
  memoryStrategy?: MemoryStrategy
}

export interface UpdateConversationRequest {
  title?: string
  knowledgeBaseId?: number | null
  memoryStrategy?: MemoryStrategy
  status?: ConversationStatus
}

export interface ConversationPatchResponse {
  id: number
  title: string
  status: ConversationStatus
}

export interface StreamMessageRequest {
  message: string
  knowledgeBaseId?: number | null
  memoryStrategy?: MemoryStrategy
  executionMode?: RequestedExecutionMode
}

export interface StreamStartEvent {
  exchangeId: number
  messageId: number
}

export interface StreamTraceStageEvent {
  stage: string
  status: string
  durationMs: number
}

export interface StreamDeltaEvent {
  text: string
}

export interface StreamReferenceEvent {
  ordinal: number
  documentId: number
  chunkId: number
  title: string
  quote: string
  score: number | null
}

export interface StreamRecommendationEvent {
  questions: string[]
}

export interface StreamAgentStepEvent {
  runId: number
  stepNo: number
  phase: string
  status: string
  summary: string
}

export interface StreamToolStartEvent {
  runId: number
  toolId: string
  stepNo: number
  summary: string
}

export interface StreamToolResultEvent {
  runId: number
  toolId: string
  status: string
  summary: string
  output?: Record<string, unknown>
}

export interface StreamCheckpointEvent {
  runId: number
  checkpointNo: number
  phase: string
  stable: boolean
}

export interface StreamResumeEvent {
  runId: number
  status: string
}

export interface StreamDoneEvent {
  exchangeId?: number
  assistantMessageId?: number
  runId?: number
  status?: string
  stopped: boolean
}

export interface StreamErrorEvent {
  code: string
  message: string
  exchangeId?: number | null
  runId?: number | null
}
