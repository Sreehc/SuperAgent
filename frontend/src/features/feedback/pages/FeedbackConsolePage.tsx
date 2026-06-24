import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { listAdminFeedbacks } from '../api'
import type { ConversationFeedback, FeedbackRating } from '../types'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { ColumnDef } from '@tanstack/react-table'
import { listMessages } from '@/features/chat/api'
import { createEvalCase, listEvalSuites } from '@/features/evaluation/api'
import type { ConversationMessage } from '@/features/chat/types'
import type { EvalSuite } from '@/features/evaluation/types'
import { ConsolePage } from '../../../shared/ui/console-page'
import { DataTable } from '../../../shared/ui/data-table'
import { Badge } from '../../../shared/ui/badge'
import { Button } from '../../../shared/ui/button'
import { Dialog, DialogContent } from '../../../shared/ui/dialog'
import { DetailDrawer } from '../../../shared/ui/drawer'
import { FormField, SelectField } from '../../../shared/ui/form'
import { formatDateTime } from '@/shared/lib/format'

const RATING_LABEL: Record<FeedbackRating, string> = {
  up: '赞同',
  down: '不满意',
  correction: '更正',
}

const RATING_TONE: Record<FeedbackRating, 'success' | 'warning' | 'accent'> = {
  up: 'success',
  down: 'warning',
  correction: 'accent',
}

interface EvalDraft {
  feedback: ConversationFeedback
  suites: EvalSuite[]
  suiteId: string
  caseKey: string
  inputJson: string
  expectedJson: string
  warning: string | null
}

function optionalText(value: string | number | null | undefined, fallback = '—') {
  if (value === undefined || value === null || value === '') return fallback
  return `${value}`
}

function jsonPretty(value: Record<string, unknown>) {
  return JSON.stringify(value, null, 2)
}

function buildEvalDraft(feedback: ConversationFeedback, messages: ConversationMessage[], suites: EvalSuite[]): EvalDraft {
  const messageIndex = messages.findIndex((message) => message.id === feedback.messageId)
  const answer = messageIndex >= 0 ? messages[messageIndex]?.content ?? '' : ''
  const previousUserMessage =
    messageIndex >= 0
      ? [...messages.slice(0, messageIndex)].reverse().find((message) => message.role === 'user')
      : undefined
  const question = previousUserMessage?.content ?? ''
  const warning = question && answer ? null : '未能从会话消息中补齐原问题或原回答，请在草稿中手动补全。'

  return {
    feedback,
    suites,
    suiteId: suites[0]?.id ? `${suites[0].id}` : '',
    caseKey: `feedback-${feedback.messageId}`,
    inputJson: jsonPretty({
      question,
      answer,
      sessionId: feedback.sessionId,
      messageId: feedback.messageId,
      exchangeId: feedback.exchangeId,
      references: [],
    }),
    expectedJson: jsonPretty({
      correction: feedback.correction ?? '',
      rating: 'correction',
      criteria: '回答应符合纠错建议，并避免重复原错误。',
    }),
    warning,
  }
}

function feedbackColumns(onOpenDetail: (feedback: ConversationFeedback) => void): ColumnDef<ConversationFeedback, unknown>[] {
  return [
    { accessorKey: 'messageId', header: '消息 ID', cell: (c) => <span className="mono">{c.row.original.messageId}</span> },
    {
      accessorKey: 'rating',
      header: '评价',
      cell: (c) => <Badge tone={RATING_TONE[c.row.original.rating]}>{RATING_LABEL[c.row.original.rating]}</Badge>,
    },
    { accessorKey: 'comment', header: '评论', cell: (c) => c.row.original.comment || '—' },
    { accessorKey: 'correction', header: '更正建议', cell: (c) => c.row.original.correction || '—' },
    { accessorKey: 'createdAt', header: '时间', cell: (c) => formatDateTime(c.row.original.createdAt) },
    {
      id: 'actions',
      header: '操作',
      enableSorting: false,
      cell: (c) => (
        <Button size="sm" variant="secondary" onClick={() => onOpenDetail(c.row.original)}>
          查看反馈 {c.row.original.id}
        </Button>
      ),
    },
  ]
}

