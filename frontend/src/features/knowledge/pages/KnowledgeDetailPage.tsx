import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  deleteKnowledgeDocument,
  getKnowledgeBase,
  listChunkingProfiles,
  listKnowledgeDocuments,
  listKnowledgeDomains,
  uploadKnowledgeDocument,
  uploadKnowledgeDocumentsBatch,
} from '../api'
import type {
  ChunkingProfileItem,
  KnowledgeBaseDetail,
  KnowledgeDocumentListItem,
  KnowledgeDomainItem,
  UploadDocumentResponse,
} from '../types'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'
import { Button } from '../../../shared/ui/button'
import { ConfirmDialog } from '../../../shared/ui/dialog'
import { toast } from '../../../utils/toast'
import { formatDateTime, formatFileSize } from '@/shared/lib/format'

const READY_POLL_INTERVAL = 2500
type UploadQueueStatus = 'queued' | 'uploading' | 'submitted' | 'processing' | 'ready' | 'failed'

interface UploadQueueItem {
  localId: string
  fileName: string
  fileSize: number
  status: UploadQueueStatus
  documentId?: number
  title?: string
  message?: string
}

const UPLOAD_STATUS_LABEL: Record<UploadQueueStatus, string> = {
  queued: '等待上传',
  uploading: '上传中',
  submitted: '已提交',
  processing: '解析中',
  ready: '已就绪',
  failed: '失败',
}

function statusBadge(status: string) {
  if (status === 'ready') return 'badge--success'
  if (status === 'failed' || status === 'error') return 'badge--danger'
  if (status === 'processing' || status === 'pending') return 'badge--warning'
  return ''
}

function uploadStatusBadge(status: UploadQueueStatus) {
  if (status === 'ready') return 'badge--success'
  if (status === 'failed') return 'badge--danger'
  if (status === 'processing') return 'badge--warning'
  if (status === 'uploading' || status === 'submitted') return 'badge--accent'
  return ''
}

function normalizeUploadStatus(status: string | undefined): UploadQueueStatus {
  if (status === 'ready') return 'ready'
  if (status === 'failed' || status === 'error') return 'failed'
  if (status === 'processing' || status === 'pending' || status === 'parsing') return 'processing'
  if (status === 'submitted') return 'submitted'
  return 'submitted'
}

function queueItemFromFile(file: File, index: number): UploadQueueItem {
  return {
    localId: `${file.name}-${file.size}-${file.lastModified}-${index}`,
    fileName: file.name,
    fileSize: file.size,
    status: 'queued',
  }
}

function queueItemFromUpload(item: UploadQueueItem, response: UploadDocumentResponse): UploadQueueItem {
  return {
    ...item,
    documentId: response.id,
    title: response.title,
    status: normalizeUploadStatus(response.status),
    message: undefined,
  }
}

