import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { createConversation, defaultMemoryStrategy, getConversation, listConversations, listMessages, openMessageStream, stopConversation } from '../api'
import type { ConversationDetail, ConversationMessage, ConversationSummary, MemoryStrategy, StreamDeltaEvent, StreamDoneEvent, StreamErrorEvent, StreamRecommendationEvent, StreamReferenceEvent, StreamStartEvent, StreamTraceStageEvent } from '../types'
import { listKnowledgeBases } from '../../knowledge/api'

interface DisplayReference {
  ordinal: number
  documentId: number
  chunkId: number
  title: string
  quote: string
  score: number | null
}

interface DisplayMessage extends ConversationMessage {
  references: DisplayReference[]
}

interface StreamState {
  exchangeId: number | null
  stage: string | null
  recommendations: string[]
  error: string
  stopped: boolean
}

function createEmptyStreamState(): StreamState {
  return {
    exchangeId: null,
    stage: null,
    recommendations: [],
    error: '',
    stopped: false,
  }
}

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<ConversationSummary[]>([])
  const selectedSessionId = ref<number | null>(null)
  const selectedConversation = ref<ConversationDetail | null>(null)
  const messages = ref<DisplayMessage[]>([])
  const loadingConversations = ref(false)
  const loadingMessages = ref(false)
  const creatingConversation = ref(false)
  const streaming = ref(false)
  const composerMessage = ref('')
  const memoryStrategy = ref<MemoryStrategy>(defaultMemoryStrategy())
  const streamState = ref<StreamState>(createEmptyStreamState())
  const activeAbortController = ref<AbortController | null>(null)
  const streamedResponse = ref('')
  const parserOffset = ref(0)
  const parserBuffer = ref('')
  const selectedReference = ref<DisplayReference | null>(null)
  const availableKnowledgeBases = ref<Array<{ id: number; name: string }>>([])
  const selectedKnowledgeBaseId = ref<number | null>(null)

  const hasMessages = computed(() => messages.value.length > 0)

  async function bootstrap(sessionId?: number | null) {
    await fetchKnowledgeBaseOptions()
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

  async function fetchKnowledgeBaseOptions() {
    try {
      const response = await listKnowledgeBases({ pageSize: 100, status: 'published' })
      availableKnowledgeBases.value = response.data.items.map((item) => ({
        id: item.id,
        name: item.name,
      }))
    } catch {
      availableKnowledgeBases.value = []
    }
  }

  async function createAndSelectConversation(title?: string) {
    creatingConversation.value = true
    try {
      const response = await createConversation({
        title,
        knowledgeBaseId: selectedKnowledgeBaseId.value,
        memoryStrategy: memoryStrategy.value,
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
      selectedConversation.value = conversationResponse.data
      memoryStrategy.value = conversationResponse.data.memoryStrategy
      selectedKnowledgeBaseId.value = conversationResponse.data.knowledgeBaseId
      messages.value = messagesResponse.data.items.map((message) => ({
        ...message,
        references: [],
      }))
      selectedReference.value = null
      streamState.value = createEmptyStreamState()
    } finally {
      loadingMessages.value = false
    }
  }

  function attachReference(reference: DisplayReference) {
    const assistantMessage = [...messages.value].reverse().find((message) => message.role === 'assistant')
    if (!assistantMessage) {
      return
    }
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
    }
    messages.value = [...messages.value, assistantMessage]
    return assistantMessage
  }

  function applyEventLine(eventName: string | null, dataLine: string) {
    if (!eventName) {
      return
    }

    const parsed = JSON.parse(dataLine)
    switch (eventName) {
      case 'start': {
        const event = parsed as StreamStartEvent
        streamState.value.exchangeId = event.exchangeId
        break
      }
      case 'trace_stage': {
        const event = parsed as StreamTraceStageEvent
        streamState.value.stage = `${event.stage} · ${event.status}`
        break
      }
      case 'delta': {
        const event = parsed as StreamDeltaEvent
        const assistantMessage = ensureAssistantMessage()
        assistantMessage.content += event.text
        break
      }
      case 'reference': {
        const event = parsed as StreamReferenceEvent
        attachReference({
          ordinal: event.ordinal,
          documentId: event.documentId,
          chunkId: event.chunkId,
          title: event.title,
          quote: event.quote,
          score: event.score,
        })
        break
      }
      case 'recommendation': {
        const event = parsed as StreamRecommendationEvent
        streamState.value.recommendations = event.questions
        break
      }
      case 'done': {
        const event = parsed as StreamDoneEvent
        const assistantMessage = [...messages.value].reverse().find((message) => message.role === 'assistant')
        if (assistantMessage) {
          assistantMessage.status = event.stopped ? 'stopped' : 'success'
        }
        streamState.value.stopped = event.stopped
        break
      }
      case 'error': {
        const event = parsed as StreamErrorEvent
        streamState.value.error = event.message
        const assistantMessage = [...messages.value].reverse().find((message) => message.role === 'assistant')
        if (assistantMessage) {
          assistantMessage.status = 'error'
        }
        break
      }
      default:
        break
    }
  }

  function consumeSseChunk(rawText: string) {
    const incremental = rawText.slice(parserOffset.value)
    parserOffset.value = rawText.length
    streamedResponse.value = rawText

    parserBuffer.value += incremental
    const blocks = parserBuffer.value.split('\n\n')
    parserBuffer.value = blocks.pop() ?? ''
    for (const block of blocks) {
      const lines = block.split('\n')
      let eventName: string | null = null
      let dataValue = ''
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventName = line.replace('event:', '').trim()
        }
        if (line.startsWith('data:')) {
          dataValue = line.replace('data:', '').trim()
        }
      }
      if (eventName && dataValue) {
        applyEventLine(eventName, dataValue)
      }
    }
  }

  async function sendMessage() {
    const text = composerMessage.value.trim()
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
    parserOffset.value = 0
    streamedResponse.value = ''
    parserBuffer.value = ''
    selectedReference.value = null
    streaming.value = true

    const userMessage: DisplayMessage = {
      id: -Math.round(Math.random() * 100000),
      role: 'user',
      content: text,
      status: 'success',
      createdAt: new Date().toISOString(),
      references: [],
    }
    messages.value = [...messages.value, userMessage]
    composerMessage.value = ''

    const controller = new AbortController()
    activeAbortController.value = controller

    try {
      await openMessageStream(
        selectedSessionId.value,
        {
          message: text,
          knowledgeBaseId: selectedKnowledgeBaseId.value,
          memoryStrategy: memoryStrategy.value,
        },
        consumeSseChunk,
        controller.signal,
      )
      await fetchConversations()
      const conversationResponse = await getConversation(selectedSessionId.value)
      selectedConversation.value = conversationResponse.data
      memoryStrategy.value = conversationResponse.data.memoryStrategy
      selectedKnowledgeBaseId.value = conversationResponse.data.knowledgeBaseId
    } catch (error) {
      const assistantMessage = [...messages.value].reverse().find((message) => message.role === 'assistant')
      if (assistantMessage) {
        assistantMessage.status = streamState.value.stopped ? 'stopped' : 'error'
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

  function clearOnRouteLeave() {
    activeAbortController.value?.abort()
    activeAbortController.value = null
    streaming.value = false
    parserOffset.value = 0
    parserBuffer.value = ''
    streamedResponse.value = ''
  }

  function setSelectedKnowledgeBaseId(value: number | null) {
    selectedKnowledgeBaseId.value = value
  }

  return {
    conversations,
    selectedSessionId,
    selectedConversation,
    messages,
    loadingConversations,
    loadingMessages,
    creatingConversation,
    streaming,
    composerMessage,
    memoryStrategy,
    streamState,
    selectedReference,
    availableKnowledgeBases,
    selectedKnowledgeBaseId,
    hasMessages,
    bootstrap,
    fetchConversations,
    fetchKnowledgeBaseOptions,
    createAndSelectConversation,
    selectConversation,
    sendMessage,
    stopStreaming,
    clearOnRouteLeave,
    setSelectedKnowledgeBaseId,
  }
})
