import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  deleteKnowledgeDocument,
  getKnowledgeBase,
  listChunkingProfiles,
  listKnowledgeDocuments,
  listKnowledgeDomains,
  uploadKnowledgeDocument,
} from '../api'
import type {
  ChunkingProfileItem,
  KnowledgeBaseDetail,
  KnowledgeDocumentListItem,
  KnowledgeDomainItem,
} from '../types'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'
import { Button } from '../../../shared/ui/button'
import { toast } from '../../../utils/toast'

const READY_POLL_INTERVAL = 2500

function statusBadge(status: string) {
  if (status === 'ready') return 'badge--success'
  if (status === 'failed' || status === 'error') return 'badge--danger'
  if (status === 'processing' || status === 'pending') return 'badge--warning'
  return ''
}

export function KnowledgeDetailPage() {
  const params = useParams()
  const navigate = useNavigate()
  const knowledgeBaseId = Number(params.knowledgeBaseId)

  const [detail, setDetail] = useState<KnowledgeBaseDetail | null>(null)
  const [documents, setDocuments] = useState<KnowledgeDocumentListItem[]>([])
  const [domains, setDomains] = useState<KnowledgeDomainItem[]>([])
  const [profiles, setProfiles] = useState<ChunkingProfileItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [title, setTitle] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [domainId, setDomainId] = useState<number | null>(null)
  const [profileId, setProfileId] = useState<number | null>(null)
  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const pollRef = useRef<number | null>(null)

  const loadDocuments = useCallback(async () => {
    const response = await listKnowledgeDocuments(knowledgeBaseId, { pageSize: 100 })
    setDocuments(response.data.items)
    return response.data.items
  }, [knowledgeBaseId])

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

  async function handleUpload() {
    if (!file) {
      toast.error('请选择要上传的文件。')
      return
    }
    setUploading(true)
    try {
      await uploadKnowledgeDocument(knowledgeBaseId, {
        file,
        title: title.trim() || undefined,
        knowledgeDomainId: domainId,
        chunkingProfileId: profileId,
      })
      toast.success('文档已提交，正在解析。')
      setTitle('')
      setFile(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
      await loadDocuments()
    } catch {
      toast.error('上传失败，请稍后重试。')
    } finally {
      setUploading(false)
    }
  }

  async function handleDelete(documentId: number) {
    if (!window.confirm('删除文档后无法恢复，确认继续吗？')) return
    try {
      await deleteKnowledgeDocument(documentId)
      await loadDocuments()
    } catch {
      toast.error('删除失败，请稍后重试。')
    }
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
              data-testid="document-upload-file"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </label>
        </div>
        <div className="action-row">
          <Button data-testid="document-upload-submit" loading={uploading} onClick={handleUpload}>
            上传文档
          </Button>
        </div>
      </section>

      <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
        <p className="section-label">文档列表</p>
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
              ) : (
                documents.map((doc) => (
                  <tr
                    key={doc.id}
                    data-testid={`document-row-${doc.id}`}
                    style={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/documents/${doc.id}`)}
                  >
                    <td>{doc.title}</td>
                    <td className="mono">{doc.fileType}</td>
                    <td>
                      <Badge className={statusBadge(doc.status)}>{doc.status}</Badge>
                    </td>
                    <td className="numeric">{doc.chunkCount}</td>
                    <td className="mono">{new Date(doc.updatedAt).toLocaleString('zh-CN')}</td>
                    <td onClick={(event) => event.stopPropagation()}>
                      <Button variant="ghost" size="sm" onClick={() => handleDelete(doc.id)}>
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
    </ConsolePage>
  )
}

export default KnowledgeDetailPage
