import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { TraceDetailPage } from './TraceDetailPage'
import { getAdminTrace } from '../api'
import { getAgentRunDetail } from '@/features/agent/api'
import type { ApiEnvelope } from '@/api/types'
import type { AgentRunDetail } from '@/features/agent/types'
import type { TraceDetail } from '../types'

vi.mock('../api', () => ({
  getAdminTrace: vi.fn(),
}))

vi.mock('@/features/agent/api', () => ({
  getAgentRunDetail: vi.fn(),
}))

const traceDetail: TraceDetail = {
  exchangeId: 5001,
  sessionId: 42,
  userId: 7,
  executionMode: 'RAG_QA',
  status: 'success',
  routeReason: '命中 RAG 问答路径',
  agentRunId: 9001,
  agentRunStatus: 'completed',
  startedAt: '2026-06-20T10:30:00',
  finishedAt: '2026-06-20T10:31:12',
  durationMs: 72000,
  stages: [
    {
      stageId: 1,
      stageCode: 'RETRIEVE',
      status: 'success',
      inputSummary: '用户询问退款政策',
      outputSummary: '检索到 2 条候选片段',
      errorMessage: null,
      startedAt: '2026-06-20T10:30:00',
      finishedAt: '2026-06-20T10:30:20',
      durationMs: 20000,
    },
    {
      stageId: 2,
      stageCode: 'GENERATE_ANSWER',
      status: 'failed',
      inputSummary: '使用检索片段生成回答',
      outputSummary: null,
      errorMessage: '模型超时',
      startedAt: '2026-06-20T10:30:20',
      finishedAt: '2026-06-20T10:31:12',
      durationMs: 52000,
    },
  ],
  modelCalls: [
    {
      id: 101,
      stageId: 2,
      provider: 'openai',
      model: 'gpt-4.1',
      callType: 'chat',
      promptSummary: '请根据退款政策回答用户问题',
      outputSummary: '生成回答失败',
      inputTokens: 120,
      outputTokens: 80,
      latencyMs: 1500,
      status: 'failed',
      errorMessage: 'timeout',
      metadata: { temperature: 0.2 },
      createdAt: '2026-06-20T10:30:25',
    },
  ],
  retrievals: [
    {
      id: 201,
      stageId: 1,
      subQuestionNo: 1,
      channel: 'vector',
      queryText: '退款政策',
      filters: { knowledgeBaseIds: [10, 11] },
      resultCount: 2,
      selectedCount: 1,
      latencyMs: 300,
      createdAt: '2026-06-20T10:30:05',
      items: [
        {
          id: 301,
          documentId: 88,
          chunkId: 501,
          rankNo: 1,
          rawScore: 0.91,
          fusedScore: 0.88,
          selected: true,
          metadata: { title: '退款政策' },
          createdAt: '2026-06-20T10:30:06',
        },
      ],
    },
  ],
  reranks: [
    {
      id: 401,
      provider: 'cohere',
      model: 'rerank-v3.5',
      enabled: true,
      skippedReason: null,
      inputCount: 12,
      outputCount: 5,
      latencyMs: 480,
      status: 'success',
      errorMessage: null,
      metadata: { topN: 5 },
      createdAt: '2026-06-20T10:30:10',
    },
  ],
}

const agentRunDetail: AgentRunDetail = {
  summary: {
    runId: 9001,
    sessionId: 42,
    exchangeId: 5001,
    status: 'completed',
    memoryStrategy: 'sliding',
    routeReason: '需要调用工具确认退款政策',
    modelStepCount: 2,
    toolCallCount: 1,
    latestCheckpointNo: 3,
    errorMessage: null,
    startedAt: '2026-06-20T10:30:20',
    finishedAt: '2026-06-20T10:31:12',
  },
  steps: [
    {
      id: 7001,
      stepNo: 1,
      phase: 'plan',
      status: 'success',
      decisionSummary: 'Plan refund action',
      observationSummary: '准备搜索退款政策',
      selectedToolId: 'search_policy',
      selectedToolReason: '需要查找政策原文',
      errorMessage: null,
      metadata: {},
      startedAt: '2026-06-20T10:30:21',
      finishedAt: '2026-06-20T10:30:30',
    },
  ],
  checkpoints: [
    {
      id: 8001,
      checkpointNo: 3,
      stepId: 7001,
      checkpointType: 'human_review',
      stable: true,
      payload: { approved: true },
      createdAt: '2026-06-20T10:30:40',
    },
  ],
  toolCalls: [
    {
      id: 90001,
      agentRunId: 9001,
      toolId: 'search_policy_tool',
      pluginId: 12,
      pluginVersion: '1.0.0',
      requestSummary: '搜索退款政策',
      responseSummary: '工具返回退款政策',
      status: 'success',
      latencyMs: 640,
      errorMessage: null,
      metadata: {},
      createdAt: '2026-06-20T10:30:35',
    },
  ],
}

