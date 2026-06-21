import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { DocumentDetailPage } from './DocumentDetailPage'
import {
  getDocumentGraph,
  getKnowledgeDocument,
  listChunkingProfiles,
  listDocumentVersions,
  listKnowledgeDocumentChunks,
  listKnowledgeDocumentTasks,
  rebuildDocumentGraph,
  reprocessKnowledgeDocument,
} from '../api'
import { toast } from '@/utils/toast'
import type { ApiEnvelope } from '@/api/types'
import type {
  ChunkingProfileItem,
  DocumentChunkItem,
  DocumentGraphDetail,
  DocumentTaskItem,
  DocumentVersionItem,
  KnowledgeDocumentDetail,
  PagedResult,
} from '../types'

vi.mock('../api', () => ({
  getDocumentGraph: vi.fn(),
  getKnowledgeDocument: vi.fn(),
  listChunkingProfiles: vi.fn(),
  listDocumentVersions: vi.fn(),
  listKnowledgeDocumentChunks: vi.fn(),
  listKnowledgeDocumentTasks: vi.fn(),
  rebuildDocumentGraph: vi.fn(),
  reprocessKnowledgeDocument: vi.fn(),
}))

vi.mock('@/utils/toast', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}))

const documentDetail: KnowledgeDocumentDetail = {
  id: 88,
  knowledgeBaseId: 42,
  knowledgeDomainId: null,
  chunkingProfileId: null,
  activeVersionNo: 3,
  title: '退款政策',
  fileName: 'refund-policy.txt',
  fileType: 'txt',
  fileSize: 2048,
  status: 'ready',
  chunkCount: 2,
  errorMessage: null,
  parsedText: '退款政策解析文本。',
  metadata: {},
  createdAt: '2026-06-20T12:00:00',
  updatedAt: '2026-06-20T16:30:00',
}

const chunks: DocumentChunkItem[] = [
  {
    id: 501,
    chunkNo: 1,
    sectionTitle: '退款条件',
    content: '购买 7 天内可申请退款。',
    charCount: 128,
    tokenCount: 32,
    metadata: {},
    createdAt: '2026-06-20T13:00:00',
  },
]

const versions: DocumentVersionItem[] = [
  {
    id: 601,
    documentId: 88,
    versionNo: 3,
    chunkingProfileId: null,
    status: 'active',
    chunkCount: 2,
    graphSyncStatus: 'synced',
    createdAt: '2026-06-20T13:00:00',
    updatedAt: '2026-06-20T16:00:00',
  },
]

const graph: DocumentGraphDetail = {
  documentId: 88,
  versionNo: 3,
  documentGraphSyncStatus: 'synced',
  versionGraphSyncStatus: 'synced',
  nodes: [
    {
      id: 'node-refund',
      type: 'policy',
      label: '退款政策',
      metadata: {},
    },
  ],
  edges: [
    {
      sourceId: 'node-refund',
      targetId: 'node-window',
      type: 'mentions',
      metadata: {},
    },
  ],
}

const rebuiltGraph: DocumentGraphDetail = {
  ...graph,
  versionNo: 4,
  documentGraphSyncStatus: 'rebuilt',
  versionGraphSyncStatus: 'synced',
  nodes: [
    ...graph.nodes,
    {
      id: 'node-rebuild',
      type: 'event',
      label: '重建记录',
      metadata: {},
    },
  ],
}

const tasks: DocumentTaskItem[] = [
  {
    id: 701,
    taskType: 'parse',
    status: 'success',
    attemptCount: 1,
    inputSummary: null,
    outputSummary: '解析完成',
    errorMessage: null,
    startedAt: '2026-06-20T14:00:00',
    finishedAt: '2026-06-20T14:01:00',
    createdAt: '2026-06-20T13:59:00',
  },
]

