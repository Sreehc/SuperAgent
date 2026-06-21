import {
  AssistantRuntimeProvider,
  ComposerPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
  useMessage,
} from '@assistant-ui/react'
import { useChatRuntime } from '../runtime'
import { useChatStore } from '../store/chat'
import type { RequestedExecutionMode } from '../types'
import { ExecutionSummary } from './ExecutionSummary'
import { KnowledgeBasePicker } from './KnowledgeBasePicker'
import { MarkdownText } from './MarkdownText'
import { MessageActions } from './MessageActions'

function UserMessage() {
  return (
    <div className="message-block message-block--user">
      <div className="message-block__shell">
        <div className="message-block__meta">你</div>
        <div className="message-block__content">
          <MessagePrimitive.Parts />
        </div>
      </div>
    </div>
  )
}

function AssistantMessage() {
  return (
    <div className="message-block message-block--assistant">
      <div className="message-block__shell">
        <div className="message-block__meta">Assistant</div>
        <div className="message-block__content">
          <MessagePrimitive.Parts components={{ Text: MarkdownText }} />
        </div>
        <AssistantExecutionSummary />
        <AssistantMessageActions />
      </div>
    </div>
  )
}

function AssistantExecutionSummary() {
  const assistantMessageId = useMessage((message) => message.id)
  const latestAssistantId = useChatStore((s) => findLatestAssistantId(s.messages))
  const streamState = useChatStore((s) => s.streamState)

  if (String(latestAssistantId) !== assistantMessageId || streamState.timeline.length === 0) {
    return null
  }

  return <ExecutionSummary items={streamState.timeline} />
}

function AssistantMessageActions() {
  const assistantMessageId = useMessage((message) => message.id)
  const message = useChatStore((s) => s.messages.find((item) => String(item.id) === assistantMessageId))
  const updatingFeedbackMessageId = useChatStore((s) => s.updatingFeedbackMessageId)

  if (!message) {
    return null
  }

  return (
    <MessageActions
      message={message}
      updating={updatingFeedbackMessageId === message.id}
      onFeedback={(target, rating) => useChatStore.getState().setMessageFeedback(target, rating)}
      onCorrection={(target, correction) => useChatStore.getState().correctMessageFeedback(target, correction)}
      onClearFeedback={(target) => useChatStore.getState().clearMessageFeedback(target)}
    />
  )
}

function findLatestAssistantId(messages: { id: number; role: string }[]) {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (messages[index].role === 'assistant') {
      return messages[index].id
    }
  }
  return null
}

const MODES: { value: RequestedExecutionMode; label: string }[] = [
  { value: 'AUTO', label: '自动' },
  { value: 'RAG_QA', label: 'RAG 问答' },
  { value: 'REACT_AGENT', label: 'ReAct Agent' },
]

export function ConversationSurface() {
  const runtime = useChatRuntime()
  const conversation = useChatStore((s) => s.selectedConversation)

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <section className="conversation-surface">
        <ThreadPrimitive.Root className="thread-root">
          <header className="conversation-header">
            <h1>{conversation?.title || '新的对话'}</h1>
          </header>
          <ThreadPrimitive.Viewport className="message-region">
            <ThreadPrimitive.Empty>
              <div className="empty-line">向 agent 提问，开始一次新的执行。</div>
            </ThreadPrimitive.Empty>
            <div className="message-list">
              <ThreadPrimitive.Messages components={{ UserMessage, AssistantMessage }} />
            </div>
          </ThreadPrimitive.Viewport>
          <StreamStatusNotice />
          <Composer />
        </ThreadPrimitive.Root>
      </section>
    </AssistantRuntimeProvider>
  )
}

function StreamStatusNotice() {
  const streamStatus = useChatStore((s) => s.streamStatus)
  const streamState = useChatStore((s) => s.streamState)

  if (streamStatus === 'error' && streamState.error) {
    return (
      <div className="stream-status stream-status--error" role="alert">
        <div>
          <strong>{streamState.error}</strong>
          <p>已保留当前消息内容。</p>
        </div>
        <button className="btn btn-secondary btn-sm" type="button" onClick={() => useChatStore.getState().retryLastMessage()}>
          重试上条消息
        </button>
      </div>
    )
  }

  if (streamStatus === 'stopping') {
    return (
      <div className="stream-status stream-status--muted" role="status" aria-live="polite">
        正在停止生成…
      </div>
    )
  }

  if (streamStatus === 'done' && streamState.stopped) {
    return (
      <div className="stream-status stream-status--muted" role="status" aria-live="polite">
        已停止生成，当前内容已保留。
      </div>
    )
  }

  return null
}

function Composer() {
  const knowledgeBases = useChatStore((s) => s.availableKnowledgeBases)
  const selectedKnowledgeBaseIds = useChatStore((s) => s.selectedKnowledgeBaseIds)
  const executionMode = useChatStore((s) => s.executionMode)
  const toolCapabilities = useChatStore((s) => s.toolCapabilities)
  const streamStatus = useChatStore((s) => s.streamStatus)
  const locked = streamStatus === 'submitting' || streamStatus === 'streaming' || streamStatus === 'stopping'

  return (
    <ComposerPrimitive.Root className="composer-dock">
      <div className="composer-chips">
        <KnowledgeBasePicker
          options={knowledgeBases}
          selectedIds={selectedKnowledgeBaseIds}
          disabled={locked}
          onChange={(ids) => useChatStore.getState().setSelectedKnowledgeBaseIds(ids)}
        />
        <label className="composer-chip">
          <span>模式</span>
          <select
            value={executionMode}
            disabled={locked}
            onChange={(event) => useChatStore.getState().setExecutionMode(event.target.value as RequestedExecutionMode)}
          >
            {MODES.map((mode) => (
              <option key={mode.value} value={mode.value}>
                {mode.label}
              </option>
            ))}
          </select>
        </label>
        {toolCapabilities.length > 0 && (
          <div className="tool-capability-strip">
            <span>工具</span>
            {toolCapabilities.slice(0, 4).map((tool) => (
              <span
                key={tool.toolId}
                className={`tool-capability${tool.executable ? '' : ' tool-capability--blocked'}`}
                title={tool.description}
              >
                {tool.name}
              </span>
            ))}
          </div>
        )}
      </div>

      <ComposerPrimitive.Input
        data-testid="chat-composer"
        className="composer-input input-control"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行…"
        disabled={locked}
      />

      <div className="composer-actions">
        <div className="composer-buttons">
          <ThreadPrimitive.If running={false}>
            <ComposerPrimitive.Send data-testid="chat-send" className="btn btn-primary">
              发送
            </ComposerPrimitive.Send>
          </ThreadPrimitive.If>
          <ThreadPrimitive.If running>
            <ComposerPrimitive.Cancel
              data-testid="chat-stop"
              className="btn btn-danger"
              disabled={streamStatus === 'stopping'}
              onClick={() => {
                void useChatStore.getState().stopStreaming()
              }}
            >
              {streamStatus === 'stopping' ? '停止中…' : '停止'}
            </ComposerPrimitive.Cancel>
          </ThreadPrimitive.If>
        </div>
      </div>
    </ComposerPrimitive.Root>
  )
}
