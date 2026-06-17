import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getEvalSuite } from '../api'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'

function runTone(status: string): 'success' | 'danger' | 'warning' | 'neutral' {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'running') return 'warning'
  return 'neutral'
}

export function EvalSuiteDetailPage() {
  const { suiteId } = useParams()
  const id = Number(suiteId)
  const { data, isLoading } = useQuery({
    queryKey: ['eval-suite', id],
    queryFn: () => getEvalSuite(id),
    enabled: Number.isInteger(id) && id > 0,
  })

  const detail = data?.data

  return (
    <ConsolePage
      title={detail?.suite.name ?? '评测套件'}
      description={detail?.suite.description ?? '套件用例与最近运行记录。'}
      backTo="/evals"
    >
      {isLoading && <div className="empty-line">加载中…</div>}
      {detail && (
        <>
          <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
            <p className="section-label">用例（{detail.cases.length}）</p>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Case Key</th>
                    <th>输入</th>
                    <th>期望</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.cases.map((evalCase) => (
                    <tr key={evalCase.id}>
                      <td className="mono">{evalCase.caseKey}</td>
                      <td className="mono" style={{ maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {JSON.stringify(evalCase.input)}
                      </td>
                      <td className="mono" style={{ maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {JSON.stringify(evalCase.expected)}
                      </td>
                    </tr>
                  ))}
                  {detail.cases.length === 0 && (
                    <tr>
                      <td colSpan={3}>
                        <div className="empty-line">暂无用例</div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </section>

          <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
            <p className="section-label">最近运行</p>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>状态</th>
                    <th>通过</th>
                    <th>失败</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.recentRuns.map((run) => (
                    <tr key={run.id}>
                      <td className="numeric">{run.id}</td>
                      <td>
                        <Badge tone={runTone(run.status)}>{run.status}</Badge>
                      </td>
                      <td className="numeric">{run.passedCount}</td>
                      <td className="numeric">{run.failedCount}</td>
                    </tr>
                  ))}
                  {detail.recentRuns.length === 0 && (
                    <tr>
                      <td colSpan={4}>
                        <div className="empty-line">暂无运行</div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </ConsolePage>
  )
}

export default EvalSuiteDetailPage