export function FeedbackConsolePage() {
  const queryClient = useQueryClient()
  const [rating, setRating] = useState<FeedbackRating | ''>('')
  const [selectedFeedback, setSelectedFeedback] = useState<ConversationFeedback | null>(null)
  const [evalDraft, setEvalDraft] = useState<EvalDraft | null>(null)
  const [draftError, setDraftError] = useState<string | null>(null)
  const [draftSuccess, setDraftSuccess] = useState<string | null>(null)
  const [draftLoading, setDraftLoading] = useState(false)
  const { data, isLoading } = useQuery({
    queryKey: ['admin-feedbacks', rating],
    queryFn: () => listAdminFeedbacks({ rating, pageSize: 50 }),
  })
  const createCaseMutation = useMutation({
    mutationFn: ({ suiteId, payload }: { suiteId: number; payload: { caseKey: string; input: Record<string, unknown>; expected: Record<string, unknown> } }) =>
      createEvalCase(suiteId, payload),
    onSuccess: async () => {
      setDraftError(null)
      setDraftSuccess('评测用例已创建。')
      await queryClient.invalidateQueries({ queryKey: ['eval-suites'] })
    },
    onError: () => {
      setDraftError('评测用例创建失败，请检查权限或稍后重试。')
    },
  })

  async function openEvalDraft(feedback: ConversationFeedback) {
    setDraftError(null)
    setDraftSuccess(null)
    setDraftLoading(true)
    try {
      const [messagesResult, suitesResult] = await Promise.all([
        listMessages(feedback.sessionId),
        listEvalSuites({ pageSize: 50 }),
      ])
      setEvalDraft(buildEvalDraft(feedback, messagesResult.data.items, suitesResult.data.items))
    } catch {
      setDraftError('评测用例草稿生成失败，请检查会话消息和评测套件权限。')
    } finally {
      setDraftLoading(false)
    }
  }

  async function submitEvalDraft() {
    if (!evalDraft) return
    setDraftError(null)
    setDraftSuccess(null)

    const suiteId = Number(evalDraft.suiteId)
    if (!Number.isInteger(suiteId) || suiteId <= 0) {
      setDraftError('请选择目标评测套件。')
      return
    }
    const caseKey = evalDraft.caseKey.trim()
    if (!caseKey) {
      setDraftError('请填写 Case Key。')
      return
    }

    let input: Record<string, unknown>
    let expected: Record<string, unknown>
    try {
      input = JSON.parse(evalDraft.inputJson) as Record<string, unknown>
      expected = JSON.parse(evalDraft.expectedJson) as Record<string, unknown>
    } catch {
      setDraftError('Input JSON 或 Expected JSON 格式不正确。')
      return
    }

    await createCaseMutation.mutateAsync({ suiteId, payload: { caseKey, input, expected } })
  }

  return (
    <ConsolePage title="反馈" description="汇总用户对回答的赞同、不满意与更正建议，用于质量分析与评测取数。">
      <div className="filter-row">
        <label className="field" style={{ maxWidth: 220 }}>
          <span>评价类型</span>
          <SelectField value={rating} onChange={(e) => setRating(e.target.value as FeedbackRating | '')}>
            <option value="">全部</option>
            <option value="up">赞同</option>
            <option value="down">不满意</option>
            <option value="correction">更正</option>
          </SelectField>
        </label>
      </div>
      <DataTable
        columns={feedbackColumns(setSelectedFeedback)}
        data={data?.data.items ?? []}
        emptyLabel="暂无反馈记录"
        loading={isLoading}
        rowTestId={(feedback) => `feedback-${feedback.id}`}
      />
      <DetailDrawer
        open={selectedFeedback != null}
        title={`反馈 #${selectedFeedback?.id ?? ''}`}
        description={
          selectedFeedback
            ? `${RATING_LABEL[selectedFeedback.rating]} · Message #${selectedFeedback.messageId}`
            : undefined
        }
        onOpenChange={(open) => {
          if (!open) setSelectedFeedback(null)
        }}
      >
        {selectedFeedback ? (
          <FeedbackDetailView
            feedback={selectedFeedback}
            isPreparingEvalDraft={draftLoading}
            onCreateEvalDraft={openEvalDraft}
          />
        ) : null}
      </DetailDrawer>
      <EvalDraftDialog
        draft={evalDraft}
        error={draftError}
        success={draftSuccess}
        isSaving={createCaseMutation.isPending}
        onDraftChange={setEvalDraft}
        onSubmit={submitEvalDraft}
        onOpenChange={(open) => {
          if (open) return
          setEvalDraft(null)
          setDraftError(null)
          setDraftSuccess(null)
        }}
      />
    </ConsolePage>
  )
}

