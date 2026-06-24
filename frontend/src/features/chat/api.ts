import { apiGet, apiPost, http } from '../../api/http'
import type {
  ConversationDetail,
  ConversationFeedback,
  ConversationMessage,
  ConversationPatchResponse,
  ConversationSummary,
  CreateConversationRequest,
  DeleteFeedbackResponse,
  FeedbackRequest,
  MemoryStrategy,
  PagedResult,
  RequestedExecutionMode,
  StreamMessageRequest,
  UpdateConversationRequest,
} from './types'

export const MULTI_KNOWLEDGE_BASE_UNSUPPORTED_MESSAGE =
  '当前后端暂不支持多知识库会话，请保留 1 个知识库或选择不限定后再发送。'

interface BuildStreamMessageRequestInput {
  message: string
  knowledgeBaseIds: number[]
  memoryStrategy?: MemoryStrategy
  executionMode?: RequestedExecutionMode
  multiKnowledgeBaseEnabled?: boolean
}

type BuildStreamMessageRequestResult =
  | {
      ok: true
      payload: StreamMessageRequest
    }
  | {
      ok: false
      error: string
    }

export function normalizeKnowledgeBaseIds(ids: number[]) {
  return Array.from(new Set(ids.filter((id) => Number.isFinite(id))))
}

export function resolveLegacyKnowledgeBaseId(ids: number[]) {
  const normalized = normalizeKnowledgeBaseIds(ids)
  return normalized.length === 1 ? normalized[0] : null
}

export function isChatMultiKnowledgeBaseStreamEnabled() {
  return import.meta.env.VITE_CHAT_MULTI_KB_STREAM_ENABLED === 'true'
}

export function buildStreamMessageRequest({
  message,
  knowledgeBaseIds,
  memoryStrategy,
  executionMode,
  multiKnowledgeBaseEnabled = isChatMultiKnowledgeBaseStreamEnabled(),
}: BuildStreamMessageRequestInput): BuildStreamMessageRequestResult {
  const normalizedIds = normalizeKnowledgeBaseIds(knowledgeBaseIds)

  if (normalizedIds.length > 1) {
    if (!multiKnowledgeBaseEnabled) {
      return { ok: false, error: MULTI_KNOWLEDGE_BASE_UNSUPPORTED_MESSAGE }
    }
    return {
      ok: true,
      payload: {
        message,
        knowledgeBaseIds: normalizedIds,
        memoryStrategy,
        executionMode,
      },
    }
  }

  return {
    ok: true,
    payload: {
      message,
      knowledgeBaseId: normalizedIds[0] ?? null,
      memoryStrategy,
      executionMode,
    },
  }
}

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

export function listConversations(params?: Record<string, string | number | undefined>) {
  return apiGet<PagedResult<ConversationSummary>>(`/conversations${buildQuery(params)}`)
}

export function createConversation(payload: CreateConversationRequest) {
  return apiPost<ConversationDetail, CreateConversationRequest>('/conversations', payload)
}

export function getConversation(sessionId: number) {
  return apiGet<ConversationDetail>(`/conversations/${sessionId}`)
}

export function updateConversation(sessionId: number, payload: UpdateConversationRequest) {
  return http.patch<{ success: boolean; code: string; message: string; data: ConversationPatchResponse; traceId: string }>(
    `/conversations/${sessionId}`,
    payload,
  )
}

export function deleteConversation(sessionId: number) {
  return http.delete<{ success: boolean; code: string; message: string; data: { deleted: boolean }; traceId: string }>(
    `/conversations/${sessionId}`,
  )
}

export function listMessages(sessionId: number) {
  return apiGet<PagedResult<ConversationMessage>>(`/conversations/${sessionId}/messages`)
}

export function listConversationFeedbacks(sessionId: number) {
  return apiGet<ConversationFeedback[]>(`/conversations/${sessionId}/feedbacks`)
}

export function upsertMessageFeedback(messageId: number, payload: FeedbackRequest) {
  return http.put<{ success: boolean; code: string; message: string; data: ConversationFeedback; traceId: string }>(
    `/messages/${messageId}/feedback`,
    payload,
  )
}

export function deleteMessageFeedback(messageId: number) {
  return http.delete<{ success: boolean; code: string; message: string; data: DeleteFeedbackResponse; traceId: string }>(
    `/messages/${messageId}/feedback`,
  )
}

export function stopConversation(sessionId: number) {
  return apiPost<{ stopped: boolean; sessionId: number }, undefined>(`/conversations/${sessionId}/stop`)
}

export function resumeConversation(sessionId: number) {
  return apiPost<{ resumed: boolean; sessionId: number; runId: number }, undefined>(`/conversations/${sessionId}/resume`)
}

export function openMessageStream(
  sessionId: number,
  payload: StreamMessageRequest,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal,
) {
  return http.request({
    method: 'POST',
    url: `/conversations/${sessionId}/messages/stream`,
    data: payload,
    responseType: 'text',
    signal,
    onDownloadProgress: (event) => {
      if (typeof event.event?.target?.responseText === 'string') {
        onChunk(event.event.target.responseText)
      }
    },
    headers: {
      Accept: 'text/event-stream',
    },
  })
}

export function defaultMemoryStrategy(): MemoryStrategy {
  return 'SLIDING_WINDOW'
}
