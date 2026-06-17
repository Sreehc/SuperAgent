import { useNavigate } from 'react-router-dom'
import { Archive, PencilSimple, Plus, Trash } from '@phosphor-icons/react'
import { selectFilteredConversations, useChatStore } from '../store/chat'
import { formatChatTime } from '../utils/presentation'

export function SessionRail() {
  const navigate = useNavigate()
  const conversations = useChatStore(selectFilteredConversations)
  const selectedSessionId = useChatStore((s) => s.selectedSessionId)
  const keyword = useChatStore((s) => s.keyword)

  async function createConversation() {
    const id = await useChatStore.getState().createAndSelectConversation()
    if (id) navigate(`/chat/${id}`)
  }

  async function select(sessionId: number) {
    await useChatStore.getState().selectConversation(sessionId)
    navigate(`/chat/${sessionId}`)
  }

  async function rename(sessionId: number, current: string) {
    const next = window.prompt('重命名会话', current)
    if (next === null) return
    await useChatStore.getState().renameConversation(sessionId, next)
  }

  async function archive(sessionId: number) {
    await useChatStore.getState().archiveConversation(sessionId)
  }

  async function remove(sessionId: number) {
    if (!window.confirm('删除后无法恢复，确认继续吗？')) return
    await useChatStore.getState().removeConversation(sessionId)
  }

  return (
    <aside className="session-rail">
      <div className="session-rail__top">
        <h2>会话</h2>
        <button className="btn btn-primary btn-sm" type="button" data-testid="chat-new-conversation" onClick={createConversation}>
          <Plus size={15} aria-hidden="true" /> 新建
        </button>
      </div>

      <input
        className="input-control"
        placeholder="搜索会话…"
        value={keyword}
        onChange={(event) => useChatStore.getState().setKeyword(event.target.value)}
      />

      <div className="conversation-list">
        {conversations.length === 0 && <div className="empty-line">还没有会话，点击「新建」开始。</div>}
        {conversations.map((conversation) => (
          <div
            key={conversation.id}
            className={`conversation-row${conversation.id === selectedSessionId ? ' conversation-row--active' : ''}`}
          >
            <button className="conversation-row__main" type="button" onClick={() => select(conversation.id)}>
              <strong>{conversation.title || '未命名会话'}</strong>
              <small>{formatChatTime(conversation.lastMessageAt)}</small>
            </button>
            <div className="conversation-row__actions">
              <button className="btn-text btn-sm" type="button" onClick={() => rename(conversation.id, conversation.title)}>
                <PencilSimple size={13} aria-hidden="true" /> 改名
              </button>
              <button className="btn-text btn-sm" type="button" onClick={() => archive(conversation.id)}>
                <Archive size={13} aria-hidden="true" /> 归档
              </button>
              <button className="btn-text btn-sm danger-text" type="button" onClick={() => remove(conversation.id)}>
                <Trash size={13} aria-hidden="true" /> 删除
              </button>
            </div>
          </div>
        ))}
      </div>
    </aside>
  )
}
