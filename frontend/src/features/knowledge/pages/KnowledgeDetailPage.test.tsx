import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { KnowledgeDetailPage } from './KnowledgeDetailPage'
import {
  deleteKnowledgeDocument,
  getKnowledgeBase,
  listChunkingProfiles,
  listKnowledgeDocuments,
  listKnowledgeDomains,
  uploadKnowledgeDocument,
  uploadKnowledgeDocumentsBatch,
} from '../api'
import type { ApiEnvelope } from '@/api/types'
import type {
  ChunkingProfileItem,
  KnowledgeBaseDetail,
  KnowledgeDocumentListItem,
  KnowledgeDomainItem,
  PagedResult,
  UploadBatchDocumentResponse,
  UploadDocumentResponse,
} from '../types'

vi.mock('../api', () => ({
  deleteKnowledgeDocument: vi.fn(),
  getKnowledgeBase: vi.fn(),
  listChunkingProfiles: vi.fn(),
  listKnowledgeDocuments: vi.fn(),
  listKnowledgeDomains: vi.fn(),
  uploadKnowledgeDocument: vi.fn(),
  uploadKnowledgeDocumentsBatch: vi.fn(),
}))

vi.mock('@/utils/toast', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}))

const knowledgeBase: KnowledgeBaseDetail = {
  id: 42,
  name: '客户支持知识库',
  description: '客服问答与流程文档',
  visibility: 'tenant',
  status: 'published',
  documentCount: 0,
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

function paged(items: KnowledgeDocumentListItem[]): ApiEnvelope<PagedResult<KnowledgeDocumentListItem>> {
  return envelope({
    items,
    page: 1,
    pageSize: 100,
    total: items.length,
  })
}

function uploadItem(id: number, title: string, status: string): UploadDocumentResponse {
  return {
    id,
    knowledgeBaseId: 42,
    title,
    status,
    taskId: id + 100,
    knowledgeDomainId: null,
    chunkingProfileId: null,
    activeVersionNo: 1,
  }
}

function documentItem(
  id: number,
  title: string,
  fileType: string,
  status: string,
): KnowledgeDocumentListItem {
  return {
    id,
    title,
    fileType,
    status,
    fileSize: 1024,
    chunkCount: status === 'ready' ? 8 : 0,
    updatedAt: '2026-06-20T16:30:00',
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

function renderKnowledgeDetail() {
  return render(
    <MemoryRouter initialEntries={['/knowledge/42']}>
      <Routes>
        <Route path="/knowledge/:knowledgeBaseId" element={<KnowledgeDetailPage />} />
        <Route path="/documents/:documentId" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

function LocationProbe() {
  const location = useLocation()
  return <output aria-label="当前路径">{location.pathname}</output>
}

describe('KnowledgeDetailPage upload queue', () => {
  beforeEach(() => {
    vi.mocked(getKnowledgeBase).mockResolvedValue(envelope(knowledgeBase))
    vi.mocked(listKnowledgeDocuments).mockResolvedValue(paged([]))
    vi.mocked(listKnowledgeDomains).mockResolvedValue(envelope([] as KnowledgeDomainItem[]))
    vi.mocked(listChunkingProfiles).mockResolvedValue(envelope([] as ChunkingProfileItem[]))
    vi.mocked(uploadKnowledgeDocument).mockResolvedValue(envelope(uploadItem(11, 'refund-guide.txt', 'submitted')))
    vi.mocked(uploadKnowledgeDocumentsBatch).mockResolvedValue(
      envelope({
        uploadedCount: 0,
        items: [],
      }),
    )
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('shows a per-file batch queue from queued to uploading and final parsing states', async () => {
    const batchUpload = deferred<ApiEnvelope<UploadBatchDocumentResponse>>()
    vi.mocked(uploadKnowledgeDocumentsBatch).mockReturnValue(batchUpload.promise)
    renderKnowledgeDetail()

    const input = (await screen.findByTestId('document-upload-file')) as HTMLInputElement
    const refundFile = new File(['refund'], 'refund-guide.txt', { type: 'text/plain' })
    const policyFile = new File(['policy'], 'policy.pdf', { type: 'application/pdf' })
    const brokenFile = new File(['broken'], 'broken.csv', { type: 'text/csv' })

    expect(input.multiple).toBe(true)

    fireEvent.change(input, { target: { files: [refundFile, policyFile, brokenFile] } })

    const queue = screen.getByTestId('upload-queue')
    expect(within(queue).getByText('上传队列')).toBeTruthy()
    expect(within(queue).getByText('refund-guide.txt')).toBeTruthy()
    expect(within(queue).getByText('policy.pdf')).toBeTruthy()
    expect(within(queue).getByText('broken.csv')).toBeTruthy()
    expect(within(queue).getAllByText('等待上传')).toHaveLength(3)

    fireEvent.click(within(queue).getAllByRole('button', { name: '移除' })[2])
    await waitFor(() => expect(within(queue).queryByText('broken.csv')).toBeNull())

    fireEvent.click(screen.getByTestId('document-upload-submit'))

    await waitFor(() =>
      expect(uploadKnowledgeDocumentsBatch).toHaveBeenCalledWith(42, {
        files: [refundFile, policyFile],
        knowledgeDomainId: null,
        chunkingProfileId: null,
      }),
    )
    expect(within(queue).getAllByText('上传中')).toHaveLength(2)

    await act(async () => {
      batchUpload.resolve(
        envelope({
          uploadedCount: 2,
          items: [
            uploadItem(21, 'refund-guide.txt', 'processing'),
            uploadItem(22, 'policy.pdf', 'ready'),
          ],
        }),
      )
    })

    await waitFor(() => expect(within(queue).getByText('解析中')).toBeTruthy())
    expect(within(queue).getByText('已就绪')).toBeTruthy()
  })

  it('keeps single-file uploads on the single document endpoint with a trimmed title', async () => {
    renderKnowledgeDetail()

    const input = (await screen.findByTestId('document-upload-file')) as HTMLInputElement
    const file = new File(['refund'], 'refund-guide.txt', { type: 'text/plain' })

    fireEvent.change(screen.getByLabelText('标题（可选）'), { target: { value: '  退款指南  ' } })
    fireEvent.change(input, { target: { files: [file] } })
    fireEvent.click(screen.getByTestId('document-upload-submit'))

    await waitFor(() =>
      expect(uploadKnowledgeDocument).toHaveBeenCalledWith(42, {
        file,
        title: '退款指南',
        knowledgeDomainId: null,
        chunkingProfileId: null,
      }),
    )
    expect(uploadKnowledgeDocumentsBatch).not.toHaveBeenCalled()

    const queue = screen.getByTestId('upload-queue')
    await waitFor(() => expect(within(queue).getByText('已提交')).toBeTruthy())
  })

  it('marks queued files as failed and keeps them visible when upload fails', async () => {
    vi.mocked(uploadKnowledgeDocument).mockRejectedValue(new Error('network failed'))
    renderKnowledgeDetail()

    const input = (await screen.findByTestId('document-upload-file')) as HTMLInputElement
    const file = new File(['refund'], 'refund-guide.txt', { type: 'text/plain' })

    fireEvent.change(input, { target: { files: [file] } })
    fireEvent.click(screen.getByTestId('document-upload-submit'))

    const queue = screen.getByTestId('upload-queue')
    const alert = await screen.findByRole('alert')

    expect(alert.textContent).toContain('上传失败，请稍后重试。')
    expect(within(queue).getByText('refund-guide.txt')).toBeTruthy()
    expect(within(queue).getByText('失败')).toBeTruthy()
  })
})

describe('KnowledgeDetailPage document list filters and actions', () => {
  const documents = [
    documentItem(31, '退款政策', 'txt', 'ready'),
    documentItem(32, '安装手册', 'pdf', 'processing'),
    documentItem(33, '错误样例', 'csv', 'failed'),
  ]

  beforeEach(() => {
    vi.mocked(getKnowledgeBase).mockResolvedValue(envelope(knowledgeBase))
    vi.mocked(listKnowledgeDocuments).mockResolvedValue(paged(documents))
    vi.mocked(listKnowledgeDomains).mockResolvedValue(envelope([] as KnowledgeDomainItem[]))
    vi.mocked(listChunkingProfiles).mockResolvedValue(envelope([] as ChunkingProfileItem[]))
    vi.mocked(deleteKnowledgeDocument).mockResolvedValue({} as never)
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('filters documents by search keyword, status, and file type', async () => {
    renderKnowledgeDetail()

    expect(await screen.findByTestId('document-row-31')).toBeTruthy()
    expect(screen.getByTestId('document-row-32')).toBeTruthy()
    expect(screen.getByTestId('document-row-33')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('搜索文档'), { target: { value: '安装' } })

    expect(screen.queryByTestId('document-row-31')).toBeNull()
    expect(screen.getByTestId('document-row-32')).toBeTruthy()
    expect(screen.queryByTestId('document-row-33')).toBeNull()

    fireEvent.change(screen.getByLabelText('搜索文档'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('状态'), { target: { value: 'failed' } })

    expect(screen.queryByTestId('document-row-31')).toBeNull()
    expect(screen.queryByTestId('document-row-32')).toBeNull()
    expect(screen.getByTestId('document-row-33')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('状态'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('文件类型'), { target: { value: 'pdf' } })

    expect(screen.queryByTestId('document-row-31')).toBeNull()
    expect(screen.getByTestId('document-row-32')).toBeTruthy()
    expect(screen.queryByTestId('document-row-33')).toBeNull()
  })

  it('supports keyboard navigation on document rows', async () => {
    renderKnowledgeDetail()

    const row = await screen.findByTestId('document-row-31')
    expect(row.getAttribute('tabindex')).toBe('0')

    fireEvent.keyDown(row, { key: 'Enter' })

    await waitFor(() => expect(screen.getByLabelText('当前路径').textContent).toBe('/documents/31'))
  })

  it('opens delete confirmation without triggering row navigation', async () => {
    renderKnowledgeDetail()

    await screen.findByTestId('document-row-31')
    fireEvent.click(within(screen.getByTestId('document-row-31')).getByRole('button', { name: '删除' }))

    expect(screen.queryByLabelText('当前路径')).toBeNull()
    const dialog = screen.getByRole('dialog', { name: '删除文档' })
    expect(within(dialog).getByText(/退款政策/)).toBeTruthy()

    fireEvent.click(within(dialog).getByRole('button', { name: '确认删除' }))

    await waitFor(() => expect(deleteKnowledgeDocument).toHaveBeenCalledWith(31))
  })
})
