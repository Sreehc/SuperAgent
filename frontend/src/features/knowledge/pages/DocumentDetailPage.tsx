import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
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
import type {
  ChunkingProfileItem,
  DocumentChunkItem,
  DocumentGraphDetail,
  DocumentTaskItem,
  DocumentVersionItem,
  KnowledgeDocumentDetail,
  PagedResult,
} from '../types'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'
import { Button } from '../../../shared/ui/button'
import {
  ConfirmDialog,
  Dialog,
  DialogContent,
  EmptyState,
  ErrorState,
  SelectField,
  TableStateRow,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/shared/ui'
import { toast } from '../../../utils/toast'
import { formatDateTime, formatNumber, formatTokenCount } from '@/shared/lib/format'

type DetailTabKey = 'chunks' | 'versions' | 'graph' | 'tasks'
type DetailTabErrors = Partial<Record<DetailTabKey, string>>

interface TabLoadResult<T> {
  data: T
  error?: string
}

function taskBadge(status: string) {
  if (status === 'success') return 'badge--success'
  if (status === 'failed' || status === 'error') return 'badge--danger'
  if (status === 'running' || status === 'pending') return 'badge--warning'
  return ''
}

async function resolveTab<T>(request: Promise<{ data: T }>, fallback: T, error: string): Promise<TabLoadResult<T>> {
  try {
    const response = await request
    return { data: response.data }
  } catch {
    return { data: fallback, error }
  }
}

function emptyChunkPage(): PagedResult<DocumentChunkItem> {
  return {
    items: [],
    page: 1,
    pageSize: 100,
    total: 0,
  }
}

export function DocumentDetailPage() {
  const params = useParams()
  const navigate = useNavigate()
  const documentId = Number(params.documentId)

  const [detail, setDetail] = useState<KnowledgeDocumentDetail | null>(null)
  const [chunks, setChunks] = useState<DocumentChunkItem[]>([])
  const [versions, setVersions] = useState<DocumentVersionItem[]>([])
  const [graph, setGraph] = useState<DocumentGraphDetail | null>(null)
  const [tasks, setTasks] = useState<DocumentTaskItem[]>([])
  const [profiles, setProfiles] = useState<ChunkingProfileItem[]>([])
  const [tabErrors, setTabErrors] = useState<DetailTabErrors>({})
  const [profileError, setProfileError] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reprocessOpen, setReprocessOpen] = useState(false)
  const [reprocessReason, setReprocessReason] = useState('')
  const [reprocessProfileId, setReprocessProfileId] = useState<number | null>(null)
  const [reprocessError, setReprocessError] = useState('')
  const [reprocessing, setReprocessing] = useState(false)
  const [rebuildOpen, setRebuildOpen] = useState(false)
  const [rebuildError, setRebuildError] = useState('')

  const load = useCallback(async () => {
    const docResponse = await getKnowledgeDocument(documentId)
    const [chunkResponse, versionResponse, graphResponse, taskResponse, profileResponse] = await Promise.all([
      resolveTab(listKnowledgeDocumentChunks(documentId, { pageSize: 100 }), emptyChunkPage(), 'Chunks 加载失败。'),
      resolveTab(listDocumentVersions(documentId), [] as DocumentVersionItem[], '版本记录加载失败。'),
      resolveTab<DocumentGraphDetail | null>(getDocumentGraph(documentId), null, '图谱加载失败。'),
      resolveTab(listKnowledgeDocumentTasks(documentId), [] as DocumentTaskItem[], '任务日志加载失败。'),
      resolveTab(listChunkingProfiles(), [] as ChunkingProfileItem[], '切块策略加载失败。'),
    ])

    setDetail(docResponse.data)
    setChunks(chunkResponse.data.items)
    setVersions(versionResponse.data)
    setGraph(graphResponse.data)
    setTasks(taskResponse.data)
    setProfiles(profileResponse.data)
    setProfileError(profileResponse.error ?? '')
    setTabErrors({
      ...(chunkResponse.error ? { chunks: chunkResponse.error } : {}),
      ...(versionResponse.error ? { versions: versionResponse.error } : {}),
      ...(graphResponse.error ? { graph: graphResponse.error } : {}),
      ...(taskResponse.error ? { tasks: taskResponse.error } : {}),
    })
  }, [documentId])

  useEffect(() => {
    let cancelled = false
    async function bootstrap() {
      setLoading(true)
      setError('')
      try {
        await load()
      } catch {
        if (!cancelled) setError('加载文档详情失败。')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void bootstrap()
    return () => {
      cancelled = true
    }
  }, [load])

  function openReprocessDialog() {
    setReprocessReason('')
    setReprocessProfileId(detail?.chunkingProfileId ?? null)
    setReprocessError('')
    setReprocessOpen(true)
  }

  function openRebuildDialog() {
    setRebuildError('')
    setRebuildOpen(true)
  }

  async function confirmReprocess() {
    setReprocessing(true)
    setReprocessError('')
    try {
      const reason = reprocessReason.trim()
      const payload: { reason?: string; chunkingProfileId?: number | null } = {}
      if (reason) {
        payload.reason = reason
      }
      if (reprocessProfileId != null) {
        payload.chunkingProfileId = reprocessProfileId
      }

      await reprocessKnowledgeDocument(documentId, payload)
      toast.success('已提交重处理任务。')
      setReprocessOpen(false)
      await load()
    } catch {
      setReprocessError('重处理失败，请稍后重试。')
      toast.error('重处理失败，请稍后重试。')
    } finally {
      setReprocessing(false)
    }
  }

  async function confirmRebuildGraph() {
    setRebuildError('')
    try {
      const response = await rebuildDocumentGraph(documentId)
      setGraph(response.data)
      setTabErrors((current) => {
        const next = { ...current }
        delete next.graph
        return next
      })
      toast.success('图谱重建已提交。')
      await load()
    } catch {
      setRebuildError('图谱重建失败，请稍后重试。')
      toast.error('图谱重建失败，请稍后重试。')
      throw new Error('rebuild graph failed')
    }
  }

  const selectedProfileMissing = reprocessProfileId != null && !profiles.some((profile) => profile.id === reprocessProfileId)

  return (
    <ConsolePage
      title={detail?.title ?? '文档详情'}
      description={detail?.fileName ?? ''}
      loading={loading}
      error={error}
      actions={
        <>
          <Button variant="ghost" onClick={() => navigate(-1)}>
            返回
          </Button>
          <Button onClick={openReprocessDialog}>
            重处理
          </Button>
        </>
      }
    >
      {detail && (
        <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
          <div className="meta-row">
            <Badge className={taskBadge(detail.status)}>{detail.status}</Badge>
            <span className="status-chip mono">{detail.fileType}</span>
            <span className="status-chip numeric">{detail.chunkCount} chunks</span>
            <span className="status-chip mono">v{detail.activeVersionNo}</span>
          </div>
          {detail.errorMessage && <p className="error-banner">{detail.errorMessage}</p>}
        </section>
      )}

      <section className="surface-box document-detail-tabs">
        <Tabs defaultValue="text" className="document-tabs">
          <TabsList className="document-tabs__list" aria-label="文档详情分组">
            <TabsTrigger value="text">解析文本</TabsTrigger>
            <TabsTrigger value="chunks">Chunks</TabsTrigger>
            <TabsTrigger value="versions">版本</TabsTrigger>
            <TabsTrigger value="graph">图谱</TabsTrigger>
            <TabsTrigger value="tasks">任务日志</TabsTrigger>
          </TabsList>

          <TabsContent value="text" className="document-tab-panel">
            <pre className="parsed-text metadata" style={{ maxHeight: 420 }}>
              {detail?.parsedText?.trim() || '暂无解析文本。'}
            </pre>
          </TabsContent>

          <TabsContent value="chunks" className="document-tab-panel">
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Chunk</th>
                    <th>章节</th>
                    <th>内容</th>
                    <th>字符</th>
                    <th>Token</th>
                    <th>创建时间</th>
                  </tr>
                </thead>
                <tbody>
                  {tabErrors.chunks ? (
                    <TableStateRow state="error" colSpan={6} title="Chunks 加载失败" description={tabErrors.chunks} />
                  ) : chunks.length === 0 ? (
                    <TableStateRow state="empty" colSpan={6} title="暂无 Chunks" description="当前文档尚未生成切块。" />
                  ) : (
                    chunks.map((chunk) => (
                      <tr key={chunk.id}>
                        <td className="numeric">#{chunk.chunkNo}</td>
                        <td>{chunk.sectionTitle ?? '—'}</td>
                        <td className="metadata">{chunk.content}</td>
                        <td className="numeric">{formatNumber(chunk.charCount)}</td>
                        <td className="numeric">{formatTokenCount(chunk.tokenCount)}</td>
                        <td className="mono">{formatDateTime(chunk.createdAt)}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </TabsContent>

          <TabsContent value="versions" className="document-tab-panel">
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>版本</th>
                    <th>状态</th>
                    <th>Chunks</th>
                    <th>图谱同步</th>
                    <th>更新时间</th>
                  </tr>
                </thead>
                <tbody>
                  {tabErrors.versions ? (
                    <TableStateRow state="error" colSpan={5} title="版本记录加载失败" description={tabErrors.versions} />
                  ) : versions.length === 0 ? (
                    <TableStateRow state="empty" colSpan={5} title="暂无版本记录" description="当前文档尚未生成版本记录。" />
                  ) : (
                    versions.map((version) => (
                      <tr key={version.id}>
                        <td className="mono">v{version.versionNo}</td>
                        <td>
                          <Badge className={taskBadge(version.status)}>{version.status}</Badge>
                        </td>
                        <td className="numeric">{formatNumber(version.chunkCount)}</td>
                        <td className="mono">{version.graphSyncStatus}</td>
                        <td className="mono">{formatDateTime(version.updatedAt)}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </TabsContent>

          <TabsContent value="graph" className="document-tab-panel">
            <div className="document-tab-header">
              <p className="section-label">图谱</p>
              <Button size="sm" variant="ghost" onClick={openRebuildDialog}>
                重建图谱
              </Button>
            </div>
            {tabErrors.graph ? (
              <ErrorState title="图谱加载失败" description={tabErrors.graph} size="compact" />
            ) : !graph || (graph.nodes.length === 0 && graph.edges.length === 0) ? (
              <EmptyState title="暂无图谱数据" description="当前文档尚未生成节点或关系。" size="compact" />
            ) : (
              <div className="document-graph">
                <div className="document-graph__summary">
                  <span className="status-chip mono">图谱版本 v{graph.versionNo}</span>
                  <span className="status-chip mono">文档 {graph.documentGraphSyncStatus}</span>
                  <span className="status-chip mono">版本 {graph.versionGraphSyncStatus}</span>
                  <span className="status-chip numeric">节点 {formatNumber(graph.nodes.length)}</span>
                  <span className="status-chip numeric">关系 {formatNumber(graph.edges.length)}</span>
                </div>

                <div className="document-graph__grid">
                  <section className="document-graph__section" aria-label="图谱节点">
                    <p className="section-label">节点</p>
                    <div className="table-wrap">
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th>ID</th>
                            <th>类型</th>
                            <th>标签</th>
                          </tr>
                        </thead>
                        <tbody>
                          {graph.nodes.length === 0 ? (
                            <TableStateRow state="empty" colSpan={3} title="暂无节点" />
                          ) : (
                            graph.nodes.map((node) => (
                              <tr key={node.id}>
                                <td className="mono">{node.id}</td>
                                <td className="mono">{node.type}</td>
                                <td>{node.label}</td>
                              </tr>
                            ))
                          )}
                        </tbody>
                      </table>
                    </div>
                  </section>

                  <section className="document-graph__section" aria-label="图谱关系">
                    <p className="section-label">关系</p>
                    <div className="table-wrap">
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th>来源</th>
                            <th>目标</th>
                            <th>类型</th>
                          </tr>
                        </thead>
                        <tbody>
                          {graph.edges.length === 0 ? (
                            <TableStateRow state="empty" colSpan={3} title="暂无关系" />
                          ) : (
                            graph.edges.map((edge) => (
                              <tr key={`${edge.sourceId}-${edge.targetId}-${edge.type}`}>
                                <td className="mono">{edge.sourceId}</td>
                                <td className="mono">{edge.targetId}</td>
                                <td>{edge.type}</td>
                              </tr>
                            ))
                          )}
                        </tbody>
                      </table>
                    </div>
                  </section>
                </div>
              </div>
            )}
          </TabsContent>

          <TabsContent value="tasks" className="document-tab-panel">
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>任务</th>
                    <th>状态</th>
                    <th>重试</th>
                    <th>输出</th>
                    <th>时间</th>
                  </tr>
                </thead>
                <tbody>
                  {tabErrors.tasks ? (
                    <TableStateRow state="error" colSpan={5} title="任务日志加载失败" description={tabErrors.tasks} />
                  ) : tasks.length === 0 ? (
                    <TableStateRow state="empty" colSpan={5} title="暂无任务记录" />
                  ) : (
                    tasks.map((task) => (
                      <tr key={task.id}>
                        <td className="mono">{`${task.taskType} · ${task.status}`}</td>
                        <td>
                          <Badge className={taskBadge(task.status)}>{task.status}</Badge>
                        </td>
                        <td className="numeric">{task.attemptCount}</td>
                        <td>{task.outputSummary ?? task.errorMessage ?? '—'}</td>
                        <td className="mono">{formatDateTime(task.finishedAt ?? task.startedAt ?? task.createdAt)}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </TabsContent>
        </Tabs>
      </section>

      <Dialog open={reprocessOpen} onOpenChange={(open) => !open && setReprocessOpen(false)}>
        <DialogContent title="重处理文档" className="dialog-content--narrow">
          <form
            className="dialog-body"
            onSubmit={(event) => {
              event.preventDefault()
              void confirmReprocess()
            }}
          >
            <p className="dialog-description">重处理会重新解析文档并生成新的切块版本，可能覆盖当前解析结果。</p>
            {profileError && (
              <p className="error-banner" role="alert">
                切块策略加载失败，只能使用默认策略。
              </p>
            )}
            {reprocessError && (
              <p className="error-banner" role="alert">
                {reprocessError}
              </p>
            )}
            <label className="field">
              <span>重处理原因</span>
              <textarea
                value={reprocessReason}
                rows={3}
                placeholder="例如：修复解析失败、调整切块策略后重跑"
                onChange={(event) => setReprocessReason(event.target.value)}
              />
            </label>
            <label className="field">
              <span>切块策略</span>
              <SelectField
                value={reprocessProfileId ?? ''}
                onChange={(event) => setReprocessProfileId(event.target.value ? Number(event.target.value) : null)}
              >
                <option value="">默认</option>
                {selectedProfileMissing && <option value={reprocessProfileId ?? ''}>当前策略 #{reprocessProfileId}</option>}
                {profiles.map((profile) => (
                  <option key={profile.id} value={profile.id}>
                    {profile.name}
                  </option>
                ))}
              </SelectField>
            </label>
            <div className="dialog-footer">
              <Button type="button" variant="ghost" onClick={() => setReprocessOpen(false)}>
                取消
              </Button>
              <Button type="submit" variant="danger" loading={reprocessing}>
                确认重处理
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={rebuildOpen}
        title="重建图谱"
        description="重建图谱会重新生成当前文档的节点和关系，已有图谱结果可能被覆盖。确认继续吗？"
        error={rebuildError}
        confirmLabel="确认重建"
        cancelLabel="取消"
        tone="danger"
        onConfirm={confirmRebuildGraph}
        onOpenChange={(open) => {
          setRebuildOpen(open)
          if (!open) setRebuildError('')
        }}
      />
    </ConsolePage>
  )
}

export default DocumentDetailPage
