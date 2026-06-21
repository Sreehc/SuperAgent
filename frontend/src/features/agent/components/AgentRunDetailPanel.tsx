import { useId } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Badge, EmptyState, ErrorState, LoadingState } from '@/shared/ui'
import { formatDateTime, formatDurationMs } from '@/shared/lib/format'
import { getAgentRunDetail } from '../api'
import type { AgentCheckpoint, AgentRunDetail, AgentRunStep, ToolCallDetail } from '../types'

interface AgentRunDetailPanelProps {
  runId: number | null | undefined
}

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

function StepList({ steps }: { steps: AgentRunStep[] }) {
  if (steps.length === 0) {
    return <EmptyState title="无 Agent Step" size="compact" />
  }

  return (
    <div className="trace-card-list">
      {steps.map((step) => (
        <article key={step.id} className="trace-observation">
          <div className="trace-observation__header">
            <div>
              <div className="meta-row">
                <Badge tone={statusTone(step.status)}>{step.status}</Badge>
                <span className="mono">Step #{step.stepNo}</span>
                <span className="mono">{step.phase}</span>
              </div>
              <strong>{optionalText(step.decisionSummary)}</strong>
            </div>
            <div className="meta-row">
              <span className="metric-chip">{step.startedAt ? formatDateTime(step.startedAt) : '—'}</span>
              <span className="metric-chip">{step.finishedAt ? formatDateTime(step.finishedAt) : '—'}</span>
            </div>
          </div>
          <dl className="reference-detail trace-detail-list">
            <div>
              <dt>Observation</dt>
              <dd>{optionalText(step.observationSummary)}</dd>
            </div>
            <div>
              <dt>Tool</dt>
              <dd>{optionalText(step.selectedToolId)}</dd>
            </div>
            <div>
              <dt>Reason</dt>
              <dd>{optionalText(step.selectedToolReason)}</dd>
            </div>
            {step.errorMessage ? (
              <div>
                <dt>错误</dt>
                <dd className="danger-text">{step.errorMessage}</dd>
              </div>
            ) : null}
          </dl>
        </article>
      ))}
    </div>
  )
}

function CheckpointList({ checkpoints }: { checkpoints: AgentCheckpoint[] }) {
  if (checkpoints.length === 0) {
    return <EmptyState title="无 Checkpoint" size="compact" />
  }

  return (
    <div className="trace-card-list">
      {checkpoints.map((checkpoint) => (
        <article key={checkpoint.id} className="trace-observation">
          <div className="trace-observation__header">
            <div>
              <div className="meta-row">
                <Badge tone={checkpoint.stable ? 'success' : 'warning'}>{checkpoint.stable ? 'stable' : 'draft'}</Badge>
                <span className="mono">Step {checkpoint.stepId ? `#${checkpoint.stepId}` : '—'}</span>
              </div>
              <strong>Checkpoint #{checkpoint.checkpointNo}</strong>
            </div>
            <div className="meta-row">
              <span className="metric-chip">{checkpoint.checkpointType}</span>
              <span className="metric-chip">{formatDateTime(checkpoint.createdAt)}</span>
            </div>
          </div>
        </article>
      ))}
    </div>
  )
}

