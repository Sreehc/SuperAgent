import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useChatStore, type ChatStore } from '../store/chat'
import { createEmptyStreamState } from '../store/streamRuntime'
import { ConversationSurface } from './ConversationSurface'

vi.mock('@assistant-ui/react', () => ({
  AssistantRuntimeProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  ComposerPrimitive: {
    Root: ({ children, className }: { children: React.ReactNode; className?: string }) => (
      <form className={className}>{children}</form>
    ),
    Input: (props: React.InputHTMLAttributes<HTMLInputElement>) => <input {...props} />,
    Send: ({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) => (
      <button type="button" {...props}>
        {children}
      </button>
    ),
    Cancel: ({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) => (
      <button type="button" {...props}>
        {children}
      </button>
    ),
  },
  MessagePrimitive: {
    Parts: () => null,
  },
  ThreadPrimitive: {
    Root: ({ children, className }: { children: React.ReactNode; className?: string }) => (
      <div className={className}>{children}</div>
    ),
    Viewport: ({ children, className }: { children: React.ReactNode; className?: string }) => (
      <div className={className}>{children}</div>
    ),
    Empty: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    Messages: () => null,
    If: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  },
  useExternalStoreRuntime: () => ({}),
  useMessage: () => '2',
}))

const originalActions = {
  retryLastMessage: useChatStore.getState().retryLastMessage,
  stopStreaming: useChatStore.getState().stopStreaming,
}

function resetChatState() {
  useChatStore.setState({
    conversations: [],
    selectedSessionId: 42,
    selectedConversation: {
      id: 42,
      title: '退款会话',
      knowledgeBaseId: null,
      memoryStrategy: 'SLIDING_WINDOW',
      status: 'active',
      createdAt: '2026-06-20T00:00:00Z',
      updatedAt: '2026-06-20T00:00:00Z',
    },
    messages: [],
    loadingConversations: false,
    loadingMessages: false,
    streaming: false,
    streamStatus: 'idle',
    streamState: createEmptyStreamState(),
    selectedReference: null,
    keyword: '',
    updatingFeedbackMessageId: null,
    message: '',
    memoryStrategy: 'SLIDING_WINDOW',
    executionMode: 'AUTO',
    availableKnowledgeBases: [],
    selectedKnowledgeBaseId: null,
    selectedKnowledgeBaseIds: [],
    toolCapabilities: [],
    ...originalActions,
  })
}

describe('ConversationSurface stream status', () => {
  beforeEach(() => {
    resetChatState()
  })

  afterEach(() => {
    cleanup()
    resetChatState()
    vi.restoreAllMocks()
  })

  it('shows a retryable error without clearing the failed content', () => {
    const retryLastMessage = vi.fn<ChatStore['retryLastMessage']>().mockResolvedValue(undefined)
    useChatStore.setState({
      retryLastMessage,
      streamStatus: 'error',
      streamState: { ...createEmptyStreamState(), error: '消息发送失败，请稍后重试。' },
      messages: [
        {
          id: 1,
          role: 'user',
          content: '原始问题',
          status: 'success',
          createdAt: '2026-06-20T00:00:00Z',
          references: [],
          feedback: null,
        },
        {
          id: 2,
          role: 'assistant',
          content: '失败前内容',
          status: 'error',
          createdAt: '2026-06-20T00:00:01Z',
          references: [],
          feedback: null,
        },
      ],
    })

    render(<ConversationSurface />)

    const alert = screen.getByRole('alert')
    expect(alert.textContent).toContain('消息发送失败，请稍后重试。')
    expect(within(alert).getByText('已保留当前消息内容。')).toBeTruthy()

    fireEvent.click(within(alert).getByRole('button', { name: '重试上条消息' }))

    expect(retryLastMessage).toHaveBeenCalledTimes(1)
  })

  it('calls the store stop action directly from the stop button', () => {
    const stopStreaming = vi.fn<ChatStore['stopStreaming']>().mockResolvedValue(undefined)
    useChatStore.setState({
      stopStreaming,
      streaming: true,
      streamStatus: 'streaming',
    })

    render(<ConversationSurface />)

    fireEvent.click(screen.getByTestId('chat-stop'))

    expect(stopStreaming).toHaveBeenCalledTimes(1)
  })
})
