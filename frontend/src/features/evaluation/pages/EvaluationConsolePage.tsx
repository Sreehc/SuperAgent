import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { listEvalRuns, listEvalSuites } from '../api'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'

function runTone(status: string): 'success' | 'danger' | 'warning' | 'neutral' {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'running') return 'warning'
  return 'neutral'
}

export function EvaluationConsolePage() {
  const navigate = useNavigate()
  const suitesQuery = useQuery({
    queryKey: ['eval-suites'],
    queryFn: () => listEvalSuites({ pageSize: 50 }),
  })
  const runsQuery = useQuery({
    queryKey: ['eval-runs'],
    queryFn: () => listEvalRuns({ pageSize: 50 }),
  })

  return (
    <ConsolePage title="评测" description="评测套件与运行记录，用于回归质量与对比模型表现。">
      <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
        <p className="section-label">评测套件</p>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Key</th>
                <th>名称</th>
                <th>用例数</th>
                <th>运行数</th>
              </tr>
            </thead>
            <tbody>
              {(suitesQuery.data?.data.items ?? []).map((suite) => (
                <tr
                  key={suite.id}
                  data-testid={`eval-suite-row-${suite.id}`}
                  style={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/evals/${suite.id}`)}
                >
                  <td className="mono">{suite.suiteKey}</td>
                  <td>{suite.name}</td>
                  <td className="numeric">{suite.caseCount}</td>
                  <td className="numeric">{suite.runCount}</td>
                </tr>
              ))}
              {(suitesQuery.data?.data.items.length ?? 0) === 0 && (
                <tr>
                  <td colSpan={4}>
                    <div className="empty-line">{suitesQuery.isLoading ? '加载中…' : '暂无评测套件'}</div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
        <p className="section-label">运行记录</p>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>#</th>
                <th>套件</th>
                <th>状态</th>
                <th>通过</th>
                <th>失败</th>
              </tr>
            </thead>
            <tbody>
              {(runsQuery.data?.data.items ?? []).map((run) => (
                <tr
                  key={run.id}
                  style={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/evals/runs/${run.id}`)}
                >
                  <td className="numeric">{run.id}</td>
                  <td className="mono">{run.suiteKey}</td>
                  <td>
                    <Badge tone={runTone(run.status)}>{run.status}</Badge>
                  </td>
                  <td className="numeric">{run.passedCount}</td>
                  <td className="numeric">{run.failedCount}</td>
                </tr>
              ))}
              {(runsQuery.data?.data.items.length ?? 0) === 0 && (
                <tr>
                  <td colSpan={5}>
                    <div className="empty-line">{runsQuery.isLoading ? '加载中…' : '暂无运行记录'}</div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </ConsolePage>
  )
}

export default EvaluationConsolePage
