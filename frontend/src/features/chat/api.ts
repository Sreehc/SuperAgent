import { apiGet, apiPost, http } from '../../api/http'
import type { ConversationDetail, ConversationMessage, ConversationSummary, CreateConversationRequest, MemoryStrategy, PagedResult, StreamMessageRequest } from './types'

export function listConversations() {
  return apiGet<PagedResult<ConversationSummary>>('/conversations')
}

export function createConversation(payload: CreateConversationRequest) {
  return apiPost<ConversationDetail, CreateConversationRequest>('/conversations', payload)
}

export function getConversation(sessionId: number) {
  return apiGet<ConversationDetail>(`/conversations/${sessionId}`)
}

export function listMessages(sessionId: number) {
  return apiGet<PagedResult<ConversationMessage>>(`/conversations/${sessionId}/messages`)
}

export function stopConversation(sessionId: number) {
  return apiPost<{ stopped: boolean; sessionId: number }, undefined>(`/conversations/${sessionId}/stop`)
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
