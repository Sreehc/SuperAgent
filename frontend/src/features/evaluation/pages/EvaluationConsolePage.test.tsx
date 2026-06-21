import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { EvaluationConsolePage } from './EvaluationConsolePage'
import { createEvalSuite, listEvalRuns, listEvalSuites } from '../api'
import type { ApiEnvelope } from '@/api/types'
import type { EvalPagedResult, EvalRun, EvalSuite } from '../types'

vi.mock('../api', () => ({
  listEvalSuites: vi.fn(),
  listEvalRuns: vi.fn(),
  createEvalSuite: vi.fn(),
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

const runs: EvalRun[] = [
  {
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
  },
]

function renderEvaluation() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  function LocationProbe() {
    const location = useLocation()
    return <output data-testid="location">{location.pathname}</output>
  }

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/evals']}>
        <Routes>
          <Route
            path="/evals"
            element={
              <>
                <LocationProbe />
                <EvaluationConsolePage />
              </>
            }
          />
          <Route path="/evals/:suiteId" element={<LocationProbe />} />
          <Route path="/evals/runs/:runId" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('EvaluationConsolePage', () => {
  beforeEach(() => {
    vi.mocked(listEvalSuites).mockResolvedValue(
      envelope<EvalPagedResult<EvalSuite>>({
        items: suites,
        page: 1,
        pageSize: 50,
        total: suites.length,
      }),
    )
    vi.mocked(listEvalRuns).mockResolvedValue(
      envelope<EvalPagedResult<EvalRun>>({
        items: runs,
        page: 1,
        pageSize: 50,
        total: runs.length,
      }),
    )
    vi.mocked(createEvalSuite).mockResolvedValue({
      data: envelope<EvalSuite>({
        id: 302,
        tenantId: 2,
        suiteKey: 'shipping-regression',
        name: '物流问答回归',
        description: '覆盖物流政策',
        caseCount: 0,
        runCount: 0,
        createdAt: '2026-06-21T10:00:00',
        updatedAt: '2026-06-21T10:00:00',
      }),
    } as never)
  })

  afterEach(() => {
    vi.useRealTimers()
    cleanup()
    vi.clearAllMocks()
  })

  it('lists eval suites and run records with row navigation', async () => {
    renderEvaluation()

    const suiteRow = await screen.findByTestId('eval-suite-row-301')
    expect(suiteRow.textContent).toContain('refund-regression')
    expect(suiteRow.textContent).toContain('退款问答回归')
    expect(suiteRow.textContent).toContain('2')

    fireEvent.click(suiteRow)
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/evals/301'))
  })

  it('creates an eval suite and refreshes the suite list', async () => {
    renderEvaluation()

    fireEvent.click(await screen.findByRole('button', { name: '新建评测套件' }))
    const dialog = await screen.findByRole('dialog', { name: '新建评测套件' })

    fireEvent.change(within(dialog).getByLabelText('Suite Key'), { target: { value: ' shipping-regression ' } })
    fireEvent.change(within(dialog).getByLabelText('名称'), { target: { value: ' 物流问答回归 ' } })
    fireEvent.change(within(dialog).getByLabelText('描述'), { target: { value: ' 覆盖物流政策 ' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '创建套件' }))

    await waitFor(() =>
      expect(vi.mocked(createEvalSuite)).toHaveBeenCalledWith({
        suiteKey: 'shipping-regression',
        name: '物流问答回归',
        description: '覆盖物流政策',
      }),
    )
    await waitFor(() => expect(vi.mocked(listEvalSuites).mock.calls.length).toBeGreaterThan(1))
    expect(await screen.findByText('评测套件已创建。')).toBeTruthy()
  })

  it('keeps the dialog open on validation or create failure', async () => {
    vi.mocked(createEvalSuite).mockRejectedValueOnce(new Error('forbidden'))
    renderEvaluation()

    fireEvent.click(await screen.findByRole('button', { name: '新建评测套件' }))
    let dialog = await screen.findByRole('dialog', { name: '新建评测套件' })

    fireEvent.click(within(dialog).getByRole('button', { name: '创建套件' }))
    expect(await within(dialog).findByText('请填写 Suite Key。')).toBeTruthy()
    expect(vi.mocked(createEvalSuite)).not.toHaveBeenCalled()

    fireEvent.change(within(dialog).getByLabelText('Suite Key'), { target: { value: 'broken-suite' } })
    fireEvent.change(within(dialog).getByLabelText('名称'), { target: { value: '失败套件' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '创建套件' }))

    expect(await screen.findByText('评测套件创建失败，请检查权限或稍后重试。')).toBeTruthy()
    dialog = screen.getByRole('dialog', { name: '新建评测套件' })
    expect((within(dialog).getByLabelText('Suite Key') as HTMLInputElement).value).toBe('broken-suite')
    expect((within(dialog).getByLabelText('名称') as HTMLInputElement).value).toBe('失败套件')
  })

  it('polls run records while a run is running', async () => {
    vi.useFakeTimers()
    vi.mocked(listEvalRuns).mockResolvedValue(
      envelope<EvalPagedResult<EvalRun>>({
        items: [
          {
            ...runs[0],
            id: 902,
            status: 'running',
            passedCount: 0,
            failedCount: 0,
            finishedAt: null,
          },
        ],
        page: 1,
        pageSize: 50,
        total: 1,
      }),
    )

    renderEvaluation()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(screen.getByText('running')).toBeTruthy()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })

    expect(vi.mocked(listEvalRuns).mock.calls.length).toBeGreaterThan(1)
  })
})
