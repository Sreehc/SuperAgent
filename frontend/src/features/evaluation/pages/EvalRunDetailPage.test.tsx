import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { EvalRunDetailPage } from './EvalRunDetailPage'
import { getEvalRun, listEvalRunCases } from '../api'
import type { ApiEnvelope } from '@/api/types'
import type { EvalRun, EvalRunCase } from '../types'

vi.mock('../api', () => ({
  getEvalRun: vi.fn(),
  listEvalRunCases: vi.fn(),
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

const run: EvalRun = {
  id: 901,
  suiteId: 301,
  suiteKey: 'refund-regression',
  status: 'failed',
  passedCount: 8,
  failedCount: 1,
  report: {
    summary: '退款政策回归存在失败用例',
    model: 'gpt-4.1',
  },
  createdAt: '2026-06-20T12:00:00',
  updatedAt: '2026-06-20T12:02:00',
  finishedAt: '2026-06-20T12:02:00',
}

const runCases: EvalRunCase[] = [
  {
    id: 8001,
    runId: 901,
    caseId: 7001,
    caseKey: 'feedback-9001',
    status: 'failed',
    score: 0.31,
    actual: { answer: '沿用 2025 年退款政策', citations: [] },
    expected: { correction: '应使用 2026 年退款政策', mustCite: true },
    diff: { answer: '年份错误', citations: '缺少引用' },
    latencyMs: 1100,
    errorMessage: 'missing citation',
    createdAt: '2026-06-20T12:01:00',
  },
]

function renderRunDetail() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/evals/runs/901']}>
        <Routes>
          <Route path="/evals/runs/:runId" element={<EvalRunDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('EvalRunDetailPage', () => {
  beforeEach(() => {
    vi.mocked(getEvalRun).mockResolvedValue(envelope(run))
    vi.mocked(listEvalRunCases).mockResolvedValue(envelope(runCases))
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('shows run summary, case actual/expected/diff, errors, and report JSON', async () => {
    renderRunDetail()

    expect(await screen.findByText('refund-regression')).toBeTruthy()
    expect(screen.getByText('运行 #901')).toBeTruthy()
    expect(screen.getAllByText('failed').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('通过 8')).toBeTruthy()
    expect(screen.getByText('失败 1')).toBeTruthy()

    const row = await screen.findByTestId('eval-run-case-row-8001')
    expect(row.textContent).toContain('feedback-9001')
    expect(row.textContent).toContain('0.31')
    expect(row.textContent).toContain('1.1s')
    expect(row.textContent).toContain('missing citation')

    expect(within(row).getByText(/沿用 2025 年退款政策/)).toBeTruthy()
    expect(within(row).getByText(/应使用 2026 年退款政策/)).toBeTruthy()
    expect(within(row).getByText(/年份错误/)).toBeTruthy()
    expect(screen.getByText('错误详情')).toBeTruthy()
    expect(screen.getAllByText('missing citation').length).toBeGreaterThanOrEqual(2)

    const report = screen.getByRole('region', { name: '报告 JSON' })
    expect(within(report).getByText(/退款政策回归存在失败用例/)).toBeTruthy()
    expect(within(report).getByText(/gpt-4.1/)).toBeTruthy()
  })

  it('keeps run summary visible when case results fail to load', async () => {
    vi.mocked(listEvalRunCases).mockRejectedValueOnce(new Error('network'))

    renderRunDetail()

    expect(await screen.findByText('refund-regression')).toBeTruthy()
    expect(screen.getByText('运行 #901')).toBeTruthy()
    expect(screen.getByText('用例结果加载失败')).toBeTruthy()
    expect(screen.getByText('请刷新页面或稍后重试。')).toBeTruthy()
  })
})
