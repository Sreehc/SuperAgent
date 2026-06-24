import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SettingsValidationError, updateRagSettings } from '../api'
import { useSettingsStore } from './settings'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return {
    ...actual,
    updateRagSettings: vi.fn(),
  }
})

const mockUpdateRagSettings = vi.mocked(updateRagSettings)

describe('settings store dirty state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useSettingsStore.setState({
      loading: false,
      savingTab: null,
      dirtyTabs: { model: false, rag: false, rerank: false, agent: false, tools: false },
      lastSavedTab: null,
      errorMessage: '',
      successMessage: '',
      fieldErrors: {},
      modelForm: {
        provider: 'openai-compatible',
        baseUrl: 'https://api.example.com',
        chatModel: 'gpt-4.1',
        embeddingModel: 'text-embedding-3-large',
        apiKey: '',
        apiKeySet: true,
      },
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
    })
  })

  it('marks only the patched settings tab as dirty', () => {
    expect(useSettingsStore.getState().dirtyTabs.model).toBe(false)
    expect(useSettingsStore.getState().dirtyTabs.rag).toBe(false)

    useSettingsStore.getState().patchModel({ chatModel: 'gpt-5' })

    expect(useSettingsStore.getState().dirtyTabs.model).toBe(true)
    expect(useSettingsStore.getState().dirtyTabs.rag).toBe(false)
  })

  it('keeps edited values and maps validation field errors after save failure', async () => {
    useSettingsStore.getState().patchRag({ vectorTopK: 0, evidenceLimit: 0 })
    mockUpdateRagSettings.mockRejectedValue(
      new SettingsValidationError('Validation failed', [
        { field: 'vector_top_k', message: '向量召回 Top-K 必须大于 0' },
        { parameter: 'evidenceLimit', message: '证据条数上限必须大于 0' },
      ]),
    )

    await useSettingsStore.getState().saveRag()

    expect(useSettingsStore.getState().ragForm.vectorTopK).toBe(0)
    expect(useSettingsStore.getState().ragForm.evidenceLimit).toBe(0)
    expect(useSettingsStore.getState().fieldErrors.vectorTopK).toBe('向量召回 Top-K 必须大于 0')
    expect(useSettingsStore.getState().fieldErrors.evidenceLimit).toBe('证据条数上限必须大于 0')
    expect(useSettingsStore.getState().dirtyTabs.rag).toBe(true)
  })
})
