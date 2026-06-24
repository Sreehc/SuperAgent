import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { createEvalSuite, listEvalRuns, listEvalSuites } from '../api'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'
import { Button } from '../../../shared/ui/button'
import { Dialog, DialogContent } from '../../../shared/ui/dialog'
import { FormField } from '../../../shared/ui/form'

function runTone(status: string): 'success' | 'danger' | 'warning' | 'neutral' {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'running') return 'warning'
  return 'neutral'
}

function hasActiveRun(runs: { status: string }[] | undefined) {
  return (runs ?? []).some((run) => run.status === 'running' || run.status === 'pending')
}

export function EvaluationConsolePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [suiteKey, setSuiteKey] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [createError, setCreateError] = useState<string | null>(null)
  const [createSuccess, setCreateSuccess] = useState<string | null>(null)
  const suitesQuery = useQuery({
    queryKey: ['eval-suites'],
    queryFn: () => listEvalSuites({ pageSize: 50 }),
  })
  const runsQuery = useQuery({
    queryKey: ['eval-runs'],
    queryFn: () => listEvalRuns({ pageSize: 50 }),
    refetchInterval: (query) => (hasActiveRun(query.state.data?.data.items) ? 5000 : false),
  })
  const createSuiteMutation = useMutation({
    mutationFn: (payload: { suiteKey: string; name: string; description?: string }) => createEvalSuite(payload),
    onSuccess: async () => {
      setCreateError(null)
      setCreateSuccess('评测套件已创建。')
      setSuiteKey('')
      setName('')
      setDescription('')
      await queryClient.invalidateQueries({ queryKey: ['eval-suites'] })
    },
    onError: () => {
      setCreateError('评测套件创建失败，请检查权限或稍后重试。')
    },
  })

  function closeCreateDialog(open: boolean) {
    setCreateOpen(open)
    if (open) {
      setCreateSuccess(null)
      return
    }
    setFieldError(null)
    setCreateError(null)
  }

  async function submitCreateSuite() {
    const trimmedKey = suiteKey.trim()
    const trimmedName = name.trim()
    const trimmedDescription = description.trim()
    setCreateError(null)
    setCreateSuccess(null)

    if (!trimmedKey) {
      setFieldError('请填写 Suite Key。')
      return
    }
    if (!trimmedName) {
      setFieldError('请填写名称。')
      return
    }

    setFieldError(null)
    try {
      await createSuiteMutation.mutateAsync({
        suiteKey: trimmedKey,
        name: trimmedName,
        ...(trimmedDescription ? { description: trimmedDescription } : {}),
      })
    } catch {
      // Error state is rendered via the mutation's onError handler.
    }
  }

  return (
    <ConsolePage
      title="评测"
      description="评测套件与运行记录，用于回归质量与对比模型表现。"
      actions={
        <Button type="button" variant="primary" onClick={() => closeCreateDialog(true)}>
          新建评测套件
        </Button>
      }
    >
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

      <Dialog open={createOpen} onOpenChange={closeCreateDialog}>
        <DialogContent title="新建评测套件">
          <div className="dialog-body">
            <p className="dialog-description">创建用于回归验证和模型效果对比的评测套件。</p>
            {createError && (
              <p className="error-banner" role="alert">
                {createError}
              </p>
            )}
            {createSuccess && (
              <p className="success-banner" role="status">
                {createSuccess}
              </p>
            )}
            <FormField label="Suite Key" htmlFor="eval-suite-key" error={fieldError?.includes('Suite Key') ? fieldError : undefined}>
              <input
                id="eval-suite-key"
                value={suiteKey}
                placeholder="refund-regression"
                onChange={(event) => {
                  setFieldError(null)
                  setSuiteKey(event.target.value)
                }}
              />
            </FormField>
            <FormField label="名称" htmlFor="eval-suite-name" error={fieldError?.includes('名称') ? fieldError : undefined}>
              <input
                id="eval-suite-name"
                value={name}
                placeholder="退款问答回归"
                onChange={(event) => {
                  setFieldError(null)
                  setName(event.target.value)
                }}
              />
            </FormField>
            <FormField label="描述" htmlFor="eval-suite-description" hint="可选，用于说明套件覆盖的业务范围。">
              <textarea
                id="eval-suite-description"
                rows={4}
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </FormField>
            <div className="dialog-footer">
              <Button type="button" variant="ghost" onClick={() => closeCreateDialog(false)}>
                取消
              </Button>
              <Button type="button" variant="primary" loading={createSuiteMutation.isPending} onClick={submitCreateSuite}>
                创建套件
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </ConsolePage>
  )
}

export default EvaluationConsolePage
