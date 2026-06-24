import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { KnowledgeListPage } from './KnowledgeListPage'
import { createKnowledgeBase, listKnowledgeBases } from '../api'
import type { ApiEnvelope } from '@/api/types'
import type { KnowledgeBaseListItem, PagedResult } from '../types'

vi.mock('../api', () => ({
  createKnowledgeBase: vi.fn(),
  listKnowledgeBases: vi.fn(),
}))

vi.mock('@/utils/toast', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}))

const knowledgeBases: KnowledgeBaseListItem[] = [
  {
    id: 1,
    name: '客户支持知识库',
    status: 'published',
    documentCount: 1234,
    updatedAt: '2026-06-20T16:30:00',
  },
  {
    id: 2,
    name: '售前草稿',
    status: 'draft',
    documentCount: 0,
    updatedAt: '',
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

function paged(items: KnowledgeBaseListItem[]): ApiEnvelope<PagedResult<KnowledgeBaseListItem>> {
  return envelope({
    items,
    page: 1,
    pageSize: 100,
    total: items.length,
  })
}

function LocationProbe() {
  const location = useLocation()
  return <output aria-label="当前路径">{location.pathname}</output>
}

function renderKnowledgeList() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/knowledge']}>
        <Routes>
          <Route path="/knowledge" element={<KnowledgeListPage />} />
          <Route path="/knowledge/:knowledgeBaseId" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('KnowledgeListPage', () => {
  beforeEach(() => {
    vi.mocked(listKnowledgeBases).mockResolvedValue(paged(knowledgeBases))
    vi.mocked(createKnowledgeBase).mockResolvedValue(envelope({
      id: 3,
      name: '产品知识库',
      status: 'draft',
      visibility: 'tenant',
    }))
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('renders readable status, document count, updated time, and row navigation', async () => {
    renderKnowledgeList()

    expect(await screen.findByText('客户支持知识库')).toBeTruthy()
    expect(screen.getByText('已发布')).toBeTruthy()
    expect(screen.getByText('草稿')).toBeTruthy()
    expect(screen.getByText('1,234')).toBeTruthy()
    expect(screen.getByText('2026/06/20 16:30')).toBeTruthy()
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
    expect(screen.queryByText('published')).toBeNull()

    fireEvent.click(screen.getByTestId('knowledge-row-1'))

    await waitFor(() => expect(screen.getByLabelText('当前路径').textContent).toBe('/knowledge/1'))
  })

  it('renders a clear empty state with the create action available', async () => {
    vi.mocked(listKnowledgeBases).mockResolvedValue(paged([]))

    renderKnowledgeList()

    expect(await screen.findByRole('status', { name: '暂无知识库' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '新建知识库' })).toBeTruthy()
  })

  it('creates a knowledge base with trimmed name and optional description', async () => {
    renderKnowledgeList()

    fireEvent.click(await screen.findByTestId('knowledge-create-open'))

    const dialog = screen.getByRole('dialog', { name: '新建知识库' })
    fireEvent.change(within(dialog).getByLabelText('名称'), { target: { value: '  产品知识库  ' } })
    fireEvent.change(within(dialog).getByLabelText('描述'), { target: { value: '  售前与支持 FAQ  ' } })
    fireEvent.click(within(dialog).getByTestId('knowledge-create-submit'))

    await waitFor(() =>
      expect(createKnowledgeBase).toHaveBeenCalledWith({
        name: '产品知识库',
        description: '售前与支持 FAQ',
      }),
    )
  })

  it('keeps the create dialog open and shows inline errors for invalid or failed submit', async () => {
    vi.mocked(createKnowledgeBase).mockRejectedValue(new Error('network failed'))

    renderKnowledgeList()

    fireEvent.click(await screen.findByTestId('knowledge-create-open'))

    const dialog = screen.getByRole('dialog', { name: '新建知识库' })
    fireEvent.click(within(dialog).getByTestId('knowledge-create-submit'))

    expect(await within(dialog).findByText('请输入知识库名称。')).toBeTruthy()
    expect(createKnowledgeBase).not.toHaveBeenCalled()

    fireEvent.change(within(dialog).getByLabelText('名称'), { target: { value: '失败知识库' } })
    fireEvent.click(within(dialog).getByTestId('knowledge-create-submit'))

    const alert = await within(dialog).findByRole('alert')
    expect(alert.textContent).toContain('创建知识库失败，请稍后重试。')
    expect(screen.getByRole('dialog', { name: '新建知识库' })).toBeTruthy()
    expect(within(dialog).getByLabelText('名称').getAttribute('value')).toBe('失败知识库')
  })
})