function envelope<T>(data: T): ApiEnvelope<T> {
  return {
    success: true,
    code: 'OK',
    message: 'ok',
    data,
    traceId: 'test-trace',
  }
}

function renderTraceDetail(exchangeId = '5001') {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/traces/${exchangeId}`]}>
        <Routes>
          <Route path="/traces/:exchangeId" element={<TraceDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('TraceDetailPage', () => {
  beforeEach(() => {
    vi.mocked(getAdminTrace).mockResolvedValue(envelope(traceDetail))
    vi.mocked(getAgentRunDetail).mockResolvedValue(envelope(agentRunDetail))
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('shows the trace timeline, selected stage inspector, retrievals, model calls, and reranks', async () => {
    renderTraceDetail()

    expect(await screen.findByText('Trace #5001')).toBeTruthy()
    expect(screen.getByText('会话 #42')).toBeTruthy()
    expect(screen.getByText('Agent Run #9001')).toBeTruthy()

    const inspector = screen.getByRole('complementary', { name: '阶段详情' })
    expect(within(inspector).getByText('RETRIEVE')).toBeTruthy()
    expect(within(inspector).getByText('检索到 2 条候选片段')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: /GENERATE_ANSWER/ }))

    expect(within(inspector).getByText('GENERATE_ANSWER')).toBeTruthy()
    expect(within(inspector).getByText('模型超时')).toBeTruthy()

    expect(screen.getByText('关联检索')).toBeTruthy()
    expect(screen.getByText('退款政策')).toBeTruthy()
    expect(screen.getByText('Document #88')).toBeTruthy()
    expect(screen.getByText('Chunk #501')).toBeTruthy()

    expect(screen.getByText('模型调用')).toBeTruthy()
    expect(screen.getByText('gpt-4.1')).toBeTruthy()
    expect(screen.getByText('请根据退款政策回答用户问题')).toBeTruthy()
    expect(screen.getByText('输入 120 / 输出 80')).toBeTruthy()

    expect(screen.getByText('Rerank')).toBeTruthy()
    expect(screen.getByText('rerank-v3.5')).toBeTruthy()
    expect(screen.getByText('输入 12 / 输出 5')).toBeTruthy()
  })

  it('loads the linked agent run and shows steps, checkpoints, and tool calls', async () => {
    renderTraceDetail()

    expect(await screen.findByText('Trace #5001')).toBeTruthy()
    expect(vi.mocked(getAgentRunDetail)).toHaveBeenCalledWith(9001)

    const panel = await screen.findByRole('region', { name: 'Agent Run 详情' })
    expect(within(panel).getByText('模型步数 2')).toBeTruthy()
    expect(within(panel).getByText('工具调用 1')).toBeTruthy()
    expect(within(panel).getByText('Plan refund action')).toBeTruthy()
    expect(within(panel).getByText('search_policy')).toBeTruthy()
    expect(within(panel).getByText('Checkpoint #3')).toBeTruthy()
    expect(within(panel).getByText('human_review')).toBeTruthy()
    expect(within(panel).getByText('search_policy_tool')).toBeTruthy()
    expect(within(panel).getByText('工具返回退款政策')).toBeTruthy()
  })

  it('keeps the detail page useful when optional trace sections are empty', async () => {
    vi.mocked(getAdminTrace).mockResolvedValue(
      envelope({
        ...traceDetail,
        stages: [],
        retrievals: [],
        modelCalls: [],
        reranks: [],
        agentRunId: null,
        agentRunStatus: null,
      }),
    )

    renderTraceDetail()

    expect(await screen.findByText('无阶段记录')).toBeTruthy()
    expect(screen.getByText('无 Agent Run 关联')).toBeTruthy()
    expect(vi.mocked(getAgentRunDetail)).not.toHaveBeenCalled()
    expect(screen.getByText('选择一个阶段查看详情。')).toBeTruthy()
    expect(screen.getByText('无检索记录')).toBeTruthy()
    expect(screen.getByText('无模型调用记录')).toBeTruthy()
    expect(screen.getByText('无 rerank 记录')).toBeTruthy()
  })

  it('keeps trace details visible when the linked agent run fails to load', async () => {
    vi.mocked(getAgentRunDetail).mockRejectedValue(new Error('agent run failed'))

    renderTraceDetail()

    expect(await screen.findByText('Trace #5001')).toBeTruthy()
    expect(screen.getByText('退款政策')).toBeTruthy()
    expect(await screen.findByText('加载 Agent Run 详情失败。')).toBeTruthy()
  })

  it('shows a page error when the trace detail request fails', async () => {
    vi.mocked(getAdminTrace).mockRejectedValue(new Error('network failed'))

    renderTraceDetail()

    expect(await screen.findByText('加载 Trace 详情失败。')).toBeTruthy()
  })
})
