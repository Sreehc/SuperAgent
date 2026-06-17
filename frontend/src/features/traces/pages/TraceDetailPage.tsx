import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Badge, Button, ConsolePage } from '@/shared/ui'
import { LoadingSpinner } from '@/components/feedback'
import { getAdminTrace } from '../api'
import type { TraceRetrievalDetail, TraceStageDetail } from '../types'

export function TraceDetailPage() {
  const { exchangeId } = useParams()
  const navigate = useNavigate()
  const id = Number(exchangeId)
  const [activeStage, setActiveStage] = useState<TraceStageDetail | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['admin-trace', id],
    queryFn: () => getAdminTrace(id),
    enabled: Number.isInteger(id) && id > 0,
  })

  if (isLoading) return <LoadingSpinner />
  const trace = data?.data
  if (!trace) return <ConsolePage title="Trace 详情"><div className="empty-line">未找到该 Trace。</div></ConsolePage>

  const stage = activeStage ?? trace.stages[0] ?? null

  return (
    <ConsolePage
      title={`Trace #${trace.exchangeId}`}
      description={`模式 ${trace.executionMode} · 状态 ${trace.status} · 耗时 ${trace.durationMs}ms`}
      actions={<Button variant="ghost" onClick={() => navigate('/traces')}>返回列表</Button>}
    >
      <div className="trace-detail-grid">
        <section className="surface-box">
          <h3 className="section-label">阶段时间线</h3>
          <div className="trace-timeline">
            {trace.stages.map((item) => (
              <button
                key={item.stageId}
                type="button"
                className={`trace-stage${stage?.stageId === item.stageId ? ' trace-stage--active' : ''}`}
                onClick={() => setActiveStage(item)}
              >
                <span aria-hidden="true" />
                <div>
                  <strong>{item.stageCode}</strong>
                  <Badge tone={item.status === 'success' ? 'success' : item.status === 'failed' ? 'danger' : 'neutral'}>
                    {item.status}
                  </Badge>
                  <small>{item.durationMs}ms</small>
                </div>
              </button>
            ))}
            {trace.stages.length === 0 && <div className="empty-line">无阶段记录</div>}
          </div>
        </section>

        <section className="surface-box">
          <h3 className="section-label">详情检查器</h3>
          {stage ? (
            <dl className="reference-detail">
              <div><dt>阶段</dt><dd>{stage.stageCode}</dd></div>
              <div><dt>输入</dt><dd>{stage.inputSummary ?? '—'}</dd></div>
              <div><dt>输出</dt><dd>{stage.outputSummary ?? '—'}</dd></div>
              {stage.errorMessage && <div><dt>错误</dt><dd className="danger-text">{stage.errorMessage}</dd></div>}
            </dl>
          ) : (
            <div className="empty-line">选择左侧阶段查看详情。</div>
          )}
        </section>

        <section className="surface-box">
          <h3 className="section-label">关联检索结果</h3>
          {trace.retrievals.length === 0 ? (
            <div className="empty-line">无检索记录</div>
          ) : (
            trace.retrievals.map((retrieval: TraceRetrievalDetail) => (
              <div key={retrieval.id} className="inset-box">
                <div className="meta-row">
                  <Badge tone="accent">{retrieval.channel}</Badge>
                  <span className="mono">命中 {retrieval.resultCount} · 选用 {retrieval.selectedCount}</span>
                </div>
                <p>{retrieval.queryText}</p>
              </div>
            ))
          )}
        </section>

        <section className="surface-box">
          <h3 className="section-label">关联模型调用</h3>
          {trace.modelCalls.length === 0 ? (
            <div className="empty-line">无模型调用记录</div>
          ) : (
            <table className="data-table">
              <thead>
                <tr><th>Provider</th><th>模型</th><th>类型</th><th>Tokens</th><th>延迟</th><th>状态</th></tr>
              </thead>
              <tbody>
                {trace.modelCalls.map((call) => (
                  <tr key={call.id}>
                    <td>{call.provider}</td>
                    <td>{call.model}</td>
                    <td>{call.callType}</td>
                    <td className="numeric">{(call.inputTokens ?? 0) + (call.outputTokens ?? 0)}</td>
                    <td className="numeric">{call.latencyMs ?? '—'}</td>
                    <td><Badge tone={call.status === 'success' ? 'success' : 'danger'}>{call.status}</Badge></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>
    </ConsolePage>
  )
}
