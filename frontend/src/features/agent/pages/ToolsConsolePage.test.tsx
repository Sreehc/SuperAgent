import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { ToolsConsolePage } from './ToolsConsolePage'
import {
  deleteToolSecret,
  getAgentRunDetail,
  listAgentRuns,
  listPlugins,
  listToolCalls,
  listToolCapabilities,
  updatePlugin,
  updateToolSecret,
} from '../api'
import type { ApiEnvelope } from '@/api/types'
import type { AgentPagedResult, AgentRunDetail, PluginItem, ToolCallDetail, ToolCapabilityResponse } from '../types'

vi.mock('../api', () => ({
  listToolCapabilities: vi.fn(),
  listPlugins: vi.fn(),
  listToolCalls: vi.fn(),
  listAgentRuns: vi.fn(),
  getAgentRunDetail: vi.fn(),
  updatePlugin: vi.fn(),
  updateToolSecret: vi.fn(),
  deleteToolSecret: vi.fn(),
}))

function envelope<T>(data: T): ApiEnvelope<T> {
  return {
    success: true,
    code: 'OK',
    message: 'ok',
    data,
    traceId: 'test-trace',
  }
}

const capabilities: ToolCapabilityResponse = {
  tools: [
    {
      toolId: 'web_search',
      name: 'Web Search',
      kind: 'http',
      riskLevel: 'medium',
      enabled: true,
      executable: true,
      requiresConfirmation: false,
      reason: '',
      description: 'Search external sources',
      configuredSecrets: [],
    },
    {
      toolId: 'github_issue',
      name: 'GitHub Issue',
      kind: 'plugin',
      riskLevel: 'high',
      enabled: true,
      executable: false,
      requiresConfirmation: true,
      reason: '缺少 GITHUB_TOKEN',
      description: 'Create GitHub issues',
      configuredSecrets: ['GITHUB_TOKEN'],
    },
  ],
}

const plugins: PluginItem[] = [
  {
    pluginId: 12,
    pluginKey: 'github',
    version: '1.2.0',
    displayName: 'GitHub',
    enabled: true,
    status: 'installed',
    manifest: {},
    installationConfig: {},
    enabledTools: ['github_issue'],
    secretKeys: ['GITHUB_TOKEN', 'GITHUB_APP_ID'],
    recentErrorCount: 2,
    updatedAt: '2026-06-20T10:30:00',
  },
  {
    pluginId: 13,
    pluginKey: 'slack',
    version: '0.8.0',
    displayName: 'Slack',
    enabled: false,
    status: 'disabled',
    manifest: {},
    installationConfig: {},
    enabledTools: ['slack_message'],
    secretKeys: [],
    recentErrorCount: 0,
    updatedAt: '2026-06-20T11:30:00',
  },
]

const toolCalls: ToolCallDetail[] = [
  {
    id: 501,
    agentRunId: 9001,
    toolId: 'github_issue',
    pluginId: 12,
    pluginVersion: '1.2.0',
    requestSummary: '创建 GitHub issue',
    responseSummary: 'Issue #42 created',
    status: 'success',
    latencyMs: 640,
    errorMessage: null,
    metadata: { owner: 'superagent' },
    createdAt: '2026-06-20T12:10:00',
  },
  {
    id: 502,
    agentRunId: 9002,
    toolId: 'web_search',
    pluginId: null,
    pluginVersion: null,
    requestSummary: '搜索退款政策',
    responseSummary: null,
    status: 'failed',
    latencyMs: 1200,
    errorMessage: 'HTTP 403',
    metadata: {},
    createdAt: '2026-06-20T12:20:00',
  },
]

const agentRuns: AgentPagedResult<AgentRunDetail['summary']> = {
  items: [
    {
      runId: 9001,
      sessionId: 42,
      exchangeId: 5001,
      status: 'completed',
      memoryStrategy: 'sliding',
      routeReason: '需要调用 GitHub 工具',
      modelStepCount: 2,
      toolCallCount: 1,
      latestCheckpointNo: 3,
      errorMessage: null,
      startedAt: '2026-06-20T12:00:00',
      finishedAt: '2026-06-20T12:01:00',
    },
  ],
  page: 1,
  pageSize: 10,
  total: 12,
}

