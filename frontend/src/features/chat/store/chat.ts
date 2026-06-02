import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  createConversation,
  defaultMemoryStrategy,
  deleteConversation,
  getConversation,
  listConversations,
  listMessages,
  openMessageStream,
  resumeConversation,
  stopConversation,
  updateConversation,
} from '../api'
import type {
  ConversationDetail,
  ConversationMessage,
  ConversationSummary,
  MemoryStrategy,
  StreamAgentStepEvent,
  StreamCheckpointEvent,
  StreamDeltaEvent,
  StreamDoneEvent,
  StreamErrorEvent,
  StreamRecommendationEvent,
  StreamReferenceEvent,
  StreamResumeEvent,
  StreamStartEvent,
  StreamToolResultEvent,
  StreamToolStartEvent,
  StreamTraceStageEvent,
} from '../types'
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
  runId: number | null
  stage: string | null
  recommendations: string[]
  error: string
  stopped: boolean
  timeline: Array<{
    type: 'agent_step' | 'tool_start' | 'tool_result' | 'checkpoint' | 'resume'
    title: string
    summary: string
  }>
}

function createEmptyStreamState(): StreamState {
  return {
    exchangeId: null,
    runId: null,
    stage: null,
    recommendations: [],
    error: '',
    stopped: false,
    timeline: [],
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
  const keyword = ref('')
  const editingConversationId = ref<number | null>(null)
  const updatingConversation = ref(false)
  const deletingConversation = ref(false)

  const hasMessages = computed(() => messages.value.length > 0)
  const filteredConversations = computed(() => {
    const search = keyword.value.trim().toLowerCase()
    if (!search) {
      return conversations.value
    }
    return conversations.value.filter((conversation) => conversation.title.toLowerCase().includes(search))
  })

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
      case 'agent_step': {
        const event = parsed as StreamAgentStepEvent
        streamState.value.runId = event.runId
        streamState.value.timeline = [
          ...streamState.value.timeline,
          { type: 'agent_step', title: `${event.phase} #${event.stepNo}`, summary: event.summary },
        ]
        streamState.value.stage = `${event.phase} · ${event.status}`
        break
      }
      case 'tool_start': {
        const event = parsed as StreamToolStartEvent
        streamState.value.runId = event.runId
        streamState.value.timeline = [
          ...streamState.value.timeline,
          { type: 'tool_start', title: event.toolId, summary: event.summary },
        ]
        break
      }
      case 'tool_result': {
        const event = parsed as StreamToolResultEvent
        streamState.value.runId = event.runId
        streamState.value.timeline = [
          ...streamState.value.timeline,
          { type: 'tool_result', title: `${event.toolId} · ${event.status}`, summary: event.summary },
        ]
        break
      }
      case 'checkpoint': {
        const event = parsed as StreamCheckpointEvent
        streamState.value.runId = event.runId
        streamState.value.timeline = [
          ...streamState.value.timeline,
          { type: 'checkpoint', title: `Checkpoint #${event.checkpointNo}`, summary: `${event.phase} · ${event.stable ? 'stable' : 'pending'}` },
        ]
        break
      }
      case 'resume': {
        const event = parsed as StreamResumeEvent
        streamState.value.runId = event.runId
        streamState.value.timeline = [
          ...streamState.value.timeline,
          { type: 'resume', title: '恢复执行', summary: event.status },
        ]
        break
      }
      case 'done': {
        const event = parsed as StreamDoneEvent
        if (event.runId) {
          streamState.value.runId = event.runId
        }
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
        if (event.runId) {
          streamState.value.runId = event.runId
        }
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
    parserOffset.value = 0
    parserBuffer.value = ''
    streamedResponse.value = ''
  }

  function setSelectedKnowledgeBaseId(value: number | null) {
    selectedKnowledgeBaseId.value = value
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
    composerMessage,
    memoryStrategy,
    streamState,
    selectedReference,
    availableKnowledgeBases,
    selectedKnowledgeBaseId,
    keyword,
    editingConversationId,
    updatingConversation,
    deletingConversation,
    hasMessages,
    bootstrap,
    fetchConversations,
    fetchKnowledgeBaseOptions,
    createAndSelectConversation,
    selectConversation,
    sendMessage,
    stopStreaming,
    resumeConversationRun,
    clearOnRouteLeave,
    setSelectedKnowledgeBaseId,
    renameConversation,
    archiveConversation,
    removeConversation,
  }
})
