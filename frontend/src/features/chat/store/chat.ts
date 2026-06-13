import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  createConversation,
  deleteMessageFeedback,
  deleteConversation,
  getConversation,
  listConversationFeedbacks,
  listConversations,
  listMessages,
  openMessageStream,
  resumeConversation,
  stopConversation,
  upsertMessageFeedback,
  updateConversation,
} from '../api'
import type {
  ConversationDetail,
  ConversationSummary,
  DisplayMessage,
  DisplayReference,
  FeedbackRating,
  StreamState,
} from '../types'
import { useChatComposerStore } from './composer'
import { applyStreamEvent, consumeSseText, createEmptyStreamState, type SseParserState } from './streamRuntime'

export const useChatStore = defineStore('chat', () => {
  const composerStore = useChatComposerStore()
  const conversations = ref<ConversationSummary[]>([])
  const selectedSessionId = ref<number | null>(null)
  const selectedConversation = ref<ConversationDetail | null>(null)
  const messages = ref<DisplayMessage[]>([])
  const loadingConversations = ref(false)
  const loadingMessages = ref(false)
  const creatingConversation = ref(false)
  const streaming = ref(false)
  const streamState = ref<StreamState>(createEmptyStreamState())
  const activeAbortController = ref<AbortController | null>(null)
  const parserState: SseParserState = { offset: 0, buffer: '' }
  const selectedReference = ref<DisplayReference | null>(null)
  const keyword = ref('')
  const editingConversationId = ref<number | null>(null)
  const updatingConversation = ref(false)
  const deletingConversation = ref(false)
  const updatingFeedbackMessageId = ref<number | null>(null)

  const hasMessages = computed(() => messages.value.length > 0)
  const filteredConversations = computed(() => {
    const search = keyword.value.trim().toLowerCase()
    if (!search) {
      return conversations.value
    }
    return conversations.value.filter((conversation) => conversation.title.toLowerCase().includes(search))
  })

  async function bootstrap(sessionId?: number | null) {
    await Promise.all([
      composerStore.fetchKnowledgeBaseOptions(),
      composerStore.fetchToolCapabilities(),
    ])
    await fetchConversations()
    const preferredSessionId = sessionId ?? selectedSessionId.value
    if (preferredSessionId) {
      const matchedConversation = conversations.value.find((conversation) => conversation.id === preferredSessionId)
      if (matchedConversation) {
        await selectConversation(preferredSessionId)
        return
      }
    }
    if (selectedSessionId.value) {
      await selectConversation(selectedSessionId.value)
      return
    }
    if (conversations.value.length > 0) {
      await selectConversation(conversations.value[0].id)
    }
  }

  async function fetchConversations() {
    loadingConversations.value = true
    try {
      const response = await listConversations()
      conversations.value = response.data.items
    } finally {
      loadingConversations.value = false
    }
  }

  async function createAndSelectConversation(title?: string) {
    creatingConversation.value = true
    try {
      const response = await createConversation({
        title,
        knowledgeBaseId: composerStore.selectedKnowledgeBaseId,
        memoryStrategy: composerStore.memoryStrategy,
      })
      await fetchConversations()
      await selectConversation(response.data.id)
    } finally {
      creatingConversation.value = false
    }
  }

  async function selectConversation(sessionId: number) {
    if (streaming.value && selectedSessionId.value !== sessionId) {
      await stopStreaming()
    }
    selectedSessionId.value = sessionId
    loadingMessages.value = true
    try {
      const [conversationResponse, messagesResponse] = await Promise.all([
        getConversation(sessionId),
        listMessages(sessionId),
      ])
      const feedbacksResponse = await listConversationFeedbacks(sessionId)
      const feedbackByMessageId = new Map(feedbacksResponse.data.map((feedback) => [feedback.messageId, feedback]))
      selectedConversation.value = conversationResponse.data
      composerStore.applyConversationConfig(conversationResponse.data)
      messages.value = messagesResponse.data.items.map((message) => ({
        ...message,
        references: [],
        feedback: feedbackByMessageId.get(message.id) ?? null,
      }))
      selectedReference.value = null
      streamState.value = createEmptyStreamState()
    } finally {
      loadingMessages.value = false
    }
  }

  function attachReference(reference: DisplayReference) {
    const assistantMessage = ensureAssistantMessage()
    assistantMessage.references = [...assistantMessage.references, reference]
    selectedReference.value = reference
  }

  function ensureAssistantMessage() {
    const current = messages.value[messages.value.length - 1]
    if (current && current.role === 'assistant' && current.status === 'streaming') {
      return current
    }
    const assistantMessage: DisplayMessage = {
      id: -Date.now(),
      role: 'assistant',
      content: '',
      status: 'streaming',
      createdAt: new Date().toISOString(),
      references: [],
      feedback: null,
    }
    messages.value = [...messages.value, assistantMessage]
    return assistantMessage
  }

  function consumeSseChunk(rawText: string) {
    consumeSseText(rawText, parserState, (eventName, data) => {
      applyStreamEvent(streamState.value, eventName, data, {
        ensureAssistantMessage,
        attachReference,
        findLatestAssistantMessage,
      })
    })
  }

  async function sendMessage() {
    const text = composerStore.message.trim()
    if (!text) {
      return
    }

    if (!selectedSessionId.value) {
      await createAndSelectConversation(text.slice(0, 24))
    }

    if (!selectedSessionId.value) {
      return
    }

    streamState.value = createEmptyStreamState()
    resetParser()
    selectedReference.value = null
    streaming.value = true

    const userMessage: DisplayMessage = {
      id: -Math.round(Math.random() * 100000),
      role: 'user',
      content: text,
      status: 'success',
      createdAt: new Date().toISOString(),
      references: [],
      feedback: null,
    }
    messages.value = [...messages.value, userMessage]
    composerStore.clearMessage()
    const controller = new AbortController()
    activeAbortController.value = controller

    try {
      await openMessageStream(
        selectedSessionId.value,
        {
          message: text,
          knowledgeBaseId: composerStore.selectedKnowledgeBaseId,
          memoryStrategy: composerStore.memoryStrategy,
          executionMode: composerStore.executionMode,
        },
        consumeSseChunk,
        controller.signal,
      )
      await fetchConversations()
      const conversationResponse = await getConversation(selectedSessionId.value)
      selectedConversation.value = conversationResponse.data
      composerStore.applyConversationConfig(conversationResponse.data)
    } catch (error) {
      const assistantMessage = [...messages.value].reverse().find((message) => message.role === 'assistant')
      if (assistantMessage) {
        assistantMessage.status = streamState.value.stopped ? 'stopped' : 'error'
      }
      if (streamState.value.stopped) {
        return
      }
      if (!streamState.value.error) {
        streamState.value.error = '消息发送失败，请稍后重试。'
      }
      throw error
    } finally {
      streaming.value = false
      activeAbortController.value = null
    }
  }

  async function stopStreaming() {
    if (!selectedSessionId.value || !streaming.value) {
      return
    }
    streamState.value.stopped = true
    activeAbortController.value?.abort()
    try {
      await stopConversation(selectedSessionId.value)
    } catch {
      if (!streamState.value.error) {
        streamState.value.error = '停止生成失败，请稍后重试。'
      }
    }
  }

  async function resumeConversationRun() {
    if (!selectedSessionId.value) {
      return
    }
    const response = await resumeConversation(selectedSessionId.value)
    streamState.value.runId = response.data.runId
    streamState.value.timeline = [
      ...streamState.value.timeline,
      { type: 'resume', title: '恢复请求', summary: response.data.resumed ? '已提交恢复请求' : '恢复请求未接受' },
    ]
  }

  function clearOnRouteLeave() {
    activeAbortController.value?.abort()
    activeAbortController.value = null
    streaming.value = false
    resetParser()
  }

  function resetParser() {
    parserState.offset = 0
    parserState.buffer = ''
  }

  function findLatestAssistantMessage() {
    return [...messages.value].reverse().find((message) => message.role === 'assistant')
  }

  async function renameConversation(sessionId: number, title: string) {
    const value = title.trim()
    if (!value) {
      return
    }
    updatingConversation.value = true
    try {
      await updateConversation(sessionId, { title: value })
      await fetchConversations()
      if (selectedSessionId.value === sessionId) {
        await selectConversation(sessionId)
      }
    } finally {
      updatingConversation.value = false
      editingConversationId.value = null
    }
  }

  async function archiveConversation(sessionId: number) {
    updatingConversation.value = true
    try {
      await updateConversation(sessionId, { status: 'archived' })
      await fetchConversations()
      if (selectedSessionId.value === sessionId && conversations.value.length > 0) {
        await selectConversation(conversations.value[0].id)
      }
    } finally {
      updatingConversation.value = false
    }
  }

  async function removeConversation(sessionId: number) {
    deletingConversation.value = true
    try {
      await deleteConversation(sessionId)
      if (selectedSessionId.value === sessionId) {
        clearOnRouteLeave()
        selectedSessionId.value = null
        selectedConversation.value = null
        messages.value = []
      }
      await fetchConversations()
      if (!selectedSessionId.value && conversations.value.length > 0) {
        await selectConversation(conversations.value[0].id)
      }
    } finally {
      deletingConversation.value = false
    }
  }

  async function setMessageFeedback(message: DisplayMessage, rating: FeedbackRating) {
    if (message.role !== 'assistant' || message.id <= 0) {
      return
    }

    if (message.feedback?.rating === rating) {
      await clearMessageFeedback(message)
      return
    }

    updatingFeedbackMessageId.value = message.id
    try {
      const response = await upsertMessageFeedback(message.id, { rating })
      patchMessageFeedback(message.id, response.data.data)
    } finally {
      updatingFeedbackMessageId.value = null
    }
  }

  async function correctMessageFeedback(message: DisplayMessage) {
    if (message.role !== 'assistant' || message.id <= 0) {
      return
    }

    const correction = window.prompt('请输入更正建议，将用于后续评测和质量改进。', message.feedback?.correction ?? '')
    if (correction === null) {
      return
    }

    const trimmedCorrection = correction.trim()
    if (!trimmedCorrection) {
      return
    }

    updatingFeedbackMessageId.value = message.id
    try {
      const response = await upsertMessageFeedback(message.id, {
        rating: 'correction',
        correction: trimmedCorrection,
      })
      patchMessageFeedback(message.id, response.data.data)
    } finally {
      updatingFeedbackMessageId.value = null
    }
  }

  async function clearMessageFeedback(message: DisplayMessage) {
    if (message.role !== 'assistant' || message.id <= 0) {
      return
    }

    updatingFeedbackMessageId.value = message.id
    try {
      await deleteMessageFeedback(message.id)
      patchMessageFeedback(message.id, null)
    } finally {
      updatingFeedbackMessageId.value = null
    }
  }

  function patchMessageFeedback(messageId: number, feedback: DisplayMessage['feedback']) {
    messages.value = messages.value.map((message) => (
      message.id === messageId
        ? { ...message, feedback }
        : message
    ))
  }

  return {
    conversations,
    filteredConversations,
    selectedSessionId,
    selectedConversation,
    messages,
    loadingConversations,
    loadingMessages,
    creatingConversation,
    streaming,
    streamState,
    selectedReference,
    keyword,
    editingConversationId,
    updatingConversation,
    deletingConversation,
    updatingFeedbackMessageId,
    hasMessages,
    bootstrap,
    fetchConversations,
    createAndSelectConversation,
    selectConversation,
    sendMessage,
    stopStreaming,
    resumeConversationRun,
    clearOnRouteLeave,
    renameConversation,
    archiveConversation,
    removeConversation,
    setMessageFeedback,
    correctMessageFeedback,
    clearMessageFeedback,
  }
})