const profiles: ChunkingProfileItem[] = [
  {
    id: 301,
    code: 'qa',
    name: '问答切块',
    strategy: 'semantic',
    isDefault: false,
    status: 'active',
    config: {},
    createdAt: '2026-06-20T12:00:00',
    updatedAt: '2026-06-20T12:00:00',
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

function paged(items: DocumentChunkItem[]): ApiEnvelope<PagedResult<DocumentChunkItem>> {
  return envelope({
    items,
    page: 1,
    pageSize: 100,
    total: items.length,
  })
}

function renderDocumentDetail() {
  return render(
    <MemoryRouter initialEntries={['/documents/88']}>
      <Routes>
        <Route path="/documents/:documentId" element={<DocumentDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

function activateTab(name: string) {
  const tab = screen.getByRole('tab', { name })
  fireEvent.mouseDown(tab, { button: 0, ctrlKey: false })
  fireEvent.click(tab)
}

describe('DocumentDetailPage tabs', () => {
  beforeEach(() => {
    vi.mocked(getKnowledgeDocument).mockResolvedValue(envelope(documentDetail))
    vi.mocked(listKnowledgeDocumentChunks).mockResolvedValue(paged(chunks))
    vi.mocked(listDocumentVersions).mockResolvedValue(envelope(versions))
    vi.mocked(getDocumentGraph).mockResolvedValue(envelope(graph))
    vi.mocked(listKnowledgeDocumentTasks).mockResolvedValue(envelope(tasks))
    vi.mocked(listChunkingProfiles).mockResolvedValue(envelope(profiles))
    vi.mocked(rebuildDocumentGraph).mockResolvedValue(envelope(rebuiltGraph))
    vi.mocked(reprocessKnowledgeDocument).mockResolvedValue(envelope({ documentId: 88, taskId: 900, status: 'pending' }))
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('renders document detail tabs and loads the data for each tab', async () => {
    renderDocumentDetail()

    expect(await screen.findByRole('tab', { name: '解析文本' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: 'Chunks' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '版本' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '图谱' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '任务日志' })).toBeTruthy()
    expect(screen.getByText('退款政策解析文本。')).toBeTruthy()

    activateTab('Chunks')
    let panel = screen.getByRole('tabpanel')
    expect(await within(panel).findByText('退款条件')).toBeTruthy()
    expect(within(panel).getByText('购买 7 天内可申请退款。')).toBeTruthy()
    expect(within(panel).getByText('32 tokens')).toBeTruthy()

    activateTab('版本')
    panel = screen.getByRole('tabpanel')
    expect(await within(panel).findByText('v3')).toBeTruthy()
    expect(within(panel).getByText('active')).toBeTruthy()
    expect(within(panel).getByText('synced')).toBeTruthy()

    activateTab('图谱')
    panel = screen.getByRole('tabpanel')
    expect(await within(panel).findByText('图谱版本 v3')).toBeTruthy()
    expect(within(panel).getByText('退款政策')).toBeTruthy()
    expect(within(panel).getByText('mentions')).toBeTruthy()

    activateTab('任务日志')
    panel = screen.getByRole('tabpanel')
    expect(await within(panel).findByText('解析完成')).toBeTruthy()

    await waitFor(() => expect(listKnowledgeDocumentChunks).toHaveBeenCalledWith(88, { pageSize: 100 }))
    expect(listDocumentVersions).toHaveBeenCalledWith(88)
    expect(getDocumentGraph).toHaveBeenCalledWith(88)
    expect(listKnowledgeDocumentTasks).toHaveBeenCalledWith(88)
  })

  it('keeps optional tab empty and error states local to their panels', async () => {
    vi.mocked(listKnowledgeDocumentChunks).mockResolvedValue(paged([]))
    vi.mocked(listDocumentVersions).mockResolvedValue(envelope([]))
    vi.mocked(getDocumentGraph).mockRejectedValue(new Error('graph failed'))
    vi.mocked(listKnowledgeDocumentTasks).mockResolvedValue(envelope([]))

    renderDocumentDetail()

    expect(await screen.findByText('退款政策解析文本。')).toBeTruthy()

    activateTab('Chunks')
    expect(await within(screen.getByRole('tabpanel')).findByRole('status', { name: '暂无 Chunks' })).toBeTruthy()

    activateTab('版本')
    expect(await within(screen.getByRole('tabpanel')).findByRole('status', { name: '暂无版本记录' })).toBeTruthy()

    activateTab('图谱')
    const graphAlert = await within(screen.getByRole('tabpanel')).findByRole('alert')
    expect(graphAlert.textContent).toContain('图谱加载失败')

    activateTab('任务日志')
    expect(await within(screen.getByRole('tabpanel')).findByRole('status', { name: '暂无任务记录' })).toBeTruthy()
  })

  it('requires confirmation with reason and chunking profile before reprocessing', async () => {
    renderDocumentDetail()

    fireEvent.click(await screen.findByRole('button', { name: '重处理' }))

    const dialog = screen.getByRole('dialog', { name: '重处理文档' })
    fireEvent.change(within(dialog).getByLabelText('重处理原因'), { target: { value: '  修复解析失败  ' } })
    fireEvent.change(within(dialog).getByLabelText('切块策略'), { target: { value: '301' } })

    expect(reprocessKnowledgeDocument).not.toHaveBeenCalled()

    fireEvent.click(within(dialog).getByRole('button', { name: '确认重处理' }))

    await waitFor(() =>
      expect(reprocessKnowledgeDocument).toHaveBeenCalledWith(88, {
        reason: '修复解析失败',
        chunkingProfileId: 301,
      }),
    )
    expect(toast.success).toHaveBeenCalledWith('已提交重处理任务。')
  })

  it('requires confirmation before rebuilding the document graph and refreshes graph data', async () => {
    vi.mocked(getDocumentGraph).mockResolvedValueOnce(envelope(graph)).mockResolvedValue(envelope(rebuiltGraph))

    renderDocumentDetail()

    expect(await screen.findByRole('tab', { name: '图谱' })).toBeTruthy()
    activateTab('图谱')
    expect(await within(screen.getByRole('tabpanel')).findByText('图谱版本 v3')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '重建图谱' }))

    const dialog = screen.getByRole('dialog', { name: '重建图谱' })
    expect(rebuildDocumentGraph).not.toHaveBeenCalled()

    fireEvent.click(within(dialog).getByRole('button', { name: '确认重建' }))

    await waitFor(() => expect(rebuildDocumentGraph).toHaveBeenCalledWith(88))
    expect(await within(screen.getByRole('tabpanel')).findByText('图谱版本 v4')).toBeTruthy()
    expect(toast.success).toHaveBeenCalledWith('图谱重建已提交。')
  })
})
