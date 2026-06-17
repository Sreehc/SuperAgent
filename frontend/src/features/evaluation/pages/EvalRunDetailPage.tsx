import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getEvalRun, listEvalRunCases } from '../api'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'

function caseTone(status: string): 'success' | 'danger' | 'warning' | 'neutral' {
  if (status === 'passed') return 'success'
  if (status === 'failed' || status === 'error') return 'danger'
  if (status === 'skipped') return 'neutral'
  return 'neutral'
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

  return (
    <ConsolePage
      title={`运行 #${runId}`}
      description={run ? `套件 ${run.suiteKey} · 通过 ${run.passedCount} / 失败 ${run.failedCount}` : '运行明细'}
      backTo="/evals"
    >
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
                <th>错误</th>
              </tr>
            </thead>
            <tbody>
              {(casesQuery.data?.data ?? []).map((runCase) => (
                <tr key={runCase.id}>
                  <td className="mono">{runCase.caseKey ?? runCase.caseId}</td>
                  <td>
                    <Badge tone={caseTone(runCase.status)}>{runCase.status}</Badge>
                  </td>
                  <td className="numeric">{runCase.score ?? '—'}</td>
                  <td className="numeric">{runCase.latencyMs != null ? `${runCase.latencyMs}ms` : '—'}</td>
                  <td>{runCase.errorMessage || '—'}</td>
                </tr>
              ))}
              {(casesQuery.data?.data.length ?? 0) === 0 && (
                <tr>
                  <td colSpan={5}>
                    <div className="empty-line">{casesQuery.isLoading ? '加载中…' : '暂无用例结果'}</div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {run?.report && Object.keys(run.report).length > 0 && (
        <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
          <p className="section-label">报告</p>
          <pre className="metadata">{JSON.stringify(run.report, null, 2)}</pre>
        </section>
      )}
    </ConsolePage>
  )
}

export default EvalRunDetailPage
