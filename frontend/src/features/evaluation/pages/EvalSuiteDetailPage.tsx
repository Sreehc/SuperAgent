import { useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { createEvalCase, createEvalRun, deleteEvalCase, getEvalSuite } from '../api'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'
import { Button } from '../../../shared/ui/button'
import { ConfirmDialog, Dialog, DialogContent } from '../../../shared/ui/dialog'
import { FormField } from '../../../shared/ui/form'
import type { EvalCase } from '../types'

function runTone(status: string): 'success' | 'danger' | 'warning' | 'neutral' {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'running') return 'warning'
  return 'neutral'
}

function hasActiveRun(runs: { status: string }[] | undefined) {
  return (runs ?? []).some((run) => run.status === 'running' || run.status === 'pending')
}

const defaultInputJson = '{\n  "question": ""\n}'
const defaultExpectedJson = '{\n  "criteria": ""\n}'

function parseObjectJson(value: string): Record<string, unknown> | null {
  try {
    const parsed = JSON.parse(value)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return null
    }
    return parsed as Record<string, unknown>
  } catch {
    return null
  }
}

export function EvalSuiteDetailPage() {
  const { suiteId } = useParams()
  const id = Number(suiteId)
  const queryClient = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [caseKey, setCaseKey] = useState('')
  const [inputJson, setInputJson] = useState(defaultInputJson)
  const [expectedJson, setExpectedJson] = useState(defaultExpectedJson)
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [createError, setCreateError] = useState<string | null>(null)
  const [createSuccess, setCreateSuccess] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<EvalCase | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [startRunOpen, setStartRunOpen] = useState(false)
  const [startRunError, setStartRunError] = useState<string | null>(null)
  const [startRunSuccess, setStartRunSuccess] = useState<string | null>(null)
  const { data, isLoading } = useQuery({
    queryKey: ['eval-suite', id],
    queryFn: () => getEvalSuite(id),
    enabled: Number.isInteger(id) && id > 0,
    refetchInterval: (query) => (hasActiveRun(query.state.data?.data.recentRuns) ? 5000 : false),
  })
  const createCaseMutation = useMutation({
    mutationFn: (payload: { caseKey: string; input: Record<string, unknown>; expected: Record<string, unknown> }) =>
      createEvalCase(id, payload),
    onSuccess: async () => {
      setCreateError(null)
      setCreateSuccess('评测用例已添加。')
      setCaseKey('')
      setInputJson(defaultInputJson)
      setExpectedJson(defaultExpectedJson)
      await queryClient.invalidateQueries({ queryKey: ['eval-suite', id] })
    },
    onError: () => {
      setCreateError('评测用例创建失败，请检查权限或稍后重试。')
    },
  })
  const deleteCaseMutation = useMutation({
    mutationFn: (caseId: number) => deleteEvalCase(caseId),
    onSuccess: async () => {
      setDeleteError(null)
      await queryClient.invalidateQueries({ queryKey: ['eval-suite', id] })
    },
    onError: () => {
      setDeleteError('评测用例删除失败，请检查权限或稍后重试。')
    },
  })
  const startRunMutation = useMutation({
    mutationFn: () =>
      createEvalRun(id, {
        status: 'running',
        passedCount: 0,
        failedCount: 0,
        report: { source: 'manual-ui' },
      }),
    onSuccess: async () => {
      setStartRunError(null)
      setStartRunSuccess('评测运行已发起。')
      await queryClient.invalidateQueries({ queryKey: ['eval-suite', id] })
      await queryClient.invalidateQueries({ queryKey: ['eval-runs'] })
    },
    onError: () => {
      setStartRunError('评测运行发起失败，请检查权限或稍后重试。')
    },
  })

  const detail = data?.data

  function closeCreateDialog(open: boolean) {
    setCreateOpen(open)
    if (open) {
      setCreateSuccess(null)
      return
    }
    setFieldError(null)
    setCreateError(null)
  }

  async function submitCreateCase() {
    const trimmedCaseKey = caseKey.trim()
    setFieldError(null)
    setCreateError(null)
    setCreateSuccess(null)

    if (!trimmedCaseKey) {
      setFieldError('请填写 Case Key。')
      return
    }

    const input = parseObjectJson(inputJson)
    const expected = parseObjectJson(expectedJson)
    if (!input || !expected) {
      setFieldError('Input JSON 或 Expected JSON 格式不正确。')
      return
    }

    try {
      await createCaseMutation.mutateAsync({ caseKey: trimmedCaseKey, input, expected })
    } catch {
      // Error state is rendered via the mutation's onError handler.
    }
  }

  async function confirmDeleteCase() {
    if (!deleteTarget) return
    try {
      await deleteCaseMutation.mutateAsync(deleteTarget.id)
      setDeleteTarget(null)
    } catch {
      // Keep the confirmation dialog open so the user can retry or cancel.
      throw new Error('delete failed')
    }
  }

  async function confirmStartRun() {
    try {
      await startRunMutation.mutateAsync()
      setStartRunOpen(false)
    } catch {
      // Keep the confirmation dialog open so the user can retry or cancel.
      throw new Error('start run failed')
    }
  }

  return (
    <ConsolePage
      title={detail?.suite.name ?? '评测套件'}
      description={detail?.suite.description ?? '套件用例与最近运行记录。'}
      backTo="/evals"
      actions={
        detail ? (
          <>
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setStartRunError(null)
                setStartRunOpen(true)
              }}
            >
              发起评测运行
            </Button>
            <Button type="button" variant="primary" onClick={() => closeCreateDialog(true)}>
              添加评测用例
            </Button>
          </>
        ) : null
      }
    >
      {isLoading && <div className="empty-line">加载中…</div>}
      {detail && (
        <>
          {startRunSuccess && (
            <p className="success-banner" role="status">
              {startRunSuccess}
            </p>
          )}

          <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
            <p className="section-label">用例（{detail.cases.length}）</p>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Case Key</th>
                    <th>输入</th>
                    <th>期望</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.cases.map((evalCase) => (
                    <tr key={evalCase.id} data-testid={`eval-case-row-${evalCase.id}`}>
                      <td className="mono">{evalCase.caseKey}</td>
                      <td className="mono" style={{ maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {JSON.stringify(evalCase.input)}
                      </td>
                      <td className="mono" style={{ maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {JSON.stringify(evalCase.expected)}
                      </td>
                      <td>
                        <Button
                          type="button"
                          variant="danger"
                          size="sm"
                          aria-label={`删除用例 ${evalCase.caseKey}`}
                          onClick={() => {
                            setDeleteError(null)
                            setDeleteTarget(evalCase)
                          }}
                        >
                          删除
                        </Button>
                      </td>
                    </tr>
                  ))}
                  {detail.cases.length === 0 && (
                    <tr>
                      <td colSpan={4}>
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
                    <tr key={run.id} data-testid={`eval-run-row-${run.id}`}>
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

          <Dialog open={createOpen} onOpenChange={closeCreateDialog}>
            <DialogContent title="添加评测用例">
              <div className="dialog-body">
                <p className="dialog-description">为当前套件添加可回归执行的评测输入和期望结果。</p>
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
                <FormField label="Case Key" htmlFor="eval-case-key" error={fieldError?.includes('Case Key') ? fieldError : undefined}>
                  <input
                    id="eval-case-key"
                    value={caseKey}
                    placeholder="feedback-9001"
                    onChange={(event) => {
                      setFieldError(null)
                      setCaseKey(event.target.value)
                    }}
                  />
                </FormField>
                <FormField
                  label="Input JSON"
                  htmlFor="eval-case-input"
                  error={fieldError?.includes('Input JSON') ? fieldError : undefined}
                >
                  <textarea
                    id="eval-case-input"
                    rows={8}
                    value={inputJson}
                    onChange={(event) => {
                      setFieldError(null)
                      setInputJson(event.target.value)
                    }}
                  />
                </FormField>
                <FormField
                  label="Expected JSON"
                  htmlFor="eval-case-expected"
                >
                  <textarea
                    id="eval-case-expected"
                    rows={8}
                    value={expectedJson}
                    onChange={(event) => {
                      setFieldError(null)
                      setExpectedJson(event.target.value)
                    }}
                  />
                </FormField>
                <div className="dialog-footer">
                  <Button type="button" variant="ghost" onClick={() => closeCreateDialog(false)}>
                    取消
                  </Button>
                  <Button type="button" variant="primary" loading={createCaseMutation.isPending} onClick={submitCreateCase}>
                    添加用例
                  </Button>
                </div>
              </div>
            </DialogContent>
          </Dialog>

          <ConfirmDialog
            open={deleteTarget != null}
            title={`删除用例 ${deleteTarget?.caseKey ?? ''}`.trim()}
            description="删除后该用例不会继续参与新的评测运行，历史运行记录不受影响。"
            error={deleteError}
            confirmLabel="删除用例"
            tone="danger"
            onOpenChange={(open) => {
              if (!open) {
                setDeleteTarget(null)
                setDeleteError(null)
              }
            }}
            onConfirm={confirmDeleteCase}
          />

          <ConfirmDialog
            open={startRunOpen}
            title="发起评测运行"
            description="将为当前套件创建一条 running 状态的评测运行记录，并自动刷新运行状态。"
            error={startRunError}
            confirmLabel="发起运行"
            tone="primary"
            onOpenChange={(open) => {
              setStartRunOpen(open)
              if (!open) {
                setStartRunError(null)
              }
            }}
            onConfirm={confirmStartRun}
          />
        </>
      )}
    </ConsolePage>
  )
}

export default EvalSuiteDetailPage
