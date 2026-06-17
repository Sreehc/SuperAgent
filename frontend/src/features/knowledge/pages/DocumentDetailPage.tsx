import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  getKnowledgeDocument,
  listKnowledgeDocumentTasks,
  reprocessKnowledgeDocument,
} from '../api'
import type { DocumentTaskItem, KnowledgeDocumentDetail } from '../types'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'
import { Button } from '../../../shared/ui/button'
import { toast } from '../../../utils/toast'

function taskBadge(status: string) {
  if (status === 'success') return 'badge--success'
  if (status === 'failed' || status === 'error') return 'badge--danger'
  if (status === 'running' || status === 'pending') return 'badge--warning'
  return ''
}

export function DocumentDetailPage() {
  const params = useParams()
  const navigate = useNavigate()
  const documentId = Number(params.documentId)

  const [detail, setDetail] = useState<KnowledgeDocumentDetail | null>(null)
  const [tasks, setTasks] = useState<DocumentTaskItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reprocessing, setReprocessing] = useState(false)

  const load = useCallback(async () => {
    const [docResponse, taskResponse] = await Promise.all([
      getKnowledgeDocument(documentId),
      listKnowledgeDocumentTasks(documentId),
    ])
    setDetail(docResponse.data)
    setTasks(taskResponse.data)
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

  async function handleReprocess() {
    setReprocessing(true)
    try {
      await reprocessKnowledgeDocument(documentId)
      toast.success('已提交重处理任务。')
      await load()
    } catch {
      toast.error('重处理失败，请稍后重试。')
    } finally {
      setReprocessing(false)
    }
  }

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
          <Button loading={reprocessing} onClick={handleReprocess}>
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

      <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
        <p className="section-label">解析文本</p>
        <pre className="parsed-text metadata" style={{ maxHeight: 360 }}>
          {detail?.parsedText?.trim() || '暂无解析文本。'}
        </pre>
      </section>

      <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
        <p className="section-label">任务日志</p>
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
              {tasks.length === 0 ? (
                <tr>
                  <td colSpan={5}>
                    <div className="empty-line">暂无任务记录。</div>
                  </td>
                </tr>
              ) : (
                tasks.map((task) => (
                  <tr key={task.id}>
                    <td className="mono">{`${task.taskType} · ${task.status}`}</td>
                    <td>
                      <Badge className={taskBadge(task.status)}>{task.status}</Badge>
                    </td>
                    <td className="numeric">{task.attemptCount}</td>
                    <td>{task.outputSummary ?? task.errorMessage ?? '—'}</td>
                    <td className="mono">
                      {task.finishedAt
                        ? new Date(task.finishedAt).toLocaleString('zh-CN')
                        : task.startedAt
                          ? new Date(task.startedAt).toLocaleString('zh-CN')
                          : new Date(task.createdAt).toLocaleString('zh-CN')}
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

export default DocumentDetailPage