export function KnowledgeDetailPage() {
  const params = useParams()
  const navigate = useNavigate()
  const knowledgeBaseId = Number(params.knowledgeBaseId)

  const [detail, setDetail] = useState<KnowledgeBaseDetail | null>(null)
  const [documents, setDocuments] = useState<KnowledgeDocumentListItem[]>([])
  const [documentSearch, setDocumentSearch] = useState('')
  const [documentStatusFilter, setDocumentStatusFilter] = useState('')
  const [documentTypeFilter, setDocumentTypeFilter] = useState('')
  const [domains, setDomains] = useState<KnowledgeDomainItem[]>([])
  const [profiles, setProfiles] = useState<ChunkingProfileItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [title, setTitle] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [uploadQueue, setUploadQueue] = useState<UploadQueueItem[]>([])
  const [uploadError, setUploadError] = useState('')
  const [domainId, setDomainId] = useState<number | null>(null)
  const [profileId, setProfileId] = useState<number | null>(null)
  const [uploading, setUploading] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<{ id: number; title: string } | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const pollRef = useRef<number | null>(null)

  const loadDocuments = useCallback(async () => {
    const response = await listKnowledgeDocuments(knowledgeBaseId, { pageSize: 100 })
    setDocuments(response.data.items)
    return response.data.items
  }, [knowledgeBaseId])

  const documentStatusOptions = useMemo(
    () => Array.from(new Set(documents.map((doc) => doc.status).filter(Boolean))).sort(),
    [documents],
  )
  const documentTypeOptions = useMemo(
    () => Array.from(new Set(documents.map((doc) => doc.fileType).filter(Boolean))).sort(),
    [documents],
  )
  const filteredDocuments = useMemo(() => {
    const keyword = documentSearch.trim().toLowerCase()
    return documents.filter((doc) => {
      const matchesKeyword = !keyword || doc.title.toLowerCase().includes(keyword) || doc.fileType.toLowerCase().includes(keyword)
      const matchesStatus = !documentStatusFilter || doc.status === documentStatusFilter
      const matchesType = !documentTypeFilter || doc.fileType === documentTypeFilter
      return matchesKeyword && matchesStatus && matchesType
    })
  }, [documentSearch, documentStatusFilter, documentTypeFilter, documents])

  useEffect(() => {
    let cancelled = false
    async function bootstrap() {
      setLoading(true)
      setError('')
      try {
        const [baseResponse] = await Promise.all([getKnowledgeBase(knowledgeBaseId)])
        if (cancelled) return
        setDetail(baseResponse.data)
        await loadDocuments()
        const [domainResponse, profileResponse] = await Promise.all([
          listKnowledgeDomains().catch(() => ({ data: [] as KnowledgeDomainItem[] })),
          listChunkingProfiles().catch(() => ({ data: [] as ChunkingProfileItem[] })),
        ])
        if (cancelled) return
        setDomains(domainResponse.data)
        setProfiles(profileResponse.data)
      } catch {
        if (!cancelled) setError('加载知识库失败。')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void bootstrap()
    return () => {
      cancelled = true
    }
  }, [knowledgeBaseId, loadDocuments])

  useEffect(() => {
    setUploadQueue((items) =>
      items.map((item) => {
        if (!item.documentId) return item
        const document = documents.find((doc) => doc.id === item.documentId)
        if (!document) return item
        const nextStatus = normalizeUploadStatus(document.status)
        return nextStatus === item.status ? item : { ...item, status: nextStatus }
      }),
    )
  }, [documents])

  // Poll while any document is still processing.
  useEffect(() => {
    const pending = documents.some((doc) => doc.status !== 'ready' && doc.status !== 'failed' && doc.status !== 'error')
    if (!pending) {
      if (pollRef.current) {
        window.clearTimeout(pollRef.current)
        pollRef.current = null
      }
      return
    }
    pollRef.current = window.setTimeout(() => {
      void loadDocuments()
    }, READY_POLL_INTERVAL)
    return () => {
      if (pollRef.current) {
        window.clearTimeout(pollRef.current)
        pollRef.current = null
      }
    }
  }, [documents, loadDocuments])

  function handleFilesChange(selectedFiles: FileList | null) {
    const nextFiles = Array.from(selectedFiles ?? [])
    setFiles(nextFiles)
    setUploadError('')
    setUploadQueue(nextFiles.map(queueItemFromFile))
  }

  async function handleUpload() {
    if (files.length === 0) {
      setUploadError('请选择要上传的文件。')
      toast.error('请选择要上传的文件。')
      return
    }
    setUploading(true)
    setUploadError('')
    setUploadQueue((items) => items.map((item) => ({ ...item, status: 'uploading', message: undefined })))
    try {
      if (files.length === 1) {
        const response = await uploadKnowledgeDocument(knowledgeBaseId, {
          file: files[0],
          title: title.trim() || undefined,
          knowledgeDomainId: domainId,
          chunkingProfileId: profileId,
        })
        setUploadQueue((items) => items.map((item, index) => (index === 0 ? queueItemFromUpload(item, response.data) : item)))
      } else {
        const response = await uploadKnowledgeDocumentsBatch(knowledgeBaseId, {
          files,
          knowledgeDomainId: domainId,
          chunkingProfileId: profileId,
        })
        setUploadQueue((items) =>
          items.map((item, index) => {
            const uploaded = response.data.items[index]
            return uploaded ? queueItemFromUpload(item, uploaded) : { ...item, status: 'submitted' }
          }),
        )
      }
      toast.success(files.length === 1 ? '文档已提交，正在解析。' : `已提交 ${files.length} 个文档，正在解析。`)
      setTitle('')
      setFiles([])
      if (fileInputRef.current) fileInputRef.current.value = ''
      await loadDocuments()
    } catch {
      setUploadError('上传失败，请稍后重试。')
      setUploadQueue((items) =>
        items.map((item) =>
          item.status === 'queued' || item.status === 'uploading'
            ? { ...item, status: 'failed', message: '上传失败，请稍后重试。' }
            : item,
        ),
      )
      toast.error('上传失败，请稍后重试。')
    } finally {
      setUploading(false)
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    try {
      await deleteKnowledgeDocument(deleteTarget.id)
      setDeleteTarget(null)
      await loadDocuments()
    } catch {
      toast.error('删除失败，请稍后重试。')
    }
  }

  function openDocument(documentId: number) {
    navigate(`/documents/${documentId}`)
  }

  function handleDocumentRowKeyDown(event: KeyboardEvent<HTMLTableRowElement>, documentId: number) {
    if (event.target !== event.currentTarget) return
    if (event.key !== 'Enter' && event.key !== ' ') return

    event.preventDefault()
    openDocument(documentId)
  }

  return (
    <ConsolePage
      title={detail?.name ?? '知识库'}
      description={detail?.description ?? '管理知识库文档与解析状态。'}
      loading={loading}
      error={error}
      actions={
        <Button variant="ghost" onClick={() => navigate('/knowledge')}>
          返回列表
        </Button>
      }
    >
      <section className="surface-box" style={{ display: 'grid', gap: 12 }}>
        <p className="section-label">上传文档</p>
        <div className="filter-row">
          <label className="field" style={{ flex: '1 1 220px' }}>
            <span>标题（可选）</span>
            <input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="默认使用文件名" />
          </label>
          <label className="field" style={{ flex: '1 1 160px' }}>
            <span>知识域</span>
            <select
              value={domainId ?? ''}
              onChange={(event) => setDomainId(event.target.value ? Number(event.target.value) : null)}
            >
              <option value="">默认</option>
              {domains.map((domain) => (
                <option key={domain.id} value={domain.id}>
                  {domain.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field" style={{ flex: '1 1 160px' }}>
            <span>切块策略</span>
            <select
              value={profileId ?? ''}
              onChange={(event) => setProfileId(event.target.value ? Number(event.target.value) : null)}
            >
              <option value="">默认</option>
              {profiles.map((profile) => (
                <option key={profile.id} value={profile.id}>
                  {profile.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field" style={{ flex: '1 1 220px' }}>
            <span>文件</span>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              data-testid="document-upload-file"
              onChange={(event) => handleFilesChange(event.target.files)}
            />
          </label>
        </div>
        {uploadError && (
          <p className="error-banner" role="alert">
            {uploadError}
          </p>
        )}
        {uploadQueue.length > 0 && (
          <div className="upload-queue" role="list" aria-label="上传队列">
            {uploadQueue.map((item) => (
              <div
                key={item.localId}
                className="upload-queue__item"
                role="listitem"
                data-status={item.status}
                data-testid={`upload-queue-item-${item.localId}`}
              >
                <div className="upload-queue__meta">
                  <strong>{item.fileName}</strong>
                  <span>{formatFileSize(item.fileSize)}</span>
                  {item.title && item.title !== item.fileName ? <span>{item.title}</span> : null}
                </div>
                <Badge className={uploadStatusBadge(item.status)}>{UPLOAD_STATUS_LABEL[item.status]}</Badge>
                {item.message ? <span className="field-error upload-queue__message">{item.message}</span> : null}
              </div>
            ))}
          </div>
        )}
        <div className="action-row">
          <Button data-testid="document-upload-submit" loading={uploading} onClick={handleUpload}>
            {files.length > 1 ? `批量上传 ${files.length} 个文档` : '上传文档'}
          </Button>
        </div>
      </section>

      <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
        <p className="section-label">文档列表</p>
        <div className="filter-row">
          <label className="field" style={{ flex: '1 1 220px' }}>
            <span>搜索文档</span>
            <input
              value={documentSearch}
              placeholder="按标题或类型搜索"
              onChange={(event) => setDocumentSearch(event.target.value)}
            />
          </label>
          <label className="field" style={{ flex: '1 1 160px' }}>
            <span>状态</span>
            <select value={documentStatusFilter} onChange={(event) => setDocumentStatusFilter(event.target.value)}>
              <option value="">全部状态</option>
              {documentStatusOptions.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </label>
          <label className="field" style={{ flex: '1 1 160px' }}>
            <span>文件类型</span>
            <select value={documentTypeFilter} onChange={(event) => setDocumentTypeFilter(event.target.value)}>
              <option value="">全部类型</option>
              {documentTypeOptions.map((fileType) => (
                <option key={fileType} value={fileType}>
                  {fileType}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>标题</th>
                <th>类型</th>
                <th>状态</th>
                <th>分块</th>
                <th>更新时间</th>
                <th aria-label="操作" />
              </tr>
            </thead>
            <tbody>
              {documents.length === 0 ? (
                <tr>
                  <td colSpan={6}>
                    <div className="empty-line">还没有文档，先上传一个吧。</div>
                  </td>
                </tr>
              ) : filteredDocuments.length === 0 ? (
                <tr>
                  <td colSpan={6}>
                    <div className="empty-line">没有匹配的文档。</div>
                  </td>
                </tr>
              ) : (
                filteredDocuments.map((doc) => (
                  <tr
                    key={doc.id}
                    data-testid={`document-row-${doc.id}`}
                    tabIndex={0}
                    className="data-table__row--clickable"
                    onClick={() => openDocument(doc.id)}
                    onKeyDown={(event) => handleDocumentRowKeyDown(event, doc.id)}
                  >
                    <td>{doc.title}</td>
                    <td className="mono">{doc.fileType}</td>
                    <td>
                      <Badge className={statusBadge(doc.status)}>{doc.status}</Badge>
                    </td>
                    <td className="numeric">{doc.chunkCount}</td>
                    <td className="mono">{formatDateTime(doc.updatedAt)}</td>
                    <td onClick={(event) => event.stopPropagation()}>
                      <Button variant="ghost" size="sm" onClick={() => setDeleteTarget({ id: doc.id, title: doc.title })}>
                        删除
                      </Button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
      <ConfirmDialog
        open={deleteTarget != null}
        title="删除文档"
        description={`删除「${deleteTarget?.title ?? '该文档'}」后无法恢复，确认继续吗？`}
        confirmLabel="确认删除"
        cancelLabel="取消"
        tone="danger"
        onConfirm={confirmDelete}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
      />
    </ConsolePage>
  )
}

export default KnowledgeDetailPage
