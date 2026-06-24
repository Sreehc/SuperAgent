import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuditLogPage } from './AuditLogPage'
import { listAuditLogs } from '../api'
import type { ApiEnvelope } from '@/api/types'
import type { AuditLogItem, AuditPagedResult } from '../types'

vi.mock('../api', () => ({
  listAuditLogs: vi.fn(),
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

const auditLogs: AuditLogItem[] = [
  {
    id: 1001,
    actorId: 7,
    action: 'documents.reprocess.requested',
    resourceType: 'knowledge_document',
    resourceId: 55,
    detail: { reason: 'chunking profile changed', profileId: 3 },
    createdAt: '2026-06-20T12:10:00',
  },
  {
    id: 1002,
    actorId: null,
    action: 'tools.secret.updated',
    resourceType: 'tool_secret',
    resourceId: null,
    detail: { toolId: 'web.search', secretKey: 'tavilyApiKey', configured: true },
    createdAt: '2026-06-20T13:20:00',
  },
]

function renderAuditLogPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/audit-logs']}>
        <Routes>
          <Route path="/audit-logs" element={<AuditLogPage />} />
          <Route path="/documents/:documentId" element={<div>文档详情页</div>} />
          <Route path="/traces/:exchangeId" element={<div>Trace 详情页</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AuditLogPage', () => {
  beforeEach(() => {
    vi.mocked(listAuditLogs).mockResolvedValue(
      envelope<AuditPagedResult<AuditLogItem>>({
        items: auditLogs,
        page: 1,
        pageSize: 50,
        total: auditLogs.length,
      }),
    )
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('lists audit logs and filters by backend-supported fields', async () => {
    renderAuditLogPage()

    expect(await screen.findByTestId('audit-log-1001')).toBeTruthy()
    expect(screen.getByTestId('audit-log-1001').textContent).toContain('documents.reprocess.requested')
    expect(screen.getByTestId('audit-log-1002').textContent).toContain('tools.secret.updated')
    expect(vi.mocked(listAuditLogs)).toHaveBeenCalledWith({ pageSize: 50 })

    fireEvent.change(screen.getByLabelText('操作'), { target: { value: 'tools.secret.updated' } })
    fireEvent.change(screen.getByLabelText('资源类型'), { target: { value: 'tool_secret' } })
    fireEvent.change(screen.getByLabelText('资源 ID'), { target: { value: '55' } })
    fireEvent.change(screen.getByLabelText('操作者 ID'), { target: { value: '7' } })
    fireEvent.change(screen.getByLabelText('开始时间'), { target: { value: '2026-06-20T08:00' } })
    fireEvent.change(screen.getByLabelText('结束时间'), { target: { value: '2026-06-20T18:30' } })

    const expectedFrom = new Date('2026-06-20T08:00').toISOString()
    const expectedTo = new Date('2026-06-20T18:30').toISOString()

    await waitFor(() =>
      expect(vi.mocked(listAuditLogs)).toHaveBeenLastCalledWith({
        pageSize: 50,
        action: 'tools.secret.updated',
        resourceType: 'tool_secret',
        resourceId: 55,
        actorId: 7,
        from: expectedFrom,
        to: expectedTo,
      }),
    )
  })

  it('clears filters back to the default audit log query', async () => {
    renderAuditLogPage()

    await screen.findByTestId('audit-log-1001')
    fireEvent.change(screen.getByLabelText('操作'), { target: { value: 'settings.rag.updated' } })
    await waitFor(() =>
      expect(vi.mocked(listAuditLogs)).toHaveBeenLastCalledWith({
        pageSize: 50,
        action: 'settings.rag.updated',
        resourceType: undefined,
        resourceId: undefined,
        actorId: undefined,
        from: undefined,
        to: undefined,
      }),
    )

    fireEvent.click(screen.getByRole('button', { name: '清空筛选' }))

    await waitFor(() => expect(vi.mocked(listAuditLogs)).toHaveBeenLastCalledWith({ pageSize: 50 }))
    expect((screen.getByLabelText('操作') as HTMLInputElement).value).toBe('')
  })

  it('opens audit log details with a document resource link and JSON detail', async () => {
    renderAuditLogPage()

    const row = await screen.findByTestId('audit-log-1001')
    fireEvent.click(within(row).getByRole('button', { name: '查看审计 1001' }))

    const drawer = await screen.findByRole('dialog', { name: '审计日志 #1001' })
    expect(within(drawer).getAllByText('documents.reprocess.requested').length).toBeGreaterThan(0)
    expect(within(drawer).getAllByText('Actor #7').length).toBeGreaterThan(0)
    expect(within(drawer).getByText('knowledge_document #55')).toBeTruthy()
    expect(within(drawer).getByRole('link', { name: '打开文档 55' }).getAttribute('href')).toBe('/documents/55')
    expect(within(drawer).getByText(/"reason": "chunking profile changed"/)).toBeTruthy()
  })

  it('shows unavailable resource state instead of fake links', async () => {
    renderAuditLogPage()

    const row = await screen.findByTestId('audit-log-1002')
    fireEvent.click(within(row).getByRole('button', { name: '查看审计 1002' }))

    const drawer = await screen.findByRole('dialog', { name: '审计日志 #1002' })
    expect(within(drawer).getByText('系统')).toBeTruthy()
    expect(within(drawer).getAllByText('tool_secret').length).toBeGreaterThan(0)
    expect(within(drawer).getByText('资源不可用')).toBeTruthy()
    expect(within(drawer).queryByRole('link', { name: /打开/ })).toBeNull()
  })
})
