import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Badge, Button, ConsolePage, DetailInspector, EmptyState } from '@/shared/ui'
import { AgentRunDetailPanel } from '@/features/agent/components/AgentRunDetailPanel'
import { getAdminTrace } from '../api'
import type {
  TraceModelCallDetail,
  TraceRerankDetail,
  TraceRetrievalDetail,
  TraceRetrievalItemDetail,
  TraceStageDetail,
} from '../types'
import { formatDateTime, formatDurationMs } from '@/shared/lib/format'

function statusTone(status: string) {
  if (status === 'success' || status === 'completed') return 'success' as const
  if (status === 'failed' || status === 'error') return 'danger' as const
  if (status === 'running' || status === 'pending') return 'warning' as const
  return 'neutral' as const
}

function optionalText(value: string | number | null | undefined) {
  if (value === undefined || value === null || value === '') return '—'
  return `${value}`
}

function countPair(inputCount: number | null | undefined, outputCount: number | null | undefined) {
  return `输入 ${inputCount ?? 0} / 输出 ${outputCount ?? 0}`
}

function scoreText(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(2) : '—'
}

function stageLabel(stageId: number | null | undefined, stages: TraceStageDetail[]) {
  if (!stageId) return '未关联阶段'
  return stages.find((stage) => stage.stageId === stageId)?.stageCode ?? `Stage #${stageId}`
}

function StageInspector({ stage }: { stage: TraceStageDetail | null }) {
  return (
    <DetailInspector
      title="阶段详情"
      description={stage ? `Stage #${stage.stageId}` : '当前 Trace 未提供阶段明细'}
      meta={stage ? <Badge tone={statusTone(stage.status)}>{stage.status}</Badge> : undefined}
      className="trace-stage-inspector"
    >
      {stage ? (
        <dl className="reference-detail trace-detail-list">
          <div>
            <dt>阶段</dt>
            <dd>{stage.stageCode}</dd>
          </div>
          <div>
            <dt>开始时间</dt>
            <dd>{stage.startedAt ? formatDateTime(stage.startedAt) : '—'}</dd>
          </div>
          <div>
            <dt>结束时间</dt>
            <dd>{stage.finishedAt ? formatDateTime(stage.finishedAt) : '—'}</dd>
          </div>
          <div>
            <dt>耗时</dt>
            <dd>{formatDurationMs(stage.durationMs)}</dd>
          </div>
          <div>
            <dt>输入</dt>
            <dd>{optionalText(stage.inputSummary)}</dd>
          </div>
          <div>
            <dt>输出</dt>
            <dd>{optionalText(stage.outputSummary)}</dd>
          </div>
          {stage.errorMessage ? (
            <div>
              <dt>错误</dt>
              <dd className="danger-text">{stage.errorMessage}</dd>
            </div>
          ) : null}
        </dl>
      ) : (
        <EmptyState title="选择一个阶段查看详情。" size="compact" />
      )}
    </DetailInspector>
  )
}

function RetrievalItemList({ items }: { items: TraceRetrievalItemDetail[] }) {
  if (items.length === 0) {
    return <div className="empty-line">无候选片段</div>
  }

  return (
    <div className="trace-candidate-list">
      {items.map((item) => (
        <div key={item.id} className="trace-candidate">
          <div className="meta-row">
            <Badge tone={item.selected ? 'success' : 'neutral'}>{item.selected ? 'selected' : `rank ${item.rankNo}`}</Badge>
            <span className="mono">Document #{item.documentId}</span>
            <span className="mono">Chunk #{item.chunkId}</span>
          </div>
          <div className="meta-row">
            <span className="metric-chip">raw {scoreText(item.rawScore)}</span>
            <span className="metric-chip">fused {scoreText(item.fusedScore)}</span>
          </div>
        </div>
      ))}
    </div>
  )
}

