import { useEffect, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
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
      <SessionRail />
      <ConversationSurface />
      <EvidenceInspector />
    </section>
  )
}