const agentRunDetail: AgentRunDetail = {
  summary: agentRuns.items[0],
  steps: [
    {
      id: 7001,
      stepNo: 1,
      phase: 'tool',
      status: 'success',
      decisionSummary: '准备创建 Issue',
      observationSummary: 'GitHub 返回创建结果',
      selectedToolId: 'github_issue',
      selectedToolReason: '用户要求创建问题',
      errorMessage: null,
      metadata: {},
      startedAt: '2026-06-20T12:00:10',
      finishedAt: '2026-06-20T12:00:30',
    },
  ],
  checkpoints: [
    {
      id: 8001,
      checkpointNo: 3,
      stepId: 7001,
      checkpointType: 'tool_call_finished',
      stable: true,
      payload: {},
      createdAt: '2026-06-20T12:00:40',
    },
  ],
  toolCalls: [toolCalls[0]],
}

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function renderTools(route = '/tools') {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route
            path="/tools"
            element={
              <>
                <LocationProbe />
                <ToolsConsolePage />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ToolsConsolePage tabs', () => {
  beforeEach(() => {
    vi.mocked(listToolCapabilities).mockResolvedValue(envelope(capabilities))
    vi.mocked(listPlugins).mockResolvedValue(envelope(plugins))
    vi.mocked(listToolCalls).mockResolvedValue(envelope(toolCalls))
    vi.mocked(listAgentRuns).mockResolvedValue(envelope(agentRuns))
    vi.mocked(getAgentRunDetail).mockResolvedValue(envelope(agentRunDetail))
    vi.mocked(updatePlugin).mockResolvedValue({} as never)
    vi.mocked(updateToolSecret).mockResolvedValue({} as never)
    vi.mocked(deleteToolSecret).mockResolvedValue({} as never)
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('shows the five tools views and keeps the capability list as the default tab', async () => {
    renderTools()

    expect(screen.getByRole('tab', { name: '能力' }).getAttribute('aria-selected')).toBe('true')
    expect(screen.getByRole('tab', { name: '插件' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '密钥' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '工具调用' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: 'Agent Runs' })).toBeTruthy()
    expect(await screen.findByText('Web Search')).toBeTruthy()
    expect(screen.getByTestId('location').textContent).toBe('/tools')
  })

  it('switches tabs and syncs the active view to the URL', async () => {
    renderTools()

    fireEvent.click(screen.getByRole('tab', { name: '工具调用' }))

    await waitFor(() => expect(screen.getByRole('tab', { name: '工具调用' }).getAttribute('aria-selected')).toBe('true'))
    expect(await screen.findByTestId('tool-call-501')).toBeTruthy()
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/tools?tab=tool-calls'))
  })

  it('restores the active tab from the URL query', async () => {
    renderTools('/tools?tab=secrets')

    expect(screen.getByRole('tab', { name: '密钥' }).getAttribute('aria-selected')).toBe('true')
    expect(await screen.findByText('GITHUB_TOKEN')).toBeTruthy()
  })

  it('shows enabled, executable, confirmation, risk and unavailable reason for each capability', async () => {
    renderTools()

    expect(await screen.findByTestId('tool-capability-web_search')).toBeTruthy()
    const unavailableTool = screen.getByTestId('tool-capability-github_issue')

    expect(unavailableTool.textContent).toContain('GitHub Issue')
    expect(unavailableTool.textContent).toContain('high')
    expect(unavailableTool.textContent).toContain('已启用')
    expect(unavailableTool.textContent).toContain('不可执行')
    expect(unavailableTool.textContent).toContain('需要确认')
    expect(unavailableTool.textContent).toContain('缺少 GITHUB_TOKEN')
  })

  it('lists plugins and requires confirmation before toggling plugin availability', async () => {
    renderTools('/tools?tab=plugins')

    const githubPlugin = await screen.findByTestId('tool-plugin-12')
    expect(githubPlugin.textContent).toContain('GitHub')
    expect(githubPlugin.textContent).toContain('已启用')
    expect(githubPlugin.textContent).toContain('github_issue')
    expect(githubPlugin.textContent).toContain('GITHUB_TOKEN')
    expect(githubPlugin.textContent).toContain('近期错误 2')

    expect(screen.getByTestId('tool-plugin-13').textContent).toContain('已禁用')
    expect(screen.getByRole('button', { name: '启用 Slack' })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '禁用 GitHub' }))

    expect(screen.getByText('禁用插件 GitHub')).toBeTruthy()
    expect(vi.mocked(updatePlugin)).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: '确认禁用' }))

    await waitFor(() => expect(vi.mocked(updatePlugin)).toHaveBeenCalledWith(12, false))
    await waitFor(() => expect(vi.mocked(listPlugins).mock.calls.length).toBeGreaterThan(1))
    await waitFor(() => expect(vi.mocked(listToolCapabilities).mock.calls.length).toBeGreaterThan(1))
  })

  it('shows secret status, writes values without echoing plaintext, and clears input after saving', async () => {
    renderTools('/tools?tab=secrets')

    const configuredSecret = await screen.findByTestId('tool-secret-github_issue-GITHUB_TOKEN')
    expect(configuredSecret.textContent).toContain('GitHub')
    expect(configuredSecret.textContent).toContain('github_issue')
    expect(configuredSecret.textContent).toContain('GITHUB_TOKEN')
    expect(configuredSecret.textContent).toContain('已设置')
    expect(configuredSecret.textContent).not.toContain('super-secret')

    const missingSecret = screen.getByTestId('tool-secret-github_issue-GITHUB_APP_ID')
    expect(missingSecret.textContent).toContain('未设置')

    const secretInput = screen.getByLabelText('github_issue GITHUB_APP_ID 密钥值') as HTMLInputElement
    fireEvent.change(secretInput, { target: { value: 'super-secret' } })
    fireEvent.click(screen.getByRole('button', { name: '保存 github_issue GITHUB_APP_ID' }))

    await waitFor(() => expect(vi.mocked(updateToolSecret)).toHaveBeenCalledWith('github_issue', 'GITHUB_APP_ID', 'super-secret'))
    await waitFor(() => expect(vi.mocked(listToolCapabilities).mock.calls.length).toBeGreaterThan(1))
    expect(secretInput.value).toBe('')
    expect(screen.queryByText('super-secret')).toBeNull()
  })

  it('keeps secret input on save failure and confirms deletion before removing configured secrets', async () => {
    vi.mocked(updateToolSecret).mockRejectedValueOnce(new Error('network failed'))
    renderTools('/tools?tab=secrets')

    const secretInput = (await screen.findByLabelText('github_issue GITHUB_APP_ID 密钥值')) as HTMLInputElement
    fireEvent.change(secretInput, { target: { value: 'keep-me' } })
    fireEvent.click(screen.getByRole('button', { name: '保存 github_issue GITHUB_APP_ID' }))

    await waitFor(() => expect(vi.mocked(updateToolSecret)).toHaveBeenCalledWith('github_issue', 'GITHUB_APP_ID', 'keep-me'))
    expect(await screen.findByText('密钥保存失败，请检查权限或稍后重试。')).toBeTruthy()
    expect(secretInput.value).toBe('keep-me')

    fireEvent.click(screen.getByRole('button', { name: '删除 github_issue GITHUB_TOKEN' }))
    expect(screen.getByText('删除密钥 GITHUB_TOKEN')).toBeTruthy()
    expect(vi.mocked(deleteToolSecret)).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))

    await waitFor(() => expect(vi.mocked(deleteToolSecret)).toHaveBeenCalledWith('github_issue', 'GITHUB_TOKEN'))
    await waitFor(() => expect(vi.mocked(listToolCapabilities).mock.calls.length).toBeGreaterThan(1))
  })

  it('lists tool calls, filters by run and tool, and opens call details', async () => {
    renderTools('/tools?tab=tool-calls')

    const callRow = await screen.findByTestId('tool-call-501')
    expect(callRow.textContent).toContain('github_issue')
    expect(callRow.textContent).toContain('Run #9001')
    expect(callRow.textContent).toContain('Issue #42 created')
    expect(screen.getByTestId('tool-call-502').textContent).toContain('HTTP 403')

    fireEvent.change(screen.getByLabelText('Agent Run ID'), { target: { value: '9001' } })
    fireEvent.change(screen.getByLabelText('工具 ID'), { target: { value: 'github_issue' } })
    fireEvent.click(screen.getByRole('button', { name: '筛选工具调用' }))

    await waitFor(() => expect(vi.mocked(listToolCalls)).toHaveBeenLastCalledWith({ runId: 9001, toolId: 'github_issue' }))

    const filteredCallRow = await screen.findByTestId('tool-call-501')
    fireEvent.click(within(filteredCallRow).getByRole('button', { name: '查看工具调用 501' }))

    const drawer = await screen.findByRole('dialog', { name: '工具调用 #501' })
    expect(within(drawer).getByText('创建 GitHub issue')).toBeTruthy()
    expect(within(drawer).getByText('Issue #42 created')).toBeTruthy()
    expect(within(drawer).getByText(/"owner": "superagent"/)).toBeTruthy()
  })

  it('lists agent runs with status filter and pagination, and opens run details', async () => {
    renderTools('/tools?tab=agent-runs')

    const runRow = await screen.findByTestId('agent-run-9001')
    expect(runRow.textContent).toContain('Run #9001')
    expect(runRow.textContent).toContain('completed')
    expect(runRow.textContent).toContain('工具调用 1')
    expect(runRow.textContent).toContain('需要调用 GitHub 工具')

    fireEvent.change(screen.getByLabelText('运行状态'), { target: { value: 'running' } })
    fireEvent.click(screen.getByRole('button', { name: '筛选 Agent Runs' }))

    await waitFor(() => expect(vi.mocked(listAgentRuns)).toHaveBeenLastCalledWith({ page: 1, pageSize: 10, status: 'running' }))

    const nextPageButton = await screen.findByRole('button', { name: '下一页 Agent Runs' })
    await waitFor(() => expect(nextPageButton.hasAttribute('disabled')).toBe(false))
    fireEvent.click(nextPageButton)

    await waitFor(() =>
      expect(vi.mocked(listAgentRuns).mock.calls).toContainEqual([{ page: 2, pageSize: 10, status: 'running' }]),
    )

    const currentRunRow = await screen.findByTestId('agent-run-9001')
    fireEvent.click(within(currentRunRow).getByRole('button', { name: '查看 Agent Run 9001' }))

    const drawer = await screen.findByRole('dialog', { name: 'Agent Run #9001' })
    expect(vi.mocked(getAgentRunDetail)).toHaveBeenCalledWith(9001)
    expect(await within(drawer).findByText('准备创建 Issue')).toBeTruthy()
    expect(within(drawer).getByText('Checkpoint #3')).toBeTruthy()
    expect(within(drawer).getAllByText('github_issue').length).toBeGreaterThan(0)
  })
})
