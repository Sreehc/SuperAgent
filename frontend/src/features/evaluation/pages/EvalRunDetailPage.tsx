import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getEvalRun, listEvalRunCases } from '../api'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'
import { TableStateRow } from '../../../shared/ui/status'
import { formatDateTime, formatDurationMs } from '@/shared/lib/format'

function caseTone(status: string): 'success' | 'danger' | 'warning' | 'neutral' {
  if (status === 'passed') return 'success'
  if (status === 'failed' || status === 'error') return 'danger'
  if (status === 'skipped') return 'neutral'
  return 'neutral'
}

function runTone(status: string): 'success' | 'danger' | 'warning' | 'neutral' {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'running' || status === 'pending') return 'warning'
  return 'neutral'
}

function JsonPreview({ value, label }: { value: Record<string, unknown>; label: string }) {
  return (
    <pre className="metadata-pre" aria-label={label}>
      {JSON.stringify(value ?? {}, null, 2)}
    </pre>
  )
}

export function EvalRunDetailPage() {
  const { runId } = useParams()
  const id = Number(runId)
  const enabled = Number.isInteger(id) && id > 0

  const runQuery = useQuery({
    queryKey: ['eval-run', id],
    queryFn: () => getEvalRun(id),
    enabled,
  })
  const casesQuery = useQuery({
    queryKey: ['eval-run-cases', id],
    queryFn: () => listEvalRunCases(id),
    enabled,
  })

  const run = runQuery.data?.data
  const runCases = casesQuery.data?.data ?? []

  return (
    <ConsolePage
      title={`运行 #${runId}`}
      description={run ? `套件 ${run.suiteKey} · 通过 ${run.passedCount} / 失败 ${run.failedCount}` : '运行明细'}
      backTo="/evals"
    >
      {run && (
        <section className="surface-box" style={{ display: 'grid', gap: 12 }}>
          <p className="section-label">运行摘要</p>
          <div className="metric-strip">
            <div className="metric-card">
              <span className="metric-label">套件</span>
              <strong className="mono">{run.suiteKey}</strong>
            </div>
            <div className="metric-card">
              <span className="metric-label">状态</span>
              <strong>
                <Badge tone={runTone(run.status)}>{run.status}</Badge>
              </strong>
            </div>
            <div className="metric-card">
              <span className="metric-label">通过</span>
              <strong>通过 {run.passedCount}</strong>
            </div>
            <div className="metric-card">
              <span className="metric-label">失败</span>
              <strong>失败 {run.failedCount}</strong>
            </div>
          </div>
          <div className="kv-grid">
            <div>
              <span>创建时间</span>
              <strong>{formatDateTime(run.createdAt)}</strong>
            </div>
            <div>
              <span>更新时间</span>
              <strong>{formatDateTime(run.updatedAt)}</strong>
            </div>
            <div>
              <span>完成时间</span>
              <strong>{formatDateTime(run.finishedAt)}</strong>
            </div>
          </div>
        </section>
      )}

      <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
        <p className="section-label">用例结果</p>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Case</th>
                <th>状态</th>
                <th>分数</th>
                <th>耗时</th>
                <th>Actual</th>
                <th>Expected</th>
                <th>Diff</th>
                <th>错误</th>
              </tr>
            </thead>
            <tbody>
              {runCases.map((runCase) => (
                <tr key={runCase.id} data-testid={`eval-run-case-row-${runCase.id}`}>
                  <td className="mono">{runCase.caseKey ?? runCase.caseId}</td>
                  <td>
                    <Badge tone={caseTone(runCase.status)}>{runCase.status}</Badge>
                  </td>
                  <td className="numeric">{runCase.score ?? '—'}</td>
                  <td className="numeric">{formatDurationMs(runCase.latencyMs)}</td>
                  <td>
                    <JsonPreview label={`actual-${runCase.id}`} value={runCase.actual} />
                  </td>
                  <td>
                    <JsonPreview label={`expected-${runCase.id}`} value={runCase.expected} />
                  </td>
                  <td>
                    <JsonPreview label={`diff-${runCase.id}`} value={runCase.diff} />
                  </td>
                  <td>{runCase.errorMessage || '—'}</td>
                </tr>
              ))}
              {casesQuery.isError && (
                <TableStateRow
                  state="error"
                  colSpan={8}
                  title="用例结果加载失败"
                  description="请刷新页面或稍后重试。"
                />
              )}
              {!casesQuery.isError && runCases.length === 0 && (
                <TableStateRow
                  state={casesQuery.isLoading ? 'loading' : 'empty'}
                  colSpan={8}
                  title={casesQuery.isLoading ? '加载中…' : '暂无用例结果'}
                />
              )}
            </tbody>
          </table>
        </div>
      </section>

      {runCases.some((runCase) => runCase.errorMessage) && (
        <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
          <p className="section-label">错误详情</p>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Case</th>
                  <th>状态</th>
                  <th>错误</th>
                </tr>
              </thead>
              <tbody>
                {runCases
                  .filter((runCase) => runCase.errorMessage)
                  .map((runCase) => (
                    <tr key={runCase.id}>
                      <td className="mono">{runCase.caseKey ?? runCase.caseId}</td>
                      <td>
                        <Badge tone={caseTone(runCase.status)}>{runCase.status}</Badge>
                      </td>
                      <td>{runCase.errorMessage}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {run?.report && Object.keys(run.report).length > 0 && (
        <section className="surface-box" style={{ display: 'grid', gap: 10 }} role="region" aria-label="报告 JSON">
          <p className="section-label">报告</p>
          <JsonPreview label="报告 JSON" value={run.report} />
        </section>
      )}
    </ConsolePage>
  )
}

export default EvalRunDetailPage
