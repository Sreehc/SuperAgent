import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import {
  getModelSettings,
  getRagSettings,
  getRerankSettings,
  updateModelSettings,
  updateRagSettings,
  updateRerankSettings,
} from '../api'

type SettingsTab = 'model' | 'rag' | 'rerank'

export const useSettingsStore = defineStore('settings', () => {
  const loading = ref(false)
  const savingTab = ref<SettingsTab | null>(null)
  const errorMessage = ref('')
  const successMessage = ref('')

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

  async function loadAll() {
    loading.value = true
    errorMessage.value = ''
    successMessage.value = ''
    try {
      const [modelResponse, ragResponse, rerankResponse] = await Promise.all([
        getModelSettings(),
        getRagSettings(),
        getRerankSettings(),
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
    } catch {
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
    try {
      await updateRagSettings({ ...ragForm })
      successMessage.value = 'RAG 设置已保存。'
    } catch {
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
    } catch {
      errorMessage.value = 'Rerank 设置保存失败，请稍后重试。'
      throw new Error(errorMessage.value)
    } finally {
      savingTab.value = null
    }
  }

  return {
    loading,
    savingTab,
    errorMessage,
    successMessage,
    modelForm,
    ragForm,
    rerankForm,
    loadAll,
    saveModel,
    saveRag,
    saveRerank,
  }
})
