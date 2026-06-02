import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import {
  getAgentSettings,
  getModelSettings,
  getRagSettings,
  getRerankSettings,
  getToolSettings,
  SettingsValidationError,
  updateAgentSettings,
  updateModelSettings,
  updateRagSettings,
  updateRerankSettings,
  updateToolSettings,
} from '../api'

type SettingsTab = 'model' | 'rag' | 'rerank' | 'agent' | 'tools'

export const useSettingsStore = defineStore('settings', () => {
  const loading = ref(false)
  const savingTab = ref<SettingsTab | null>(null)
  const errorMessage = ref('')
  const successMessage = ref('')
  const fieldErrors = reactive<Record<string, string>>({})

  const modelForm = reactive({
    provider: 'openai-compatible',
    baseUrl: '',
    chatModel: '',
    embeddingModel: '',
    apiKey: '',
    apiKeySet: false,
  })

  const ragForm = reactive({
    rewriteEnabled: true,
    subQuestionEnabled: true,
    maxSubQuestions: 4,
    vectorTopK: 20,
    keywordTopK: 20,
    rrfK: 60,
    rerankEnabled: false,
    evidenceLimit: 8,
    perQuestionEvidenceCharLimit: 4000,
    totalEvidenceCharLimit: 12000,
    minRelevanceScore: 0.35,
  })

  const rerankForm = reactive({
    enabled: false,
    provider: 'openai-compatible',
    baseUrl: '',
    model: '',
    apiKey: '',
    apiKeySet: false,
  })

  const agentForm = reactive({
    enabled: true,
    maxModelSteps: 6,
    maxToolCalls: 6,
    checkpointEnabled: true,
    defaultMemoryStrategy: 'SUMMARY_PLUS_WINDOW',
    webSearchEnabled: true,
    httpToolEnabled: false,
    graphToolEnabled: true,
    codeExecutionEnabled: false,
    toolTimeoutMs: 10000,
    allowedHttpDomainsText: '',
  })

  const toolForm = reactive({
    webSearchEnabled: true,
    httpToolEnabled: false,
    graphToolEnabled: true,
    codeExecutionEnabled: false,
    toolTimeoutMs: 10000,
    searchProvider: 'tavily',
    allowedHttpDomainsText: '',
  })

  async function loadAll() {
    loading.value = true
    errorMessage.value = ''
    successMessage.value = ''
    clearFieldErrors()
    try {
      const [modelResponse, ragResponse, rerankResponse, agentResponse, toolResponse] = await Promise.all([
        getModelSettings(),
        getRagSettings(),
        getRerankSettings(),
        getAgentSettings(),
        getToolSettings(),
      ])

      Object.assign(modelForm, {
        provider: modelResponse.data.provider,
        baseUrl: modelResponse.data.baseUrl,
        chatModel: modelResponse.data.chatModel,
        embeddingModel: modelResponse.data.embeddingModel,
        apiKey: '',
        apiKeySet: modelResponse.data.apiKeySet,
      })

      Object.assign(ragForm, ragResponse.data)

      Object.assign(rerankForm, {
        enabled: rerankResponse.data.enabled,
        provider: rerankResponse.data.provider,
        baseUrl: rerankResponse.data.baseUrl ?? '',
        model: rerankResponse.data.model ?? '',
        apiKey: '',
        apiKeySet: rerankResponse.data.apiKeySet,
      })

      Object.assign(agentForm, {
        ...agentResponse.data,
        allowedHttpDomainsText: agentResponse.data.allowedHttpDomains.join('\n'),
      })

      Object.assign(toolForm, {
        ...toolResponse.data,
        allowedHttpDomainsText: toolResponse.data.allowedHttpDomains.join('\n'),
      })
    } catch {
      errorMessage.value = '设置加载失败，请刷新后重试。'
      throw new Error(errorMessage.value)
    } finally {
      loading.value = false
    }
  }

  async function saveModel() {
    savingTab.value = 'model'
    errorMessage.value = ''
    successMessage.value = ''
    clearFieldErrors()
    try {
      const response = await updateModelSettings({
        baseUrl: modelForm.baseUrl,
        chatModel: modelForm.chatModel,
        embeddingModel: modelForm.embeddingModel,
        apiKey: modelForm.apiKey.trim() || undefined,
      })
      modelForm.apiKey = ''
      modelForm.apiKeySet = response.data.apiKeySet
      successMessage.value = '模型设置已保存。'
    } catch (error) {
      if (applyValidationError(error)) {
        errorMessage.value = '模型设置校验失败，请修正高亮字段。'
        throw error
      }
      errorMessage.value = '模型设置保存失败，请检查输入后重试。'
      throw new Error(errorMessage.value)
    } finally {
      savingTab.value = null
    }
  }

  async function saveRag() {
    savingTab.value = 'rag'
    errorMessage.value = ''
    successMessage.value = ''
    clearFieldErrors()
    try {
      await updateRagSettings({ ...ragForm })
      successMessage.value = 'RAG 设置已保存。'
    } catch (error) {
      if (applyValidationError(error)) {
        errorMessage.value = 'RAG 设置校验失败，请修正高亮字段。'
        throw error
      }
      errorMessage.value = 'RAG 设置保存失败，请稍后重试。'
      throw new Error(errorMessage.value)
    } finally {
      savingTab.value = null
    }
  }

  async function saveRerank() {
    savingTab.value = 'rerank'
    errorMessage.value = ''
    successMessage.value = ''
    clearFieldErrors()
    try {
      const response = await updateRerankSettings({
        enabled: rerankForm.enabled,
        provider: rerankForm.provider,
        baseUrl: rerankForm.baseUrl.trim() || undefined,
        model: rerankForm.model.trim() || undefined,
        apiKey: rerankForm.apiKey.trim() || undefined,
      })
      rerankForm.apiKey = ''
      rerankForm.apiKeySet = response.data.apiKeySet
      successMessage.value = 'Rerank 设置已保存。'
    } catch (error) {
      if (applyValidationError(error)) {
        errorMessage.value = 'Rerank 设置校验失败，请修正高亮字段。'
        throw error
      }
      errorMessage.value = 'Rerank 设置保存失败，请稍后重试。'
      throw new Error(errorMessage.value)
    } finally {
      savingTab.value = null
    }
  }

  async function saveAgent() {
    savingTab.value = 'agent'
    errorMessage.value = ''
    successMessage.value = ''
    clearFieldErrors()
    try {
      await updateAgentSettings({
        enabled: agentForm.enabled,
        maxModelSteps: agentForm.maxModelSteps,
        maxToolCalls: agentForm.maxToolCalls,
        checkpointEnabled: agentForm.checkpointEnabled,
        defaultMemoryStrategy: agentForm.defaultMemoryStrategy,
        webSearchEnabled: agentForm.webSearchEnabled,
        httpToolEnabled: agentForm.httpToolEnabled,
        graphToolEnabled: agentForm.graphToolEnabled,
        codeExecutionEnabled: agentForm.codeExecutionEnabled,
        toolTimeoutMs: agentForm.toolTimeoutMs,
        allowedHttpDomains: parseDomainLines(agentForm.allowedHttpDomainsText),
      })
      successMessage.value = 'Agent 设置已保存。'
    } catch (error) {
      if (applyValidationError(error)) {
        errorMessage.value = 'Agent 设置校验失败，请修正高亮字段。'
        throw error
      }
      errorMessage.value = 'Agent 设置保存失败，请稍后重试。'
      throw new Error(errorMessage.value)
    } finally {
      savingTab.value = null
    }
  }

  async function saveTools() {
    savingTab.value = 'tools'
    errorMessage.value = ''
    successMessage.value = ''
    clearFieldErrors()
    try {
      await updateToolSettings({
        webSearchEnabled: toolForm.webSearchEnabled,
        httpToolEnabled: toolForm.httpToolEnabled,
        graphToolEnabled: toolForm.graphToolEnabled,
        codeExecutionEnabled: toolForm.codeExecutionEnabled,
        toolTimeoutMs: toolForm.toolTimeoutMs,
        searchProvider: toolForm.searchProvider,
        allowedHttpDomains: parseDomainLines(toolForm.allowedHttpDomainsText),
      })
      successMessage.value = 'Tools 设置已保存。'
    } catch (error) {
      if (applyValidationError(error)) {
        errorMessage.value = 'Tools 设置校验失败，请修正高亮字段。'
        throw error
      }
      errorMessage.value = 'Tools 设置保存失败，请稍后重试。'
      throw new Error(errorMessage.value)
    } finally {
      savingTab.value = null
    }
  }

  function clearFieldErrors() {
    Object.keys(fieldErrors).forEach((key) => {
      delete fieldErrors[key]
    })
  }

  function applyValidationError(error: unknown) {
    if (!(error instanceof SettingsValidationError)) {
      return false
    }
    for (const item of error.fieldErrors) {
      const key = item.field ?? item.parameter
      if (key) {
        fieldErrors[key] = item.message
      }
    }
    return Object.keys(fieldErrors).length > 0
  }

  function parseDomainLines(value: string) {
    return value
      .split(/\r?\n|,/)
      .map((item) => item.trim())
      .filter(Boolean)
  }

  return {
    loading,
    savingTab,
    errorMessage,
    successMessage,
    fieldErrors,
    modelForm,
    ragForm,
    rerankForm,
    agentForm,
    toolForm,
    loadAll,
    saveModel,
    saveRag,
    saveRerank,
    saveAgent,
    saveTools,
  }
})
