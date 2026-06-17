import { create } from 'zustand'
import {
  createConversation,
  deleteConversation,
  deleteMessageFeedback,
  getConversation,
  listConversationFeedbacks,
  listConversations,
  listMessages,
  openMessageStream,
  resumeConversation,
  stopConversation,
  updateConversation,
  upsertMessageFeedback,
  defaultMemoryStrategy,
} from '../api'
import type {
  ConversationDetail,
  ConversationSummary,
  DisplayMessage,
  DisplayReference,
  FeedbackRating,
  MemoryStrategy,
  RequestedExecutionMode,
  StreamState,
} from '../types'
import { applyStreamEvent, consumeSseText, createEmptyStreamState, type SseParserState } from './streamRuntime'
import { listKnowledgeBases } from '../../knowledge/api'
import { listToolCapabilities } from '../../agent/api'
import type { ToolCapabilityItem } from '../../agent/types'

interface KnowledgeOption {
  id: number
  name: string
}

interface ChatState {
  conversations: ConversationSummary[]
  selectedSessionId: number | null
  selectedConversation: ConversationDetail | null
  messages: DisplayMessage[]
  loadingConversations: boolean
  loadingMessages: boolean
  streaming: boolean
  streamState: StreamState
  selectedReference: DisplayReference | null
  keyword: string
  updatingFeedbackMessageId: number | null
  // composer
  message: string
  memoryStrategy: MemoryStrategy
  executionMode: RequestedExecutionMode
  availableKnowledgeBases: KnowledgeOption[]
  selectedKnowledgeBaseId: number | null
  toolCapabilities: ToolCapabilityItem[]
}

interface ChatActions {
  bootstrap: (sessionId?: number | null) => Promise<void>
  fetchConversations: () => Promise<void>
  createAndSelectConversation: (title?: string) => Promise<number | null>
  selectConversation: (sessionId: number) => Promise<void>
  sendMessage: (text?: string) => Promise<void>
  stopStreaming: () => Promise<void>
  resumeConversationRun: () => Promise<void>
  renameConversation: (sessionId: number, title: string) => Promise<void>
  archiveConversation: (sessionId: number) => Promise<void>
  removeConversation: (sessionId: number) => Promise<void>
  setMessageFeedback: (message: DisplayMessage, rating: FeedbackRating) => Promise<void>
  correctMessageFeedback: (message: DisplayMessage) => Promise<void>
  clearMessageFeedback: (message: DisplayMessage) => Promise<void>
  setKeyword: (value: string) => void
  setMessage: (value: string) => void
  setSelectedKnowledgeBaseId: (value: number | null) => void
  setExecutionMode: (value: RequestedExecutionMode) => void
  useRecommendation: (question: string) => void
  clearOnRouteLeave: () => void
}

export type ChatStore = ChatState & ChatActions

// Non-reactive stream plumbing.
let abortController: AbortController | null = null
let stopRequested = false
const parser: SseParserState = { offset: 0, buffer: '' }
function resetParser() {
  parser.offset = 0
  parser.buffer = ''
}

