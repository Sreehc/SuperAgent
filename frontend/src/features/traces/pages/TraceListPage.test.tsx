import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { TraceListPage } from './TraceListPage'
import { listAdminTraces } from '../api'
import type { ApiEnvelope } from '@/api/types'
import type { TraceListItem, TracePagedResult } from '../types'

vi.mock('../api', () => ({
  listAdminTraces: vi.fn(),
}))

const traces: TraceListItem[] = [
  {
    exchangeId: 1001,
    sessionId: 42,
    userId: 7,
    executionMode: 'RAG_QA',
    status: 'success',
    startedAt: '2026-06-20T10:30:00',
    finishedAt: '2026-06-20T10:31:00',
    durationMs: 60000,
  },
]

function envelope<T>(data: T): ApiEnvelope<T> {
  return {
    success: true,
    code: 'OK',
    message: 'ok',
    data,
    traceId: 'test-trace',
  }
}

function paged(items: TraceListItem[]): ApiEnvelope<TracePagedResult<TraceListItem>> {
  return envelope({
    items,
    page: 1,
    pageSize: 50,
    total: items.length,
  })
}

function renderTraceList() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/traces']}>
        <Routes>
          <Route path="/traces" element={<TraceListPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function lastTraceParams() {
  const calls = vi.mocked(listAdminTraces).mock.calls
  return calls[calls.length - 1]?.[0]
}

describe('TraceListPage filters', () => {
  beforeEach(() => {
    vi.mocked(listAdminTraces).mockResolvedValue(paged(traces))
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('passes status, execution mode, time range, session and user filters to the query', async () => {
    renderTraceList()

    expect(await screen.findByTestId('trace-row-1001')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('状态'), { target: { value: 'failed' } })
    fireEvent.change(screen.getByLabelText('执行模式'), { target: { value: 'RAG_QA' } })
    fireEvent.change(screen.getByLabelText('开始时间'), { target: { value: '2026-06-20T10:30' } })
    fireEvent.change(screen.getByLabelText('结束时间'), { target: { value: '2026-06-21T18:45' } })
    fireEvent.change(screen.getByLabelText('会话 ID'), { target: { value: ' 42 ' } })
    fireEvent.change(screen.getByLabelText('用户 ID'), { target: { value: ' 7 ' } })

    await waitFor(() =>
      expect(lastTraceParams()).toMatchObject({
        pageSize: 50,
        status: 'failed',
        executionMode: 'RAG_QA',
        from: new Date('2026-06-20T10:30').toISOString(),
        to: new Date('2026-06-21T18:45').toISOString(),
        sessionId: 42,
        userId: 7,
      }),
    )
  })

  it('refreshes with current filters and can clear them', async () => {
    renderTraceList()

    expect(await screen.findByTestId('trace-row-1001')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('状态'), { target: { value: 'success' } })
    await waitFor(() => expect(lastTraceParams()).toMatchObject({ status: 'success' }))

    const callsBeforeRefresh = vi.mocked(listAdminTraces).mock.calls.length
    fireEvent.click(screen.getByTestId('trace-refresh'))

    await waitFor(() => expect(vi.mocked(listAdminTraces).mock.calls.length).toBeGreaterThan(callsBeforeRefresh))
    expect(lastTraceParams()).toMatchObject({ pageSize: 50, status: 'success' })

    fireEvent.click(screen.getByRole('button', { name: '清空筛选' }))

    await waitFor(() => expect(lastTraceParams()).toEqual({ pageSize: 50 }))
  })

  it('shows a page error when loading traces fails', async () => {
    vi.mocked(listAdminTraces).mockRejectedValue(new Error('network failed'))

    renderTraceList()

    expect(await screen.findByText('加载 Trace 列表失败。')).toBeTruthy()
  })
})
