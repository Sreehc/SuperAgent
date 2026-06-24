import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { EvalSuiteDetailPage } from './EvalSuiteDetailPage'
import { createEvalCase, createEvalRun, deleteEvalCase, getEvalSuite } from '../api'
import type { ApiEnvelope } from '@/api/types'
import type { EvalCase, EvalRun, EvalSuite, EvalSuiteDetail } from '../types'

vi.mock('../api', () => ({
  getEvalSuite: vi.fn(),
  createEvalCase: vi.fn(),
  createEvalRun: vi.fn(),
  deleteEvalCase: vi.fn(),
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

const suite: EvalSuite = {
  id: 301,
  tenantId: 2,
  suiteKey: 'refund-regression',
  name: '退款问答回归',
  description: '覆盖退款政策纠错',
  caseCount: 1,
  runCount: 1,
  createdAt: '2026-06-19T10:00:00',
  updatedAt: '2026-06-20T10:00:00',
}

const evalCase: EvalCase = {
  id: 7001,
  suiteId: 301,
  caseKey: 'feedback-9001',
  input: { question: '退款政策是什么？' },
  expected: { correction: '应使用 2026 年退款政策' },
  createdAt: '2026-06-20T10:00:00',
  updatedAt: '2026-06-20T10:00:00',
}

const run: EvalRun = {
  id: 901,
  suiteId: 301,
  suiteKey: 'refund-regression',
  status: 'success',
  passedCount: 8,
  failedCount: 1,
  report: {},
  createdAt: '2026-06-20T12:00:00',
  updatedAt: '2026-06-20T12:02:00',
  finishedAt: '2026-06-20T12:02:00',
}

const detail: EvalSuiteDetail = {
  suite,
  cases: [evalCase],
  recentRuns: [run],
}

function renderSuiteDetail() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/evals/301']}>
        <Routes>
          <Route path="/evals/:suiteId" element={<EvalSuiteDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('EvalSuiteDetailPage', () => {
  beforeEach(() => {
    vi.mocked(getEvalSuite).mockResolvedValue(envelope(detail))
    vi.mocked(createEvalCase).mockResolvedValue({
      data: envelope<EvalCase>({
        id: 7002,
        suiteId: 301,
        caseKey: 'feedback-9002',
        input: { question: '物流多久到？' },
        expected: { answer: '使用物流 SLA' },
        createdAt: '2026-06-21T10:00:00',
        updatedAt: '2026-06-21T10:00:00',
      }),
    } as never)
    vi.mocked(deleteEvalCase).mockResolvedValue({
      data: envelope({ deleted: true }),
    } as never)
    vi.mocked(createEvalRun).mockResolvedValue({
      data: envelope<EvalRun>({
        id: 902,
        suiteId: 301,
        suiteKey: 'refund-regression',
        status: 'running',
        passedCount: 0,
        failedCount: 0,
        report: { source: 'manual-ui' },
        createdAt: '2026-06-21T11:00:00',
        updatedAt: '2026-06-21T11:00:00',
        finishedAt: null,
      }),
    } as never)
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('shows suite cases and recent runs', async () => {
    renderSuiteDetail()

    expect(await screen.findByText('退款问答回归')).toBeTruthy()
    const caseRow = await screen.findByTestId('eval-case-row-7001')
    expect(caseRow.textContent).toContain('feedback-9001')
    expect(caseRow.textContent).toContain('退款政策是什么？')
    expect(caseRow.textContent).toContain('应使用 2026 年退款政策')

    const runRow = screen.getByTestId('eval-run-row-901')
    expect(runRow.textContent).toContain('success')
    expect(runRow.textContent).toContain('8')
    expect(runRow.textContent).toContain('1')
  })

  it('adds an eval case and refreshes the suite detail', async () => {
    renderSuiteDetail()

    fireEvent.click(await screen.findByRole('button', { name: '添加评测用例' }))
    const dialog = await screen.findByRole('dialog', { name: '添加评测用例' })

    fireEvent.change(within(dialog).getByLabelText('Case Key'), { target: { value: ' feedback-9002 ' } })
    fireEvent.change(within(dialog).getByLabelText('Input JSON'), { target: { value: '{ "question": "物流多久到？" }' } })
    fireEvent.change(within(dialog).getByLabelText('Expected JSON'), { target: { value: '{ "answer": "使用物流 SLA" }' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '添加用例' }))

    await waitFor(() =>
      expect(vi.mocked(createEvalCase)).toHaveBeenCalledWith(301, {
        caseKey: 'feedback-9002',
        input: { question: '物流多久到？' },
        expected: { answer: '使用物流 SLA' },
      }),
    )
    await waitFor(() => expect(vi.mocked(getEvalSuite).mock.calls.length).toBeGreaterThan(1))
    expect(await screen.findByText('评测用例已添加。')).toBeTruthy()
  })

  it('keeps the add dialog open on JSON validation or create failure', async () => {
    vi.mocked(createEvalCase).mockRejectedValueOnce(new Error('forbidden'))
    renderSuiteDetail()

    fireEvent.click(await screen.findByRole('button', { name: '添加评测用例' }))
    let dialog = await screen.findByRole('dialog', { name: '添加评测用例' })

    fireEvent.change(within(dialog).getByLabelText('Case Key'), { target: { value: 'feedback-broken' } })
    fireEvent.change(within(dialog).getByLabelText('Input JSON'), { target: { value: '{ broken' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '添加用例' }))

    expect(await within(dialog).findByText('Input JSON 或 Expected JSON 格式不正确。')).toBeTruthy()
    expect(vi.mocked(createEvalCase)).not.toHaveBeenCalled()

    fireEvent.change(within(dialog).getByLabelText('Input JSON'), { target: { value: '{ "question": "坏例子" }' } })
    fireEvent.change(within(dialog).getByLabelText('Expected JSON'), { target: { value: '{ "answer": "失败后保留" }' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '添加用例' }))

    expect(await screen.findByText('评测用例创建失败，请检查权限或稍后重试。')).toBeTruthy()
    dialog = screen.getByRole('dialog', { name: '添加评测用例' })
    expect((within(dialog).getByLabelText('Case Key') as HTMLInputElement).value).toBe('feedback-broken')
    expect((within(dialog).getByLabelText('Input JSON') as HTMLTextAreaElement).value).toBe('{ "question": "坏例子" }')
  })

  it('deletes an eval case after confirmation and refreshes detail', async () => {
    renderSuiteDetail()

    fireEvent.click(await screen.findByRole('button', { name: '删除用例 feedback-9001' }))
    const confirm = await screen.findByRole('dialog', { name: '删除用例 feedback-9001' })
    expect(vi.mocked(deleteEvalCase)).not.toHaveBeenCalled()

    fireEvent.click(within(confirm).getByRole('button', { name: '删除用例' }))

    await waitFor(() => expect(vi.mocked(deleteEvalCase)).toHaveBeenCalledWith(7001))
    await waitFor(() => expect(vi.mocked(getEvalSuite).mock.calls.length).toBeGreaterThan(1))
  })

  it('starts an eval run and refreshes the suite detail', async () => {
    renderSuiteDetail()

    fireEvent.click(await screen.findByRole('button', { name: '发起评测运行' }))
    const confirm = await screen.findByRole('dialog', { name: '发起评测运行' })
    fireEvent.click(within(confirm).getByRole('button', { name: '发起运行' }))

    await waitFor(() =>
      expect(vi.mocked(createEvalRun)).toHaveBeenCalledWith(301, {
        status: 'running',
        passedCount: 0,
        failedCount: 0,
        report: { source: 'manual-ui' },
      }),
    )
    await waitFor(() => expect(vi.mocked(getEvalSuite).mock.calls.length).toBeGreaterThan(1))
    expect(await screen.findByText('评测运行已发起。')).toBeTruthy()
  })

  it('keeps the run confirmation open on start failure', async () => {
    vi.mocked(createEvalRun).mockRejectedValueOnce(new Error('forbidden'))
    renderSuiteDetail()

    fireEvent.click(await screen.findByRole('button', { name: '发起评测运行' }))
    const confirm = await screen.findByRole('dialog', { name: '发起评测运行' })
    fireEvent.click(within(confirm).getByRole('button', { name: '发起运行' }))

    expect(await screen.findByText('评测运行发起失败，请检查权限或稍后重试。')).toBeTruthy()
    expect(screen.getByRole('dialog', { name: '发起评测运行' })).toBeTruthy()
  })

  it('keeps the delete confirmation open on failure', async () => {
    vi.mocked(deleteEvalCase).mockRejectedValueOnce(new Error('forbidden'))
    renderSuiteDetail()

    fireEvent.click(await screen.findByRole('button', { name: '删除用例 feedback-9001' }))
    const confirm = await screen.findByRole('dialog', { name: '删除用例 feedback-9001' })
    fireEvent.click(within(confirm).getByRole('button', { name: '删除用例' }))

    expect(await screen.findByText('评测用例删除失败，请检查权限或稍后重试。')).toBeTruthy()
    expect(screen.getByRole('dialog', { name: '删除用例 feedback-9001' })).toBeTruthy()
  })
})
