import { create } from 'zustand'
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

export type SettingsTab = 'model' | 'rag' | 'rerank' | 'agent' | 'tools'

export interface ModelForm {
  provider: string
  baseUrl: string
  chatModel: string
  embeddingModel: string
  apiKey: string
  apiKeySet: boolean
}

export interface RagForm {
  rewriteEnabled: boolean
  subQuestionEnabled: boolean
  maxSubQuestions: number
  vectorTopK: number
  keywordTopK: number
  rrfK: number
  rerankEnabled: boolean
  evidenceLimit: number
  perQuestionEvidenceCharLimit: number
  totalEvidenceCharLimit: number
  minRelevanceScore: number
}

export interface RerankForm {
  enabled: boolean
  provider: string
  baseUrl: string
  model: string
  apiKey: string
  apiKeySet: boolean
}

export interface AgentForm {
  enabled: boolean
  maxModelSteps: number
  maxToolCalls: number
  checkpointEnabled: boolean
  defaultMemoryStrategy: string
  webSearchEnabled: boolean
  httpToolEnabled: boolean
  graphToolEnabled: boolean
  codeExecutionEnabled: boolean
  toolTimeoutMs: number
  allowedHttpDomainsText: string
}

export interface ToolForm {
  webSearchEnabled: boolean
  httpToolEnabled: boolean
  graphToolEnabled: boolean
  codeExecutionEnabled: boolean
  toolTimeoutMs: number
  searchProvider: string
  allowedHttpDomainsText: string
}

interface SettingsState {
  loading: boolean
  savingTab: SettingsTab | null
  errorMessage: string
  successMessage: string
  fieldErrors: Record<string, string>
  modelForm: ModelForm
  ragForm: RagForm
  rerankForm: RerankForm
  agentForm: AgentForm
  toolForm: ToolForm
}

interface SettingsActions {
  patchModel: (patch: Partial<ModelForm>) => void
  patchRag: (patch: Partial<RagForm>) => void
  patchRerank: (patch: Partial<RerankForm>) => void
  patchAgent: (patch: Partial<AgentForm>) => void
  patchTool: (patch: Partial<ToolForm>) => void
  loadAll: () => Promise<void>
  saveModel: () => Promise<void>
  saveRag: () => Promise<void>
  saveRerank: () => Promise<void>
  saveAgent: () => Promise<void>
  saveTools: () => Promise<void>
}

export type SettingsStore = SettingsState & SettingsActions

const initialState: SettingsState = {
  loading: false,
  savingTab: null,
  errorMessage: '',
  successMessage: '',
  fieldErrors: {},
  modelForm: { provider: 'openai-compatible', baseUrl: '', chatModel: '', embeddingModel: '', apiKey: '', apiKeySet: false },
  ragForm: {
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
  },
  rerankForm: { enabled: false, provider: 'openai-compatible', baseUrl: '', model: '', apiKey: '', apiKeySet: false },
  agentForm: {
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
  },
  toolForm: {
    webSearchEnabled: true,
    httpToolEnabled: false,
    graphToolEnabled: true,
    codeExecutionEnabled: false,
    toolTimeoutMs: 10000,
    searchProvider: 'tavily',
    allowedHttpDomainsText: '',
  },
}

function parseDomainLines(value: string) {
  return value
    .split(/\r?\n|,/)
    .map((item) => item.trim())
    .filter(Boolean)
}

