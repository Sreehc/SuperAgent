import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryRouter, Link, RouterProvider, useLocation } from 'react-router-dom'
import { SettingsPage } from './SettingsPage'
import { useAuthStore } from '../../auth/store/auth'
import { useSettingsStore } from '../store/settings'

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function renderSettings(route = '/settings') {
  const router = createMemoryRouter(
    [
      {
        path: '/settings',
        element: (
          <>
            <LocationProbe />
            <Link to="/tools">离开设置</Link>
            <SettingsPage />
          </>
        ),
      },
      { path: '/tools', element: <LocationProbe /> },
    ],
    { initialEntries: [route] },
  )

  return render(<RouterProvider router={router} />)
}

describe('SettingsPage URL tabs and dirty state', () => {
  beforeEach(() => {
    useAuthStore.setState({
      user: { id: 1, username: 'owner', displayName: 'Owner' },
      tenants: [{ id: 10, name: 'Acme', code: 'acme', role: 'OWNER', status: 'active' }],
      currentTenantId: 10,
      initialized: true,
    })
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
    vi.spyOn(useSettingsStore.getState(), 'loadAll').mockResolvedValue(undefined)
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('restores the active tab from the URL and syncs tab changes back to the URL', async () => {
    renderSettings('/settings?tab=rag')

    expect(screen.getByRole('tab', { name: 'RAG' }).getAttribute('aria-selected')).toBe('true')
    expect(screen.getByLabelText('最大子问题数')).toBeTruthy()
    expect(screen.getByTestId('location').textContent).toBe('/settings?tab=rag')

    fireEvent.click(screen.getByRole('tab', { name: '工具权限' }))

    await waitFor(() => expect(screen.getByRole('tab', { name: '工具权限' }).getAttribute('aria-selected')).toBe('true'))
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/settings?tab=tools'))
  })

  it('shows dirty save state after a field changes and prevents accidental route leave', async () => {
    renderSettings('/settings?tab=rag')

    fireEvent.change(screen.getByLabelText('最大子问题数'), { target: { value: '6' } })

    expect(await screen.findByText('RAG 配置有未保存修改。')).toBeTruthy()
    expect(screen.getByRole('button', { name: '保存检索策略' }).hasAttribute('disabled')).toBe(false)
    expect(screen.getByText('dirty')).toBeTruthy()

    fireEvent.click(screen.getByRole('link', { name: '离开设置' }))

    expect(await screen.findByRole('dialog', { name: '未保存修改' })).toBeTruthy()
    expect(screen.getByTestId('location').textContent).toBe('/settings?tab=rag')
  })

  it('requires high-risk confirmation, keeps dirty state on cancel, and shows audit notice after save', async () => {
    const saveAgent = vi.spyOn(useSettingsStore.getState(), 'saveAgent').mockImplementation(async () => {
      useSettingsStore.setState((state) => ({
        dirtyTabs: { ...state.dirtyTabs, agent: false },
        lastSavedTab: 'agent',
        successMessage: 'Agent 设置已保存。',
      }))
    })
    renderSettings('/settings?tab=agent')

    fireEvent.change(screen.getByLabelText('允许的 HTTP 域名（每行一个）'), { target: { value: 'api.example.com' } })
    expect(await screen.findByText('智能体配置有未保存修改。')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '保存智能体设置' }))
    const dialog = await screen.findByRole('dialog', { name: '保存智能体设置' })

    expect(dialog.textContent).toContain('HTTP 域名')
    expect(dialog.textContent).toContain('当前版本使用二次确认和审计记录')
    expect(saveAgent).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: '取消' }))
    expect(saveAgent).not.toHaveBeenCalled()
    expect(await screen.findByText('智能体配置有未保存修改。')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '保存智能体设置' }))
    fireEvent.click(await screen.findByRole('button', { name: '确认保存并记录审计' }))

    await waitFor(() => expect(saveAgent).toHaveBeenCalledTimes(1))
    expect((await screen.findAllByText('Agent 设置已保存。已记录审计。')).length).toBeGreaterThanOrEqual(1)
  })

  it('shows validation errors on matching fields without clearing edited input', async () => {
    const saveRag = vi.spyOn(useSettingsStore.getState(), 'saveRag').mockImplementation(async () => {
      useSettingsStore.setState({
        fieldErrors: {
          vectorTopK: '向量召回 Top-K 必须大于 0',
          evidenceLimit: '证据条数上限必须大于 0',
        },
        errorMessage: 'RAG 设置校验失败，请修正高亮字段。',
      })
    })
    renderSettings('/settings?tab=rag')

    fireEvent.change(screen.getByLabelText('向量召回 Top-K'), { target: { value: '0' } })
    fireEvent.change(screen.getByLabelText('证据条数上限'), { target: { value: '0' } })
    fireEvent.click(screen.getByRole('button', { name: '保存检索策略' }))
    fireEvent.click(await screen.findByRole('button', { name: '确认保存并记录审计' }))

    await waitFor(() => expect(saveRag).toHaveBeenCalledTimes(1))
    expect(await screen.findByText('向量召回 Top-K 必须大于 0')).toBeTruthy()
    expect(await screen.findByText('证据条数上限必须大于 0')).toBeTruthy()
    expect((screen.getByLabelText('向量召回 Top-K') as HTMLInputElement).value).toBe('0')
    expect((screen.getByLabelText('证据条数上限') as HTMLInputElement).value).toBe('0')
    expect(screen.getAllByText('RAG 设置校验失败，请修正高亮字段。').length).toBeGreaterThanOrEqual(1)
  })
})
