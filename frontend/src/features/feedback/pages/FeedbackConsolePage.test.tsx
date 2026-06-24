import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { FeedbackConsolePage } from './FeedbackConsolePage'
import { listAdminFeedbacks } from '../api'
import { listMessages } from '@/features/chat/api'
import { createEvalCase, listEvalSuites } from '@/features/evaluation/api'
import type { ApiEnvelope } from '@/api/types'
import type { ConversationFeedback, FeedbackPagedResult } from '../types'
import type { ConversationMessage, PagedResult } from '@/features/chat/types'
import type { EvalCase, EvalPagedResult, EvalSuite } from '@/features/evaluation/types'

vi.mock('../api', () => ({
  listAdminFeedbacks: vi.fn(),
}))

vi.mock('@/features/chat/api', () => ({
  listMessages: vi.fn(),
}))

vi.mock('@/features/evaluation/api', () => ({
  listEvalSuites: vi.fn(),
  createEvalCase: vi.fn(),
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

const feedbacks: ConversationFeedback[] = [
  {
    id: 101,
    sessionId: 42,
    exchangeId: 5001,
    messageId: 9001,
    actorUserId: 7,
    rating: 'correction',
    comment: '回答引用了过期条款',
    correction: '应使用 2026 年退款政策',
    metadata: { source: 'chat', severity: 'high' },
    createdAt: '2026-06-20T12:10:00',
    updatedAt: '2026-06-20T12:20:00',
  },
  {
    id: 102,
    sessionId: 43,
    exchangeId: null,
    messageId: 9002,
    actorUserId: 8,
    rating: 'down',
    comment: null,
    correction: null,
    metadata: {},
    createdAt: '2026-06-20T13:10:00',
    updatedAt: '2026-06-20T13:20:00',
  },
]

const messages: ConversationMessage[] = [
  {
    id: 9000,
    role: 'user',
    content: '退款政策是什么？',
    status: 'done',
    createdAt: '2026-06-20T12:09:00',
  },
  {
    id: 9001,
    role: 'assistant',
    content: '退款政策沿用 2025 年条款。',
    status: 'done',
    createdAt: '2026-06-20T12:10:00',
  },
]

const suites: EvalSuite[] = [
  {
    id: 301,
    tenantId: 2,
    suiteKey: 'refund-regression',
    name: '退款问答回归',
    description: '覆盖退款政策纠错',
    caseCount: 2,
    runCount: 1,
    createdAt: '2026-06-19T10:00:00',
    updatedAt: '2026-06-20T10:00:00',
  },
]

function renderFeedback() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/feedback']}>
        <Routes>
          <Route path="/feedback" element={<FeedbackConsolePage />} />
          <Route path="/chat/:sessionId" element={<div>会话详情页</div>} />
          <Route path="/traces/:exchangeId" element={<div>Trace 详情页</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('FeedbackConsolePage', () => {
  beforeEach(() => {
    vi.mocked(listAdminFeedbacks).mockResolvedValue(
      envelope<FeedbackPagedResult<ConversationFeedback>>({
        items: feedbacks,
        page: 1,
        pageSize: 50,
        total: feedbacks.length,
      }),
    )
    vi.mocked(listMessages).mockResolvedValue(
      envelope<PagedResult<ConversationMessage>>({
        items: messages,
        page: 1,
        pageSize: 50,
        total: messages.length,
      }),
    )
    vi.mocked(listEvalSuites).mockResolvedValue(
      envelope<EvalPagedResult<EvalSuite>>({
        items: suites,
        page: 1,
        pageSize: 50,
        total: suites.length,
      }),
    )
    vi.mocked(createEvalCase).mockResolvedValue({
      data: envelope<EvalCase>({
        id: 7001,
        suiteId: 301,
        caseKey: 'feedback-9001',
        input: {},
        expected: {},
        createdAt: '2026-06-20T14:00:00',
        updatedAt: '2026-06-20T14:00:00',
      }),
    } as never)
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('lists feedback and filters by rating', async () => {
    renderFeedback()

    expect(await screen.findByTestId('feedback-101')).toBeTruthy()
    expect(screen.getByTestId('feedback-101').textContent).toContain('更正')
    expect(screen.getByTestId('feedback-101').textContent).toContain('回答引用了过期条款')
    expect(screen.getByTestId('feedback-101').textContent).toContain('应使用 2026 年退款政策')
    expect(screen.getByTestId('feedback-102').textContent).toContain('不满意')

    fireEvent.change(screen.getByLabelText('评价类型'), { target: { value: 'correction' } })

    await waitFor(() =>
      expect(vi.mocked(listAdminFeedbacks)).toHaveBeenLastCalledWith({ rating: 'correction', pageSize: 50 }),
    )
  })

  it('opens feedback details with conversation, message and trace entries', async () => {
    renderFeedback()

    const row = await screen.findByTestId('feedback-101')
    fireEvent.click(within(row).getByRole('button', { name: '查看反馈 101' }))

    const drawer = await screen.findByRole('dialog', { name: '反馈 #101' })
    expect(within(drawer).getByText('回答引用了过期条款')).toBeTruthy()
    expect(within(drawer).getByText('应使用 2026 年退款政策')).toBeTruthy()
    expect(within(drawer).getByText('Session #42')).toBeTruthy()
    expect(within(drawer).getByText('Message #9001')).toBeTruthy()
    expect(within(drawer).getByText('Trace #5001')).toBeTruthy()
    expect(within(drawer).getByRole('link', { name: '打开会话 42' }).getAttribute('href')).toBe('/chat/42')
    expect(within(drawer).getByRole('link', { name: '打开 Trace 5001' }).getAttribute('href')).toBe('/traces/5001')
    expect(within(drawer).getByText('消息暂不支持直接跳转')).toBeTruthy()
    expect(within(drawer).getByText(/"severity": "high"/)).toBeTruthy()
  })

  it('shows unavailable trace state when feedback has no exchange id', async () => {
    renderFeedback()

    const row = await screen.findByTestId('feedback-102')
    fireEvent.click(within(row).getByRole('button', { name: '查看反馈 102' }))

    const drawer = await screen.findByRole('dialog', { name: '反馈 #102' })
    expect(within(drawer).getByText('Trace 不可用')).toBeTruthy()
    expect(within(drawer).queryByRole('link', { name: /打开 Trace/ })).toBeNull()
    expect(within(drawer).getByText('未填写评论')).toBeTruthy()
    expect(within(drawer).getByText('未提供更正建议')).toBeTruthy()
  })

  it('creates an editable eval case draft from correction feedback', async () => {
    renderFeedback()

    const row = await screen.findByTestId('feedback-101')
    fireEvent.click(within(row).getByRole('button', { name: '查看反馈 101' }))

    const drawer = await screen.findByRole('dialog', { name: '反馈 #101' })
    fireEvent.click(within(drawer).getByRole('button', { name: '生成评测用例' }))

    const dialog = await screen.findByRole('dialog', { name: '生成评测用例' })
    expect(vi.mocked(listMessages)).toHaveBeenCalledWith(42)
    expect(vi.mocked(listEvalSuites)).toHaveBeenCalledWith({ pageSize: 50 })
    expect((within(dialog).getByLabelText('目标套件') as HTMLSelectElement).value).toBe('301')
    expect((within(dialog).getByLabelText('Case Key') as HTMLInputElement).value).toBe('feedback-9001')
    expect((within(dialog).getByLabelText('Input JSON') as HTMLTextAreaElement).value).toBe(
      JSON.stringify(
        {
          question: '退款政策是什么？',
          answer: '退款政策沿用 2025 年条款。',
          sessionId: 42,
          messageId: 9001,
          exchangeId: 5001,
          references: [],
        },
        null,
        2,
      ),
    )
    expect((within(dialog).getByLabelText('Expected JSON') as HTMLTextAreaElement).value).toBe(
      JSON.stringify(
        {
          correction: '应使用 2026 年退款政策',
          rating: 'correction',
          criteria: '回答应符合纠错建议，并避免重复原错误。',
        },
        null,
        2,
      ),
    )

    fireEvent.change(within(dialog).getByLabelText('Case Key'), { target: { value: 'refund-policy-correction' } })
    fireEvent.change(within(dialog).getByLabelText('Expected JSON'), {
      target: {
        value: JSON.stringify({ correction: '应使用 2026 年退款政策', rating: 'correction', criteria: '必须引用 2026 版本' }),
      },
    })
    fireEvent.click(within(dialog).getByRole('button', { name: '创建评测用例' }))

    await waitFor(() =>
      expect(vi.mocked(createEvalCase)).toHaveBeenCalledWith(301, {
        caseKey: 'refund-policy-correction',
        input: {
          question: '退款政策是什么？',
          answer: '退款政策沿用 2025 年条款。',
          sessionId: 42,
          messageId: 9001,
          exchangeId: 5001,
          references: [],
        },
        expected: {
          correction: '应使用 2026 年退款政策',
          rating: 'correction',
          criteria: '必须引用 2026 版本',
        },
      }),
    )
    expect(await screen.findByText('评测用例已创建。')).toBeTruthy()
  })

  it('only exposes eval draft creation for correction feedback and warns when source messages are incomplete', async () => {
    vi.mocked(listMessages).mockResolvedValueOnce(
      envelope<PagedResult<ConversationMessage>>({
        items: [],
        page: 1,
        pageSize: 50,
        total: 0,
      }),
    )
    renderFeedback()

    fireEvent.click(within(await screen.findByTestId('feedback-102')).getByRole('button', { name: '查看反馈 102' }))
    const downDrawer = await screen.findByRole('dialog', { name: '反馈 #102' })
    expect(within(downDrawer).queryByRole('button', { name: '生成评测用例' })).toBeNull()

    fireEvent.click(screen.getByLabelText('关闭反馈 #102'))
    fireEvent.click(within(await screen.findByTestId('feedback-101')).getByRole('button', { name: '查看反馈 101' }))
    const correctionDrawer = await screen.findByRole('dialog', { name: '反馈 #101' })
    fireEvent.click(within(correctionDrawer).getByRole('button', { name: '生成评测用例' }))

    const dialog = await screen.findByRole('dialog', { name: '生成评测用例' })
    expect(within(dialog).getByText('未能从会话消息中补齐原问题或原回答，请在草稿中手动补全。')).toBeTruthy()
    expect((within(dialog).getByLabelText('Input JSON') as HTMLTextAreaElement).value).toBe(
      JSON.stringify(
        {
          question: '',
          answer: '',
          sessionId: 42,
          messageId: 9001,
          exchangeId: 5001,
          references: [],
        },
        null,
        2,
      ),
    )
  })
})