export const useChatStore = create<ChatStore>((set, get) => ({
  conversations: [],
  selectedSessionId: null,
  selectedConversation: null,
  messages: [],
  loadingConversations: false,
  loadingMessages: false,
  streaming: false,
  streamState: createEmptyStreamState(),
  selectedReference: null,
  keyword: '',
  updatingFeedbackMessageId: null,
  message: '',
  memoryStrategy: defaultMemoryStrategy(),
  executionMode: 'AUTO',
  availableKnowledgeBases: [],
  selectedKnowledgeBaseId: null,
  toolCapabilities: [],

  async bootstrap(sessionId) {
    await Promise.all([fetchKnowledgeBaseOptions(set), fetchToolCapabilities(set)])
    await get().fetchConversations()
    const conversations = get().conversations
    const preferred = sessionId ?? get().selectedSessionId
    if (preferred && conversations.some((c) => c.id === preferred)) {
      await get().selectConversation(preferred)
      return
    }
    if (get().selectedSessionId) {
      await get().selectConversation(get().selectedSessionId as number)
      return
    }
    if (conversations.length > 0) {
      await get().selectConversation(conversations[0].id)
    }
  },

  async fetchConversations() {
    set({ loadingConversations: true })
    try {
      const response = await listConversations()
      set({ conversations: response.data.items })
    } finally {
      set({ loadingConversations: false })
    }
  },

  async createAndSelectConversation(title) {
    const response = await createConversation({
      title,
      knowledgeBaseId: get().selectedKnowledgeBaseId,
      memoryStrategy: get().memoryStrategy,
    })
    await get().fetchConversations()
    await get().selectConversation(response.data.id)
    return response.data.id
  },

  async selectConversation(sessionId) {
    if (get().streaming && get().selectedSessionId !== sessionId) {
      await get().stopStreaming()
    }
    set({ selectedSessionId: sessionId, loadingMessages: true })
    try {
      const [conversationResponse, messagesResponse] = await Promise.all([
        getConversation(sessionId),
        listMessages(sessionId),
      ])
      const feedbacksResponse = await listConversationFeedbacks(sessionId)
      const feedbackByMessageId = new Map(feedbacksResponse.data.map((feedback) => [feedback.messageId, feedback]))
      set({
        selectedConversation: conversationResponse.data,
        memoryStrategy: conversationResponse.data.memoryStrategy,
        selectedKnowledgeBaseId: conversationResponse.data.knowledgeBaseId,
        messages: messagesResponse.data.items.map((message) => ({
          ...message,
          references: [],
          feedback: feedbackByMessageId.get(message.id) ?? null,
        })),
        selectedReference: null,
        streamState: createEmptyStreamState(),
      })
    } finally {
      set({ loadingMessages: false })
    }
  },

  async sendMessage(text) {
    const body = (text ?? get().message).trim()
    if (!body) return

    if (!get().selectedSessionId) {
      await get().createAndSelectConversation(body.slice(0, 24))
    }
    const sessionId = get().selectedSessionId
    if (!sessionId) return

    resetParser()
    stopRequested = false
    const draft: DisplayMessage[] = [
      ...get().messages,
      {
        id: -Math.round(Math.random() * 1_000_000),
        role: 'user',
        content: body,
        status: 'success',
        createdAt: new Date().toISOString(),
        references: [],
        feedback: null,
      },
    ]
    const workingState = createEmptyStreamState()
    set({ messages: draft, message: '', streaming: true, streamState: workingState, selectedReference: null })

    function ensureAssistantMessage(): DisplayMessage {
      const last = draft[draft.length - 1]
      if (last && last.role === 'assistant' && last.status === 'streaming') return last
      const assistant: DisplayMessage = {
        id: -Date.now(),
        role: 'assistant',
        content: '',
        status: 'streaming',
        createdAt: new Date().toISOString(),
        references: [],
        feedback: null,
      }
      draft.push(assistant)
      return assistant
    }

    const controller = new AbortController()
    abortController = controller

    try {
      await openMessageStream(
        sessionId,
        {
          message: body,
          knowledgeBaseId: get().selectedKnowledgeBaseId,
          memoryStrategy: get().memoryStrategy,
          executionMode: get().executionMode,
        },
        (raw) => {
          consumeSseText(raw, parser, (eventName, data) => {
            applyStreamEvent(workingState, eventName, data, {
              ensureAssistantMessage,
              attachReference: (reference) => {
                const assistant = ensureAssistantMessage()
                assistant.references = [...assistant.references, reference]
                set({ selectedReference: reference })
              },
              findLatestAssistantMessage: () => [...draft].reverse().find((m) => m.role === 'assistant'),
            })
          })
          set({ messages: [...draft], streamState: { ...workingState } })
        },
        controller.signal,
      )
      await get().fetchConversations()
      const conversationResponse = await getConversation(sessionId)
      set({
        selectedConversation: conversationResponse.data,
        memoryStrategy: conversationResponse.data.memoryStrategy,
        selectedKnowledgeBaseId: conversationResponse.data.knowledgeBaseId,
      })
    } catch (error) {
      const stopped = stopRequested || workingState.stopped
      const assistant = [...draft].reverse().find((m) => m.role === 'assistant')
      if (assistant) assistant.status = stopped ? 'stopped' : 'error'
      if (!stopped && !workingState.error) {
        workingState.error = '消息发送失败，请稍后重试。'
      }
      set({ messages: [...draft], streamState: { ...workingState } })
      if (!stopped) throw error
    } finally {
      set({ streaming: false })
      abortController = null
    }
  },

  async stopStreaming() {
    const sessionId = get().selectedSessionId
    if (!sessionId || !get().streaming) return
    stopRequested = true
    set({ streamState: { ...get().streamState, stopped: true } })
    abortController?.abort()
    try {
      await stopConversation(sessionId)
    } catch {
      const current = get().streamState
      if (!current.error) set({ streamState: { ...current, error: '停止生成失败，请稍后重试。' } })
    }
  },

  async resumeConversationRun() {
    const sessionId = get().selectedSessionId
    if (!sessionId) return
    const response = await resumeConversation(sessionId)
    const current = get().streamState
    set({
      streamState: {
        ...current,
        runId: response.data.runId,
        timeline: [
          ...current.timeline,
          { type: 'resume', title: '恢复请求', summary: response.data.resumed ? '已提交恢复请求' : '恢复请求未接受' },
        ],
      },
    })
  },

  async renameConversation(sessionId, title) {
    const value = title.trim()
    if (!value) return
    await updateConversation(sessionId, { title: value })
    await get().fetchConversations()
    if (get().selectedSessionId === sessionId) await get().selectConversation(sessionId)
  },

  async archiveConversation(sessionId) {
    await updateConversation(sessionId, { status: 'archived' })
    await get().fetchConversations()
    if (get().selectedSessionId === sessionId && get().conversations.length > 0) {
      await get().selectConversation(get().conversations[0].id)
    }
  },

  async removeConversation(sessionId) {
    await deleteConversation(sessionId)
    if (get().selectedSessionId === sessionId) {
      get().clearOnRouteLeave()
      set({ selectedSessionId: null, selectedConversation: null, messages: [] })
    }
    await get().fetchConversations()
    if (!get().selectedSessionId && get().conversations.length > 0) {
      await get().selectConversation(get().conversations[0].id)
    }
  },

  async setMessageFeedback(message, rating) {
    if (message.role !== 'assistant' || message.id <= 0) return
    if (message.feedback?.rating === rating) {
      await get().clearMessageFeedback(message)
      return
    }
    set({ updatingFeedbackMessageId: message.id })
    try {
      const response = await upsertMessageFeedback(message.id, { rating })
      patchFeedback(set, message.id, response.data.data)
    } finally {
      set({ updatingFeedbackMessageId: null })
    }
  },

  async correctMessageFeedback(message) {
    if (message.role !== 'assistant' || message.id <= 0) return
    const correction = window.prompt('请输入更正建议，将用于后续评测和质量改进。', message.feedback?.correction ?? '')
    if (correction === null) return
    const trimmed = correction.trim()
    if (!trimmed) return
    set({ updatingFeedbackMessageId: message.id })
    try {
      const response = await upsertMessageFeedback(message.id, { rating: 'correction', correction: trimmed })
      patchFeedback(set, message.id, response.data.data)
    } finally {
      set({ updatingFeedbackMessageId: null })
    }
  },

  async clearMessageFeedback(message) {
    if (message.role !== 'assistant' || message.id <= 0) return
    set({ updatingFeedbackMessageId: message.id })
    try {
      await deleteMessageFeedback(message.id)
      patchFeedback(set, message.id, null)
    } finally {
      set({ updatingFeedbackMessageId: null })
    }
  },

  setKeyword: (value) => set({ keyword: value }),
  setMessage: (value) => set({ message: value }),
  setSelectedKnowledgeBaseId: (value) => set({ selectedKnowledgeBaseId: value }),
  setExecutionMode: (value) => set({ executionMode: value }),
  useRecommendation: (question) => set({ message: question }),

  clearOnRouteLeave() {
    abortController?.abort()
    abortController = null
    resetParser()
    set({ streaming: false })
  },
}))

type SetState = (partial: Partial<ChatStore>) => void

function patchFeedback(set: SetState, messageId: number, feedback: DisplayMessage['feedback']) {
  set({
    messages: useChatStore
      .getState()
      .messages.map((message) => (message.id === messageId ? { ...message, feedback } : message)),
  })
}

async function fetchKnowledgeBaseOptions(set: SetState) {
  try {
    const response = await listKnowledgeBases({ pageSize: 100, status: 'published' })
    set({ availableKnowledgeBases: response.data.items.map((item) => ({ id: item.id, name: item.name })) })
  } catch {
    set({ availableKnowledgeBases: [] })
  }
}

async function fetchToolCapabilities(set: SetState) {
  try {
    const response = await listToolCapabilities()
    set({ toolCapabilities: response.data.tools })
  } catch {
    set({ toolCapabilities: [] })
  }
}

// Derived selectors
export const selectFilteredConversations = (s: ChatStore) => {
  const search = s.keyword.trim().toLowerCase()
  if (!search) return s.conversations
  return s.conversations.filter((c) => c.title.toLowerCase().includes(search))
}
