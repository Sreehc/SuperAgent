import { useEffect, useRef, useState } from 'react'
import { ChatCenteredText, Info } from '@phosphor-icons/react'
import { useNavigate, useParams } from 'react-router-dom'
import { DetailDrawer } from '@/shared/ui'
import { ConversationSurface } from '../components/ConversationSurface'
import { EvidenceInspector } from '../components/EvidenceInspector'
import { SessionRail } from '../components/SessionRail'
import { useChatStore } from '../store/chat'

function normalizeSessionId(value: string | undefined): number | null {
  if (!value) return null
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

export function ChatWorkspacePage() {
  const { sessionId } = useParams()
  const navigate = useNavigate()
  const selectedSessionId = useChatStore((state) => state.selectedSessionId)
  const firstParamRun = useRef(true)
  const [sessionsOpen, setSessionsOpen] = useState(false)
  const [inspectorOpen, setInspectorOpen] = useState(false)

  useEffect(() => {
    useChatStore.getState().bootstrap(normalizeSessionId(sessionId))
    return () => useChatStore.getState().clearOnRouteLeave()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (firstParamRun.current) {
      firstParamRun.current = false
      return
    }
    const sid = normalizeSessionId(sessionId)
    if (sid && sid !== useChatStore.getState().selectedSessionId) {
      useChatStore.getState().selectConversation(sid)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId])

  useEffect(() => {
    if (selectedSessionId && String(selectedSessionId) !== sessionId) {
      navigate(`/chat/${selectedSessionId}`, { replace: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedSessionId])

  return (
    <section className="chat-workspace">
      <div className="chat-workspace__mobile-actions" aria-label="聊天工具栏">
        <button className="btn btn-secondary btn-sm" type="button" onClick={() => setSessionsOpen(true)}>
          <ChatCenteredText size={15} aria-hidden="true" />
          会话
        </button>
        <button className="btn btn-secondary btn-sm" type="button" onClick={() => setInspectorOpen(true)}>
          <Info size={15} aria-hidden="true" />
          证据 / Trace
        </button>
      </div>
      <SessionRail />
      <ConversationSurface />
      <EvidenceInspector />
      <DetailDrawer open={sessionsOpen} title="会话" description="会话列表与管理" side="bottom" onOpenChange={setSessionsOpen}>
        <SessionRail onAfterNavigate={() => setSessionsOpen(false)} />
      </DetailDrawer>
      <DetailDrawer open={inspectorOpen} title="证据 / Trace" description="引用、推荐追问和执行链路" side="bottom" onOpenChange={setInspectorOpen}>
        <EvidenceInspector framed={false} />
      </DetailDrawer>
    </section>
  )
}
