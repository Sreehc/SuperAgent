export type MemoryStrategy = 'NONE' | 'SLIDING_WINDOW'

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

export interface StreamDoneEvent {
  exchangeId: number
  assistantMessageId: number
  stopped: boolean
}

export interface StreamErrorEvent {
  code: string
  message: string
  exchangeId: number | null
}
