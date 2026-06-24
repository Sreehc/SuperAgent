import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Archive, PencilSimple, Plus, Trash } from '@phosphor-icons/react'
import { selectFilteredConversations, useChatStore } from '../store/chat'
import { formatChatTime } from '../utils/presentation'
import { ConfirmDialog, EmptyState, PromptDialog } from '@/shared/ui'

interface ConversationActionTarget {
  sessionId: number
  title: string
}

interface SessionRailProps {
  onAfterNavigate?: () => void
}

export function SessionRail({ onAfterNavigate }: SessionRailProps = {}) {
  const navigate = useNavigate()
  const conversations = useChatStore(selectFilteredConversations)
  const selectedSessionId = useChatStore((s) => s.selectedSessionId)
  const keyword = useChatStore((s) => s.keyword)
  const [renameTarget, setRenameTarget] = useState<ConversationActionTarget | null>(null)
  const [archiveTarget, setArchiveTarget] = useState<ConversationActionTarget | null>(null)
  const [removeTarget, setRemoveTarget] = useState<ConversationActionTarget | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const hasOpenDialog = renameTarget != null || archiveTarget != null || removeTarget != null
  const emptyTitle = keyword.trim() ? '无匹配会话' : '暂无会话'
  const emptyDescription = keyword.trim() ? '没有匹配当前关键词的会话。' : '还没有会话，可以从这里开始一次新对话。'

  async function createConversation() {
    setActionError(null)
    try {
      const id = await useChatStore.getState().createAndSelectConversation()
      if (id) {
        navigate(`/chat/${id}`)
        onAfterNavigate?.()
      }
    } catch {
      setActionError('新建会话失败，请稍后重试。')
    }
  }

  async function select(sessionId: number) {
    setActionError(null)
    try {
      await useChatStore.getState().selectConversation(sessionId)
      navigate(`/chat/${sessionId}`)
      onAfterNavigate?.()
    } catch {
      setActionError('切换会话失败，请稍后重试。')
    }
  }

  async function confirmRename(nextTitle: string) {
    if (!renameTarget) return
    setActionError(null)
    try {
      await useChatStore.getState().renameConversation(renameTarget.sessionId, nextTitle)
      setRenameTarget(null)
    } catch (error) {
      setActionError('重命名会话失败，请稍后重试。')
      throw error
    }
  }

  async function confirmArchive() {
    if (!archiveTarget) return
    setActionError(null)
    try {
      await useChatStore.getState().archiveConversation(archiveTarget.sessionId)
      setArchiveTarget(null)
    } catch (error) {
      setActionError('归档会话失败，请稍后重试。')
      throw error
    }
  }

  async function confirmRemove() {
    if (!removeTarget) return
    setActionError(null)
    try {
      await useChatStore.getState().removeConversation(removeTarget.sessionId)
      setRemoveTarget(null)
    } catch (error) {
      setActionError('删除会话失败，请稍后重试。')
      throw error
    }
  }

  function openRename(target: ConversationActionTarget) {
    setActionError(null)
    setRenameTarget(target)
  }

  function openArchive(target: ConversationActionTarget) {
    setActionError(null)
    setArchiveTarget(target)
  }

  function openRemove(target: ConversationActionTarget) {
    setActionError(null)
    setRemoveTarget(target)
  }

  function closeDialogs() {
    setActionError(null)
    setRenameTarget(null)
    setArchiveTarget(null)
    setRemoveTarget(null)
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
        {actionError && !hasOpenDialog && (
          <p className="error-banner session-rail__error" role="alert">
            {actionError}
          </p>
        )}
        {conversations.length === 0 && (
          <EmptyState
            title={emptyTitle}
            description={emptyDescription}
            size="compact"
            className="session-rail__empty"
            action={
              <button className="btn btn-primary btn-sm" type="button" onClick={createConversation}>
                <Plus size={15} aria-hidden="true" /> 新建会话
              </button>
            }
          />
        )}
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
              <button
                className="btn-text btn-sm"
                type="button"
                onClick={() => openRename({ sessionId: conversation.id, title: conversation.title })}
              >
                <PencilSimple size={13} aria-hidden="true" /> 改名
              </button>
              <button
                className="btn-text btn-sm"
                type="button"
                onClick={() => openArchive({ sessionId: conversation.id, title: conversation.title })}
              >
                <Archive size={13} aria-hidden="true" /> 归档
              </button>
              <button
                className="btn-text btn-sm danger-text"
                type="button"
                onClick={() => openRemove({ sessionId: conversation.id, title: conversation.title })}
              >
                <Trash size={13} aria-hidden="true" /> 删除
              </button>
            </div>
          </div>
        ))}
      </div>
      <PromptDialog
        open={renameTarget != null}
        title="重命名会话"
        label="会话名称"
        defaultValue={renameTarget?.title ?? ''}
        error={renameTarget ? actionError : null}
        confirmLabel="保存"
        cancelLabel="取消"
        required
        onConfirm={confirmRename}
        onOpenChange={(open) => !open && closeDialogs()}
      />
      <ConfirmDialog
        open={archiveTarget != null}
        title="归档会话"
        description={`归档「${archiveTarget?.title || '未命名会话'}」后，它会从当前会话列表中移除。`}
        error={archiveTarget ? actionError : null}
        confirmLabel="确认归档"
        cancelLabel="取消"
        tone="primary"
        onConfirm={confirmArchive}
        onOpenChange={(open) => !open && closeDialogs()}
      />
      <ConfirmDialog
        open={removeTarget != null}
        title="删除会话"
        description={`删除「${removeTarget?.title || '未命名会话'}」后无法恢复，确认继续吗？`}
        error={removeTarget ? actionError : null}
        confirmLabel="确认删除"
        cancelLabel="取消"
        tone="danger"
        onConfirm={confirmRemove}
        onOpenChange={(open) => !open && closeDialogs()}
      />
    </aside>
  )
}