function ToolCallList({ toolCalls }: { toolCalls: ToolCallDetail[] }) {
  if (toolCalls.length === 0) {
    return <EmptyState title="无工具调用" size="compact" />
  }

  return (
    <div className="trace-card-list">
      {toolCalls.map((call) => (
        <article key={call.id} className="trace-observation">
          <div className="trace-observation__header">
            <div>
              <div className="meta-row">
                <Badge tone={statusTone(call.status)}>{call.status}</Badge>
                {call.pluginVersion ? <span className="mono">v{call.pluginVersion}</span> : null}
              </div>
              <strong>{call.toolId}</strong>
            </div>
            <div className="meta-row">
              <span className="metric-chip">{formatDurationMs(call.latencyMs)}</span>
              <span className="metric-chip">{formatDateTime(call.createdAt)}</span>
            </div>
          </div>
          <dl className="reference-detail trace-detail-list">
            <div>
              <dt>Request</dt>
              <dd>{optionalText(call.requestSummary)}</dd>
            </div>
            <div>
              <dt>Response</dt>
              <dd>{optionalText(call.responseSummary)}</dd>
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
  )
}

function AgentRunContent({ detail }: { detail: AgentRunDetail }) {
  const { summary } = detail

  return (
    <>
      <div className="trace-overview">
        <Badge tone={statusTone(summary.status)}>{summary.status}</Badge>
        <span className="metric-chip">Run #{summary.runId}</span>
        <span className="metric-chip">模型步数 {summary.modelStepCount}</span>
        <span className="metric-chip">工具调用 {summary.toolCallCount}</span>
        <span className="metric-chip">最新 Checkpoint {summary.latestCheckpointNo}</span>
        <span className="metric-chip">{summary.memoryStrategy}</span>
      </div>
      {summary.routeReason || summary.errorMessage ? (
        <dl className="reference-detail trace-detail-list">
          {summary.routeReason ? (
            <div>
              <dt>Route</dt>
              <dd>{summary.routeReason}</dd>
            </div>
          ) : null}
          {summary.errorMessage ? (
            <div>
              <dt>错误</dt>
              <dd className="danger-text">{summary.errorMessage}</dd>
            </div>
          ) : null}
        </dl>
      ) : null}

      <div className="agent-run-detail-grid">
        <section className="agent-run-detail-section">
          <div className="section-heading">
            <h4 className="section-label">Steps</h4>
            <span className="metric-chip">{detail.steps.length}</span>
          </div>
          <StepList steps={detail.steps} />
        </section>
        <section className="agent-run-detail-section">
          <div className="section-heading">
            <h4 className="section-label">Checkpoints</h4>
            <span className="metric-chip">{detail.checkpoints.length}</span>
          </div>
          <CheckpointList checkpoints={detail.checkpoints} />
        </section>
        <section className="agent-run-detail-section">
          <div className="section-heading">
            <h4 className="section-label">Tool Calls</h4>
            <span className="metric-chip">{detail.toolCalls.length}</span>
          </div>
          <ToolCallList toolCalls={detail.toolCalls} />
        </section>
      </div>
    </>
  )
}

export function AgentRunDetailPanel({ runId }: AgentRunDetailPanelProps) {
  const headingId = useId()
  const safeRunId = typeof runId === 'number' && Number.isFinite(runId) && runId > 0 ? runId : null
  const query = useQuery({
    queryKey: ['agent-run-detail', safeRunId],
    queryFn: () => getAgentRunDetail(safeRunId as number),
    enabled: safeRunId !== null,
  })

  return (
    <section className="surface-box trace-section agent-run-detail-panel" role="region" aria-labelledby={headingId}>
      <div className="section-heading">
        <h3 id={headingId} className="section-label">
          Agent Run 详情
        </h3>
        {safeRunId ? <span className="metric-chip">#{safeRunId}</span> : null}
      </div>

      {!safeRunId ? (
        <EmptyState title="无 Agent Run 关联" description="该 Trace 没有关联的 Agent Run。" size="compact" />
      ) : query.isLoading ? (
        <LoadingState label="正在加载 Agent Run 详情" size="compact" />
      ) : query.isError ? (
        <ErrorState title="加载 Agent Run 详情失败。" description="Trace 详情仍可继续查看，请稍后重试 Agent Run 明细。" size="compact" />
      ) : query.data?.data ? (
        <AgentRunContent detail={query.data.data} />
      ) : (
        <EmptyState title="无 Agent Run 详情" description="服务端未返回该 Agent Run 的明细。" size="compact" />
      )}
    </section>
  )
}