function FeedbackDetailView({
  feedback,
  isPreparingEvalDraft,
  onCreateEvalDraft,
}: {
  feedback: ConversationFeedback
  isPreparingEvalDraft: boolean
  onCreateEvalDraft: (feedback: ConversationFeedback) => void
}) {
  return (
    <div className="trace-card-list">
      <article className="trace-observation feedback-detail">
        <div className="trace-observation__header">
          <div>
            <div className="meta-row">
              <Badge tone={RATING_TONE[feedback.rating]}>{RATING_LABEL[feedback.rating]}</Badge>
              <span className="mono">Actor #{feedback.actorUserId}</span>
            </div>
            <strong>反馈记录 #{feedback.id}</strong>
          </div>
          <div className="meta-row">
            <span className="metric-chip">{formatDateTime(feedback.createdAt)}</span>
          </div>
        </div>

        <dl className="reference-detail trace-detail-list">
          <div>
            <dt>评论</dt>
            <dd>{optionalText(feedback.comment, '未填写评论')}</dd>
          </div>
          <div>
            <dt>更正建议</dt>
            <dd>{optionalText(feedback.correction, '未提供更正建议')}</dd>
          </div>
          <div>
            <dt>会话</dt>
            <dd>
              <div className="meta-row">
                <span className="mono">Session #{feedback.sessionId}</span>
                <Button asChild size="sm" variant="text">
                  <Link to={`/chat/${feedback.sessionId}`}>打开会话 {feedback.sessionId}</Link>
                </Button>
              </div>
            </dd>
          </div>
          <div>
            <dt>消息</dt>
            <dd>
              <div className="meta-row">
                <span className="mono">Message #{feedback.messageId}</span>
                <span className="metadata">消息暂不支持直接跳转</span>
              </div>
            </dd>
          </div>
          <div>
            <dt>Trace</dt>
            <dd>
              {feedback.exchangeId ? (
                <div className="meta-row">
                  <span className="mono">Trace #{feedback.exchangeId}</span>
                  <Button asChild size="sm" variant="text">
                    <Link to={`/traces/${feedback.exchangeId}`}>打开 Trace {feedback.exchangeId}</Link>
                  </Button>
                </div>
              ) : (
                <span className="metadata">Trace 不可用</span>
              )}
            </dd>
          </div>
          <div>
            <dt>更新时间</dt>
            <dd className="mono">{formatDateTime(feedback.updatedAt)}</dd>
          </div>
          <div>
            <dt>Metadata</dt>
            <dd>
              <pre className="metadata-pre">{JSON.stringify(feedback.metadata ?? {}, null, 2)}</pre>
            </dd>
          </div>
        </dl>
        {feedback.rating === 'correction' && (
          <div className="action-row">
            <Button type="button" variant="primary" loading={isPreparingEvalDraft} onClick={() => onCreateEvalDraft(feedback)}>
              生成评测用例
            </Button>
          </div>
        )}
      </article>
    </div>
  )
}

function EvalDraftDialog({
  draft,
  error,
  success,
  isSaving,
  onDraftChange,
  onSubmit,
  onOpenChange,
}: {
  draft: EvalDraft | null
  error: string | null
  success: string | null
  isSaving: boolean
  onDraftChange: (draft: EvalDraft | null) => void
  onSubmit: () => void
  onOpenChange: (open: boolean) => void
}) {
  function updateDraft(patch: Partial<EvalDraft>) {
    if (!draft) return
    onDraftChange({ ...draft, ...patch })
  }

  return (
    <Dialog open={draft != null} onOpenChange={onOpenChange}>
      <DialogContent title="生成评测用例" className="eval-draft-dialog">
        {draft && (
          <div className="dialog-body">
            <p className="dialog-description">
              从反馈 #{draft.feedback.id} 生成用例草稿。创建新套件入口将在评测页补齐；这里先选择已有套件并创建用例。
            </p>
            {draft.warning && (
              <p className="error-banner" role="alert">
                {draft.warning}
              </p>
            )}
            {error && (
              <p className="error-banner" role="alert">
                {error}
              </p>
            )}
            {success && (
              <p className="success-banner" role="status">
                {success}
              </p>
            )}
            {draft.suites.length === 0 && (
              <p className="error-banner" role="alert">
                暂无可用评测套件，请先在评测页创建套件后再生成用例。
              </p>
            )}
            <FormField label="目标套件" htmlFor="feedback-eval-suite">
              <SelectField id="feedback-eval-suite" value={draft.suiteId} onChange={(event) => updateDraft({ suiteId: event.target.value })}>
                <option value="">选择评测套件</option>
                {draft.suites.map((suite) => (
                  <option key={suite.id} value={suite.id}>
                    {suite.name} ({suite.suiteKey})
                  </option>
                ))}
              </SelectField>
            </FormField>
            <FormField label="Case Key" htmlFor="feedback-eval-case-key">
              <input
                id="feedback-eval-case-key"
                value={draft.caseKey}
                onChange={(event) => updateDraft({ caseKey: event.target.value })}
              />
            </FormField>
            <FormField
              label="Input JSON"
              htmlFor="feedback-eval-input"
              hint="可编辑原问题、原回答、会话、消息和 Trace 信息。"
            >
              <textarea
                id="feedback-eval-input"
                rows={10}
                value={draft.inputJson}
                onChange={(event) => updateDraft({ inputJson: event.target.value })}
              />
            </FormField>
            <FormField label="Expected JSON" htmlFor="feedback-eval-expected" hint="默认写入 correction 和评分标准，可按评测需要调整。">
              <textarea
                id="feedback-eval-expected"
                rows={8}
                value={draft.expectedJson}
                onChange={(event) => updateDraft({ expectedJson: event.target.value })}
              />
            </FormField>
            <div className="dialog-footer">
              <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
                取消
              </Button>
              <Button type="button" variant="primary" loading={isSaving} disabled={draft.suites.length === 0} onClick={onSubmit}>
                创建评测用例
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

export default FeedbackConsolePage
