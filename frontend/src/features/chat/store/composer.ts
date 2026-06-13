import { defineStore } from 'pinia'
import { ref } from 'vue'
import { defaultMemoryStrategy } from '../api'
import type { MemoryStrategy, RequestedExecutionMode } from '../types'
import { listKnowledgeBases } from '../../knowledge/api'
import { listToolCapabilities } from '../../agent/api'
import type { ToolCapabilityItem } from '../../agent/types'

export const useChatComposerStore = defineStore('chat-composer', () => {
  const message = ref('')
  const memoryStrategy = ref<MemoryStrategy>(defaultMemoryStrategy())
  const executionMode = ref<RequestedExecutionMode>('AUTO')
  const availableKnowledgeBases = ref<Array<{ id: number; name: string }>>([])
  const selectedKnowledgeBaseId = ref<number | null>(null)
  const toolCapabilities = ref<ToolCapabilityItem[]>([])

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

  async function fetchToolCapabilities() {
    try {
      const response = await listToolCapabilities()
      toolCapabilities.value = response.data.tools
    } catch {
      toolCapabilities.value = []
    }
  }

  function applyConversationConfig(config: { memoryStrategy: MemoryStrategy; knowledgeBaseId: number | null }) {
    memoryStrategy.value = config.memoryStrategy
    selectedKnowledgeBaseId.value = config.knowledgeBaseId
  }

  function setSelectedKnowledgeBaseId(value: number | null) {
    selectedKnowledgeBaseId.value = value
  }

  function setExecutionMode(value: RequestedExecutionMode) {
    executionMode.value = value
  }

  function useRecommendation(question: string) {
    message.value = question
  }

  function clearMessage() {
    message.value = ''
  }

  return {
    message,
    memoryStrategy,
    executionMode,
    availableKnowledgeBases,
    selectedKnowledgeBaseId,
    toolCapabilities,
    fetchKnowledgeBaseOptions,
    fetchToolCapabilities,
    applyConversationConfig,
    setSelectedKnowledgeBaseId,
    setExecutionMode,
    useRecommendation,
    clearMessage,
  }
})
