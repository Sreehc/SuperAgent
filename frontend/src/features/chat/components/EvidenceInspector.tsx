import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { cn } from '@/lib/cn'
import { DetailInspector, Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui'
import { selectFilteredConversations, useChatStore } from '../store/chat'
import type { DisplayReference, RunTimelineItem } from '../types'

interface EvidenceInspectorProps {
  framed?: boolean
  className?: string
}

export function EvidenceInspector({ framed = true, className }: EvidenceInspectorProps) {
  const messages = useChatStore((s) => s.messages)
  const streamReference = useChatStore((s) => s.selectedReference)
  const streamState = useChatStore((s) => s.streamState)
  // Touch the conversations selector so the inspector refreshes with the rail.
  useChatStore(selectFilteredConversations)
  const [active, setActive] = useState<DisplayReference | null>(null)

  const references = useMemo(() => {
    const latestAssistant = [...messages].reverse().find((m) => m.role === 'assistant')
    return latestAssistant?.references ?? []
  }, [messages])

  const detail = active ?? streamReference ?? references[0] ?? null
  const traceItems = streamState.timeline.filter((item) => item.type === 'trace_stage' || item.type === 'agent_step')
  const toolItems = streamState.timeline.filter((item) => item.type === 'tool_start' || item.type === 'tool_result')
  const checkpointItems = streamState.timeline.filter((item) => item.type === 'checkpoint' || item.type === 'resume')

  const content = (
    <>
      {streamState.error && (
        <p className="error-banner" role="alert">
          {streamState.error}
        </p>
      )}
      <Tabs defaultValue="evidence" className="inspector-tabs">
        <TabsList className="inspector-tabs__list" aria-label="检查器分组">
          <TabsTrigger value="evidence">证据 {references.length}</TabsTrigger>
          <TabsTrigger value="trace">Trace {traceItems.length}</TabsTrigger>
          <TabsTrigger value="tools">工具 {toolItems.length}</TabsTrigger>
          <TabsTrigger value="checkpoints">Checkpoint {checkpointItems.length}</TabsTrigger>
          <TabsTrigger value="recommendations">追问 {streamState.recommendations.length}</TabsTrigger>
        </TabsList>

        <TabsContent value="evidence" className="inspector-tab-panel">
          <EvidenceTab detail={detail} references={references} onSelectReference={setActive} />
        </TabsContent>

        <TabsContent value="trace" className="inspector-tab-panel">
          <TraceTab exchangeId={streamState.exchangeId} runId={streamState.runId} stage={streamState.stage} items={traceItems} />
        </TabsContent>

        <TabsContent value="tools" className="inspector-tab-panel">
          <TimelinePanel title="工具调用" emptyLabel="暂无工具调用。" items={toolItems} />
        </TabsContent>

        <TabsContent value="checkpoints" className="inspector-tab-panel">
          <TimelinePanel title="Checkpoint" emptyLabel="暂无 Checkpoint 或恢复记录。" items={checkpointItems} />
        </TabsContent>

        <TabsContent value="recommendations" className="inspector-tab-panel">
          <RecommendationsTab questions={streamState.recommendations} />
        </TabsContent>
      </Tabs>
    </>
  )

  if (!framed) {
    return <div className={cn('evidence-inspector__drawer-body', className)}>{content}</div>
  }

  return (
    <DetailInspector title="证据 / Trace" meta={streamState.stage && <span className="status-chip">{streamState.stage}</span>} className={cn('evidence-inspector', className)}>
      {content}
    </DetailInspector>
  )
}

function EvidenceTab({
  detail,
  references,
  onSelectReference,
}: {
  detail: DisplayReference | null
  references: DisplayReference[]
  onSelectReference: (reference: DisplayReference) => void
}) {
  return (
    <div className="inspector-section">
      {detail ? (
        <div className="reference-panel reference-detail">
          <p className="section-label">引用来源</p>
          <h3>{detail.title}</h3>
          <blockquote>{detail.quote}</blockquote>
          {detail.score != null && (
            <div className="score-meter">
              <span>相关度</span>
              <strong>{detail.score.toFixed(2)}</strong>
              <div>
                <i style={{ width: `${Math.min(100, Math.round(detail.score * 100))}%` }} />
              </div>
            </div>
          )}
          <Link className="btn btn-secondary btn-sm" to={`/documents/${detail.documentId}`}>
            查看文档
          </Link>
        </div>
      ) : (
        <div className="empty-line">发送消息后，这里会显示检索证据。</div>
      )}

      {references.length > 0 && (
        <div className="chip-row">
          {references.map((reference) => (
            <button
              key={`${reference.documentId}-${reference.chunkId}-${reference.ordinal}`}
              type="button"
              className="reference-chip"
              data-testid="chat-reference-chip"
              onClick={() => onSelectReference(reference)}
            >
              [{reference.ordinal}] {reference.title}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function TraceTab({
  exchangeId,
  runId,
  stage,
  items,
}: {
  exchangeId: number | null
  runId: number | null
  stage: string | null
  items: RunTimelineItem[]
}) {
  return (
    <div className="inspector-section">
      <div className="inspector-meta-grid">
        <span>
          <small>Exchange</small>
          <strong>{exchangeId ?? '—'}</strong>
        </span>
        <span>
          <small>Run</small>
          <strong>{runId ?? '—'}</strong>
        </span>
        <span>
          <small>阶段</small>
          <strong>{stage ?? '—'}</strong>
        </span>
      </div>
      {exchangeId != null && (
        <Link className="btn btn-secondary btn-sm" to={`/traces/${exchangeId}`}>
          打开 Trace
        </Link>
      )}
      <TimelinePanel title="Trace 阶段" emptyLabel="暂无 Trace 阶段。" items={items} />
    </div>
  )
}

function TimelinePanel({ title, emptyLabel, items }: { title: string; emptyLabel: string; items: RunTimelineItem[] }) {
  if (items.length === 0) {
    return <div className="empty-line">{emptyLabel}</div>
  }

  return (
    <div className="run-timeline">
      <h3>{title}</h3>
      {items.map((item, index) => (
        <div key={`${item.type}-${item.title}-${index}`} className={`run-step run-step--${item.type}`}>
          <span aria-hidden="true" />
          <div>
            <strong>{item.title}</strong>
            <p>{item.summary}</p>
          </div>
        </div>
      ))}
    </div>
  )
}

function RecommendationsTab({ questions }: { questions: string[] }) {
  if (questions.length === 0) {
    return <div className="empty-line">暂无推荐追问。</div>
  }

  return (
    <div className="recommendations">
      <p className="section-label">推荐追问</p>
      {questions.map((question) => (
        <button
          key={question}
          type="button"
          className="btn btn-ghost btn-sm"
          onClick={() => useChatStore.getState().useRecommendation(question)}
        >
          {question}
        </button>
      ))}
    </div>
  )
}