function RetrievalSection({ retrievals, stages }: { retrievals: TraceRetrievalDetail[]; stages: TraceStageDetail[] }) {
  return (
    <section className="surface-box trace-section">
      <div className="section-heading">
        <h3 className="section-label">关联检索</h3>
        <span className="metric-chip">{retrievals.length} 次</span>
      </div>
      {retrievals.length === 0 ? (
        <EmptyState title="无检索记录" size="compact" />
      ) : (
        <div className="trace-card-list">
          {retrievals.map((retrieval) => (
            <article key={retrieval.id} className="trace-observation">
              <div className="trace-observation__header">
                <div>
                  <div className="meta-row">
                    <Badge tone="accent">{retrieval.channel}</Badge>
                    <span className="mono">{stageLabel(retrieval.stageId, stages)}</span>
                    <span className="mono">Q{retrieval.subQuestionNo}</span>
                  </div>
                  <strong>{retrieval.queryText}</strong>
                </div>
                <div className="meta-row">
                  <span className="metric-chip">命中 {retrieval.resultCount}</span>
                  <span className="metric-chip">选用 {retrieval.selectedCount}</span>
                  <span className="metric-chip">{formatDurationMs(retrieval.latencyMs)}</span>
                </div>
              </div>
              <RetrievalItemList items={retrieval.items} />
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

function ModelCallSection({ modelCalls, stages }: { modelCalls: TraceModelCallDetail[]; stages: TraceStageDetail[] }) {
  return (
    <section className="surface-box trace-section">
      <div className="section-heading">
        <h3 className="section-label">模型调用</h3>
        <span className="metric-chip">{modelCalls.length} 次</span>
      </div>
      {modelCalls.length === 0 ? (
        <EmptyState title="无模型调用记录" size="compact" />
      ) : (
        <div className="trace-card-list">
          {modelCalls.map((call) => (
            <article key={call.id} className="trace-observation">
              <div className="trace-observation__header">
                <div>
                  <div className="meta-row">
                    <Badge tone={statusTone(call.status)}>{call.status}</Badge>
                    <span className="mono">{stageLabel(call.stageId, stages)}</span>
                    <span className="mono">{call.callType}</span>
                  </div>
                  <strong className="trace-provider-line">
                    <span>{call.provider}</span>
                    <span>{call.model}</span>
                  </strong>
                </div>
                <div className="meta-row">
                  <span className="metric-chip">{countPair(call.inputTokens, call.outputTokens)}</span>
                  <span className="metric-chip">{formatDurationMs(call.latencyMs)}</span>
                </div>
              </div>
              <dl className="reference-detail trace-detail-list">
                <div>
                  <dt>Prompt</dt>
                  <dd>{optionalText(call.promptSummary)}</dd>
                </div>
                <div>
                  <dt>Output</dt>
                  <dd>{optionalText(call.outputSummary)}</dd>
                </div>
                {call.errorMessage ? (
                  <div>
                    <dt>错误</dt>
                    <dd className="danger-text">{call.errorMessage}</dd>
                  </div>
                ) : null}
              </dl>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

function RerankSection({ reranks }: { reranks: TraceRerankDetail[] }) {
  return (
    <section className="surface-box trace-section">
      <div className="section-heading">
        <h3 className="section-label">Rerank</h3>
        <span className="metric-chip">{reranks.length} 次</span>
      </div>
      {reranks.length === 0 ? (
        <EmptyState title="无 rerank 记录" size="compact" />
      ) : (
        <div className="trace-card-list">
          {reranks.map((rerank) => (
            <article key={rerank.id} className="trace-observation">
              <div className="trace-observation__header">
                <div>
                  <div className="meta-row">
                    <Badge tone={statusTone(rerank.status)}>{rerank.status}</Badge>
                    <Badge tone={rerank.enabled ? 'success' : 'neutral'}>{rerank.enabled ? '已启用' : '已跳过'}</Badge>
                  </div>
                  <strong className="trace-provider-line">
                    <span>{optionalText(rerank.provider)}</span>
                    <span>{optionalText(rerank.model)}</span>
                  </strong>
                </div>
                <div className="meta-row">
                  <span className="metric-chip">{countPair(rerank.inputCount, rerank.outputCount)}</span>
                  <span className="metric-chip">{formatDurationMs(rerank.latencyMs)}</span>
                </div>
              </div>
              {rerank.skippedReason || rerank.errorMessage ? (
                <dl className="reference-detail trace-detail-list">
                  {rerank.skippedReason ? (
                    <div>
                      <dt>跳过原因</dt>
                      <dd>{rerank.skippedReason}</dd>
                    </div>
                  ) : null}
                  {rerank.errorMessage ? (
                    <div>
                      <dt>错误</dt>
                      <dd className="danger-text">{rerank.errorMessage}</dd>
                    </div>
                  ) : null}
                </dl>
              ) : null}
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

export function TraceDetailPage() {
  const { exchangeId } = useParams()
  const navigate = useNavigate()
  const id = Number(exchangeId)
  const [activeStageId, setActiveStageId] = useState<number | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-trace', id],
    queryFn: () => getAdminTrace(id),
    enabled: Number.isInteger(id) && id > 0,
  })

  const trace = data?.data
  const stages = trace?.stages ?? []
  const activeStage = useMemo(
    () => stages.find((stage) => stage.stageId === activeStageId) ?? stages[0] ?? null,
    [activeStageId, stages],
  )

  if (!Number.isInteger(id) || id <= 0) {
    return (
      <ConsolePage title="Trace 详情" error="Trace ID 无效。">
        <div />
      </ConsolePage>
    )
  }

  if (isLoading) {
    return (
      <ConsolePage title="Trace 详情" loading>
        <div />
      </ConsolePage>
    )
  }

  if (isError) {
    return (
      <ConsolePage title="Trace 详情" error="加载 Trace 详情失败。">
        <div />
      </ConsolePage>
    )
  }

  if (!trace) {
    return (
      <ConsolePage title="Trace 详情" error="未找到该 Trace。">
        <div />
      </ConsolePage>
    )
  }

  return (
    <ConsolePage
      title={`Trace #${trace.exchangeId}`}
      description={`模式 ${trace.executionMode} · 状态 ${trace.status} · 耗时 ${formatDurationMs(trace.durationMs)}`}
      actions={
        <Button variant="ghost" onClick={() => navigate('/traces')}>
          返回列表
        </Button>
      }
    >
      <div className="trace-overview">
        <span className="metric-chip">会话 #{trace.sessionId}</span>
        <span className="metric-chip">用户 #{trace.userId}</span>
        <span className="metric-chip">开始 {formatDateTime(trace.startedAt)}</span>
        <span className="metric-chip">结束 {trace.finishedAt ? formatDateTime(trace.finishedAt) : '—'}</span>
        {trace.routeReason ? <span className="metric-chip">{trace.routeReason}</span> : null}
        {trace.agentRunId ? <span className="metric-chip">Agent Run #{trace.agentRunId}</span> : null}
        {trace.agentRunStatus ? <Badge tone={statusTone(trace.agentRunStatus)}>{trace.agentRunStatus}</Badge> : null}
      </div>

      <div className="trace-detail-layout">
        <section className="surface-box trace-timeline-panel">
          <div className="section-heading">
            <h3 className="section-label">阶段时间线</h3>
            <span className="metric-chip">{stages.length} 阶段</span>
          </div>
          <div className="trace-timeline">
            {stages.length === 0 ? (
              <EmptyState title="无阶段记录" size="compact" />
            ) : (
              stages.map((stage) => (
                <button
                  key={stage.stageId}
                  type="button"
                  className={`trace-stage${activeStage?.stageId === stage.stageId ? ' trace-stage--active' : ''}`}
                  aria-pressed={activeStage?.stageId === stage.stageId}
                  onClick={() => setActiveStageId(stage.stageId)}
                >
                  <span className="trace-stage__dot" aria-hidden="true" />
                  <span className="trace-stage__content">
                    <span className="trace-stage__title">{stage.stageCode}</span>
                    <span className="meta-row">
                      <Badge tone={statusTone(stage.status)}>{stage.status}</Badge>
                      <span className="mono">{formatDurationMs(stage.durationMs)}</span>
                    </span>
                  </span>
                </button>
              ))
            )}
          </div>
        </section>

        <StageInspector stage={activeStage} />
      </div>

      <AgentRunDetailPanel runId={trace.agentRunId} />

      <div className="trace-observability-grid">
        <RetrievalSection retrievals={trace.retrievals ?? []} stages={stages} />
        <ModelCallSection modelCalls={trace.modelCalls ?? []} stages={stages} />
        <RerankSection reranks={trace.reranks ?? []} />
      </div>
    </ConsolePage>
  )
}