export const useSettingsStore = create<SettingsStore>((set, get) => ({
  ...initialState,

  patchModel: (patch) => set((s) => ({ modelForm: { ...s.modelForm, ...patch } })),
  patchRag: (patch) => set((s) => ({ ragForm: { ...s.ragForm, ...patch } })),
  patchRerank: (patch) => set((s) => ({ rerankForm: { ...s.rerankForm, ...patch } })),
  patchAgent: (patch) => set((s) => ({ agentForm: { ...s.agentForm, ...patch } })),
  patchTool: (patch) => set((s) => ({ toolForm: { ...s.toolForm, ...patch } })),

  async loadAll() {
    set({ loading: true, errorMessage: '', successMessage: '', fieldErrors: {} })
    try {
      const [modelResponse, ragResponse, rerankResponse, agentResponse, toolResponse] = await Promise.all([
        getModelSettings(),
        getRagSettings(),
        getRerankSettings(),
        getAgentSettings(),
        getToolSettings(),
      ])

      set((s) => ({
        modelForm: {
          ...s.modelForm,
          provider: modelResponse.data.provider,
          baseUrl: modelResponse.data.baseUrl,
          chatModel: modelResponse.data.chatModel,
          embeddingModel: modelResponse.data.embeddingModel,
          apiKey: '',
          apiKeySet: modelResponse.data.apiKeySet,
        },
        ragForm: { ...s.ragForm, ...ragResponse.data },
        rerankForm: {
          ...s.rerankForm,
          enabled: rerankResponse.data.enabled,
          provider: rerankResponse.data.provider,
          baseUrl: rerankResponse.data.baseUrl ?? '',
          model: rerankResponse.data.model ?? '',
          apiKey: '',
          apiKeySet: rerankResponse.data.apiKeySet,
        },
        agentForm: {
          ...s.agentForm,
          ...agentResponse.data,
          allowedHttpDomainsText: agentResponse.data.allowedHttpDomains.join('\n'),
        },
        toolForm: {
          ...s.toolForm,
          ...toolResponse.data,
          allowedHttpDomainsText: toolResponse.data.allowedHttpDomains.join('\n'),
        },
      }))
    } catch {
      set({ errorMessage: '设置加载失败，请刷新后重试。' })
      throw new Error('设置加载失败，请刷新后重试。')
    } finally {
      set({ loading: false })
    }
  },

  async saveModel() {
    set({ savingTab: 'model', errorMessage: '', successMessage: '', fieldErrors: {} })
    try {
      const { modelForm } = get()
      const response = await updateModelSettings({
        baseUrl: modelForm.baseUrl,
        chatModel: modelForm.chatModel,
        embeddingModel: modelForm.embeddingModel,
        apiKey: modelForm.apiKey.trim() || undefined,
      })
      set((s) => ({ modelForm: { ...s.modelForm, apiKey: '', apiKeySet: response.data.apiKeySet }, successMessage: '模型设置已保存。' }))
    } catch (error) {
      handleSaveError(set, error, '模型设置校验失败，请修正高亮字段。', '模型设置保存失败，请检查输入后重试。')
    } finally {
      set({ savingTab: null })
    }
  },

  async saveRag() {
    set({ savingTab: 'rag', errorMessage: '', successMessage: '', fieldErrors: {} })
    try {
      const { ragForm } = get()
      await updateRagSettings({ ...ragForm })
      set({ successMessage: 'RAG 设置已保存。' })
    } catch (error) {
      handleSaveError(set, error, 'RAG 设置校验失败，请修正高亮字段。', 'RAG 设置保存失败，请稍后重试。')
    } finally {
      set({ savingTab: null })
    }
  },

  async saveRerank() {
    set({ savingTab: 'rerank', errorMessage: '', successMessage: '', fieldErrors: {} })
    try {
      const { rerankForm } = get()
      const response = await updateRerankSettings({
        enabled: rerankForm.enabled,
        provider: rerankForm.provider,
        baseUrl: rerankForm.baseUrl.trim() || undefined,
        model: rerankForm.model.trim() || undefined,
        apiKey: rerankForm.apiKey.trim() || undefined,
      })
      set((s) => ({ rerankForm: { ...s.rerankForm, apiKey: '', apiKeySet: response.data.apiKeySet }, successMessage: 'Rerank 设置已保存。' }))
    } catch (error) {
      handleSaveError(set, error, 'Rerank 设置校验失败，请修正高亮字段。', 'Rerank 设置保存失败，请稍后重试。')
    } finally {
      set({ savingTab: null })
    }
  },

  async saveAgent() {
    set({ savingTab: 'agent', errorMessage: '', successMessage: '', fieldErrors: {} })
    try {
      const { agentForm } = get()
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
      set({ successMessage: 'Agent 设置已保存。' })
    } catch (error) {
      handleSaveError(set, error, 'Agent 设置校验失败，请修正高亮字段。', 'Agent 设置保存失败，请稍后重试。')
    } finally {
      set({ savingTab: null })
    }
  },

  async saveTools() {
    set({ savingTab: 'tools', errorMessage: '', successMessage: '', fieldErrors: {} })
    try {
      const { toolForm } = get()
      await updateToolSettings({
        webSearchEnabled: toolForm.webSearchEnabled,
        httpToolEnabled: toolForm.httpToolEnabled,
        graphToolEnabled: toolForm.graphToolEnabled,
        codeExecutionEnabled: toolForm.codeExecutionEnabled,
        toolTimeoutMs: toolForm.toolTimeoutMs,
        searchProvider: toolForm.searchProvider,
        allowedHttpDomains: parseDomainLines(toolForm.allowedHttpDomainsText),
      })
      set({ successMessage: 'Tools 设置已保存。' })
    } catch (error) {
      handleSaveError(set, error, 'Tools 设置校验失败，请修正高亮字段。', 'Tools 设置保存失败，请稍后重试。')
    } finally {
      set({ savingTab: null })
    }
  },
}))

function handleSaveError(
  set: (partial: Partial<SettingsState>) => void,
  error: unknown,
  validationMessage: string,
  genericMessage: string,
) {
  if (error instanceof SettingsValidationError) {
    const fieldErrors: Record<string, string> = {}
    for (const item of error.fieldErrors) {
      const key = item.field ?? item.parameter
      if (key) {
        fieldErrors[key] = item.message
      }
    }
    if (Object.keys(fieldErrors).length > 0) {
      set({ fieldErrors, errorMessage: validationMessage })
      return
    }
  }
  set({ errorMessage: genericMessage })
}
