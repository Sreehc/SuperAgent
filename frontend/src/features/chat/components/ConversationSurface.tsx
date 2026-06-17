import { AssistantRuntimeProvider, ComposerPrimitive, MessagePrimitive, ThreadPrimitive } from '@assistant-ui/react'
import { useChatRuntime } from '../runtime'
import { useChatStore } from '../store/chat'
import { renderMarkdown } from '../utils/renderMarkdown'
import type { RequestedExecutionMode } from '../types'

function MarkdownText({ text }: { text: string }) {
  return <div className="markdown-body" dangerouslySetInnerHTML={{ __html: renderMarkdown(text) }} />
}

function UserMessage() {
  return (
    <div className="message-block message-block--user">
      <div className="message-block__content">
        <MessagePrimitive.Parts />
      </div>
    </div>
  )
}

function AssistantMessage() {
  return (
    <div className="message-block message-block--assistant">
      <div className="message-block__content">
        <MessagePrimitive.Parts components={{ Text: MarkdownText }} />
      </div>
    </div>
  )
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
          <Composer />
        </ThreadPrimitive.Root>
      </section>
    </AssistantRuntimeProvider>
  )
}

function Composer() {
  const knowledgeBases = useChatStore((s) => s.availableKnowledgeBases)
  const selectedKnowledgeBaseId = useChatStore((s) => s.selectedKnowledgeBaseId)
  const executionMode = useChatStore((s) => s.executionMode)
  const toolCapabilities = useChatStore((s) => s.toolCapabilities)

  return (
    <ComposerPrimitive.Root className="composer-dock">
      <div className="composer-chips">
        <label className="composer-chip">
          <span>知识库</span>
          <select
            data-testid="chat-knowledge-base"
            value={selectedKnowledgeBaseId ?? ''}
            onChange={(event) =>
              useChatStore.getState().setSelectedKnowledgeBaseId(event.target.value ? Number(event.target.value) : null)
            }
          >
            <option value="">不限定</option>
            {knowledgeBases.map((kb) => (
              <option key={kb.id} value={kb.id}>
                {kb.name}
              </option>
            ))}
          </select>
        </label>
        <label className="composer-chip">
          <span>模式</span>
          <select
            value={executionMode}
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
      />

      <div className="composer-actions">
        <div className="composer-buttons">
          <ThreadPrimitive.If running={false}>
            <ComposerPrimitive.Send data-testid="chat-send" className="btn btn-primary">
              发送
            </ComposerPrimitive.Send>
          </ThreadPrimitive.If>
          <ThreadPrimitive.If running>
            <ComposerPrimitive.Cancel data-testid="chat-stop" className="btn btn-danger">
              停止
            </ComposerPrimitive.Cancel>
          </ThreadPrimitive.If>
        </div>
      </div>
    </ComposerPrimitive.Root>
  )
}
