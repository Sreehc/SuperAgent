import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { selectFilteredConversations, useChatStore } from '../store/chat'
import type { DisplayReference } from '../types'

export function EvidenceInspector() {
  const navigate = useNavigate()
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

  const detail = active ?? streamReference

  return (
    <aside className="evidence-inspector inspector-box">
      <div className="evidence-inspector__header">
        <h2>证据 / Trace</h2>
        {streamState.stage && <span className="status-chip">{streamState.stage}</span>}
      </div>

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
          <button className="btn btn-secondary btn-sm" type="button" onClick={() => navigate(`/documents/${detail.documentId}`)}>
            查看文档
          </button>
        </div>
      ) : (
        <div className="empty-line">发送消息后，这里会显示检索证据与执行链路。</div>
      )}

      {references.length > 0 && (
        <div className="chip-row">
          {references.map((reference) => (
            <button
              key={`${reference.documentId}-${reference.chunkId}-${reference.ordinal}`}
              type="button"
              className="reference-chip"
              data-testid="chat-reference-chip"
              onClick={() => setActive(reference)}
            >
              [{reference.ordinal}] {reference.title}
            </button>
          ))}
        </div>
      )}

      {streamState.timeline.length > 0 && (
        <div className="run-timeline">
          <h3>执行链路</h3>
          {streamState.timeline.map((item, index) => (
            <div key={index} className="run-step">
              <span aria-hidden="true" />
              <div>
                <strong>{item.title}</strong>
                <p>{item.summary}</p>
              </div>
            </div>
          ))}
        </div>
      )}

      {streamState.recommendations.length > 0 && (
        <div className="recommendations">
          <p className="section-label">推荐追问</p>
          {streamState.recommendations.map((question) => (
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
      )}

      {streamState.error && <p className="error-banner">{streamState.error}</p>}
    </aside>
  )
}
