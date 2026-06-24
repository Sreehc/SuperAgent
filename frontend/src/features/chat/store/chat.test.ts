import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createConversation,
  getConversation,
  listConversationFeedbacks,
  listConversations,
  listMessages,
  openMessageStream,
  stopConversation,
} from '../api'
import { listToolCapabilities } from '../../agent/api'
import { listKnowledgeBases } from '../../knowledge/api'
import { useChatStore, type ChatStore } from './chat'
import { createEmptyStreamState } from './streamRuntime'

vi.mock('../api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api')>()),
  createConversation: vi.fn(),
  deleteConversation: vi.fn(),
  deleteMessageFeedback: vi.fn(),
  getConversation: vi.fn(),
  listConversationFeedbacks: vi.fn(),
  listConversations: vi.fn(),
  listMessages: vi.fn(),
  openMessageStream: vi.fn(),
  resumeConversation: vi.fn(),
  stopConversation: vi.fn(),
  updateConversation: vi.fn(),
  upsertMessageFeedback: vi.fn(),
}))

vi.mock('../../knowledge/api', () => ({
  listKnowledgeBases: vi.fn(),
}))

vi.mock('../../agent/api', () => ({
  listToolCapabilities: vi.fn(),
}))

const originalActions = {
  bootstrap: useChatStore.getState().bootstrap,
  fetchConversations: useChatStore.getState().fetchConversations,
  createAndSelectConversation: useChatStore.getState().createAndSelectConversation,
  selectConversation: useChatStore.getState().selectConversation,
  sendMessage: useChatStore.getState().sendMessage,
  stopStreaming: useChatStore.getState().stopStreaming,
  retryLastMessage: useChatStore.getState().retryLastMessage,
  resumeConversationRun: useChatStore.getState().resumeConversationRun,
  renameConversation: useChatStore.getState().renameConversation,
  archiveConversation: useChatStore.getState().archiveConversation,
  removeConversation: useChatStore.getState().removeConversation,
  setMessageFeedback: useChatStore.getState().setMessageFeedback,
  correctMessageFeedback: useChatStore.getState().correctMessageFeedback,
  clearMessageFeedback: useChatStore.getState().clearMessageFeedback,
  setKeyword: useChatStore.getState().setKeyword,
  setMessage: useChatStore.getState().setMessage,
  setSelectedKnowledgeBaseId: useChatStore.getState().setSelectedKnowledgeBaseId,
  setSelectedKnowledgeBaseIds: useChatStore.getState().setSelectedKnowledgeBaseIds,
  setExecutionMode: useChatStore.getState().setExecutionMode,
  useRecommendation: useChatStore.getState().useRecommendation,
  clearOnRouteLeave: useChatStore.getState().clearOnRouteLeave,
}

function resetChatState() {
  useChatStore.setState({
    conversations: [],
    selectedSessionId: null,
    selectedConversation: null,
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

describe('chat knowledge base selection', () => {
  beforeEach(() => {
    vi.unstubAllEnvs()
    vi.mocked(createConversation).mockReset()
    vi.mocked(getConversation).mockReset()
    vi.mocked(listConversations).mockReset()
    vi.mocked(openMessageStream).mockReset()
    vi.mocked(stopConversation).mockReset()
    resetChatState()
  })

  it('selects a requested deep-linked session even when it is not on the first conversation page', async () => {
    vi.mocked(listKnowledgeBases).mockResolvedValue({
      success: true,
      code: 'OK',
      message: 'ok',
      data: { items: [], page: 1, pageSize: 100, total: 0 },
      traceId: 'trace-test',
    })
    vi.mocked(listToolCapabilities).mockResolvedValue({
      success: true,
      code: 'OK',
      message: 'ok',
      data: { tools: [] },
      traceId: 'trace-test',
    })
    vi.mocked(listConversations).mockResolvedValue({
      success: true,
      code: 'OK',
      message: 'ok',
      data: {
        items: [
          {
            id: 1,
            title: '最新会话',
            status: 'active',
            lastMessageAt: '2026-01-02T00:00:00Z',
          },
        ],
        page: 1,
        pageSize: 20,
        total: 2,
      },
      traceId: 'trace-test',
    })
    vi.mocked(getConversation).mockResolvedValue({
      success: true,
      code: 'OK',
      message: 'ok',
      data: {
        id: 42,
        title: '深链会话',
        knowledgeBaseId: 7,
        memoryStrategy: 'SLIDING_WINDOW',
        status: 'active',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
      traceId: 'trace-test',
    })
    vi.mocked(listMessages).mockResolvedValue({
      success: true,
      code: 'OK',
      message: 'ok',
      data: { items: [], page: 1, pageSize: 20, total: 0 },
      traceId: 'trace-test',
    })
    vi.mocked(listConversationFeedbacks).mockResolvedValue({
      success: true,
      code: 'OK',
      message: 'ok',
      data: [],
      traceId: 'trace-test',
    })

    await useChatStore.getState().bootstrap(42)

    expect(getConversation).toHaveBeenCalledWith(42)
    expect(useChatStore.getState().selectedSessionId).toBe(42)
    expect(useChatStore.getState().selectedConversation?.title).toBe('深链会话')
  })

  it('keeps multi selection unique and mirrors legacy single selection only for one id', () => {
    useChatStore.getState().setSelectedKnowledgeBaseIds([2, 3, 2])

    expect(useChatStore.getState().selectedKnowledgeBaseIds).toEqual([2, 3])
    expect(useChatStore.getState().selectedKnowledgeBaseId).toBeNull()

    useChatStore.getState().setSelectedKnowledgeBaseIds([5])

    expect(useChatStore.getState().selectedKnowledgeBaseIds).toEqual([5])
    expect(useChatStore.getState().selectedKnowledgeBaseId).toBe(5)

    useChatStore.getState().setSelectedKnowledgeBaseId(null)

    expect(useChatStore.getState().selectedKnowledgeBaseIds).toEqual([])
    expect(useChatStore.getState().selectedKnowledgeBaseId).toBeNull()
  })

  it('shows a degradation error instead of sending when multiple knowledge bases are selected', async () => {
    useChatStore.setState({
      selectedKnowledgeBaseIds: [1, 2],
      selectedKnowledgeBaseId: null,
      message: '总结退款规则',
    })

    await useChatStore.getState().sendMessage()

    expect(useChatStore.getState().streamState.error).toContain('暂不支持多知识库')
    expect(useChatStore.getState().streaming).toBe(false)
    expect(createConversation).not.toHaveBeenCalled()
    expect(openMessageStream).not.toHaveBeenCalled()
  })

  it('sends target knowledgeBaseIds when the integration flag is enabled', async () => {
    vi.stubEnv('VITE_CHAT_MULTI_KB_STREAM_ENABLED', 'true')
    vi.mocked(openMessageStream).mockResolvedValue({} as Awaited<ReturnType<typeof openMessageStream>>)
    vi.mocked(listConversations).mockResolvedValue({
      success: true,
      code: 'OK',
      message: 'ok',
      data: { items: [], page: 1, pageSize: 20, total: 0 },
      traceId: 'trace-test',
    })
    vi.mocked(getConversation).mockResolvedValue({
      success: true,
      code: 'OK',
      message: 'ok',
      data: {
        id: 42,
        title: '多知识库',
        knowledgeBaseId: null,
        memoryStrategy: 'SLIDING_WINDOW',
        status: 'active',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
      traceId: 'trace-test',
    })
    useChatStore.setState({
      selectedSessionId: 42,
      selectedKnowledgeBaseIds: [1, 2],
      selectedKnowledgeBaseId: null,
      message: '总结退款规则',
    })

    await useChatStore.getState().sendMessage()

    expect(openMessageStream).toHaveBeenCalledTimes(1)
    expect(openMessageStream).toHaveBeenCalledWith(
      42,
      {
        message: '总结退款规则',
        knowledgeBaseIds: [1, 2],
        memoryStrategy: 'SLIDING_WINDOW',
        executionMode: 'AUTO',
      },
      expect.any(Function),
      expect.any(AbortSignal),
    )
  })
})

describe('chat stream status handling', () => {
  beforeEach(() => {
    vi.unstubAllEnvs()
    vi.mocked(createConversation).mockReset()
    vi.mocked(getConversation).mockReset()
    vi.mocked(listConversations).mockReset()
    vi.mocked(openMessageStream).mockReset()
    vi.mocked(stopConversation).mockReset()
    resetChatState()
  })

  it('preserves the user message and partial assistant text when streaming fails', async () => {
    vi.mocked(openMessageStream).mockImplementation(async (_sessionId, _payload, onChunk) => {
      onChunk('event: delta\ndata: {"text":"已生成一半"}\n\n')
      throw new Error('network failed')
    })
    useChatStore.setState({
      selectedSessionId: 42,
      message: '总结退款规则',
    })

    await expect(useChatStore.getState().sendMessage()).rejects.toThrow('network failed')

    const state = useChatStore.getState()
    expect(state.streaming).toBe(false)
    expect(state.streamStatus).toBe('error')
    expect(state.streamState.error).toBe('消息发送失败，请稍后重试。')
    expect(state.messages).toHaveLength(2)
    expect(state.messages[0]).toMatchObject({ role: 'user', content: '总结退款规则', status: 'success' })
    expect(state.messages[1]).toMatchObject({ role: 'assistant', content: '已生成一半', status: 'error' })
  })

  it('prevents duplicate stop requests while stopping is in flight', async () => {
    let resolveStop: () => void = () => {}
    vi.mocked(stopConversation).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveStop = () => resolve({
            success: true,
            code: 'OK',
            message: 'ok',
            data: { stopped: true, sessionId: 42 },
            traceId: 'trace-test',
          })
        }),
    )
    useChatStore.setState({
      selectedSessionId: 42,
      streaming: true,
      streamStatus: 'streaming',
    })

    const firstStop = useChatStore.getState().stopStreaming()
    void useChatStore.getState().stopStreaming()

    expect(useChatStore.getState().streamStatus).toBe('stopping')
    expect(stopConversation).toHaveBeenCalledTimes(1)

    resolveStop()
    await firstStop
  })

  it('treats an aborted stream as stopped when stop was requested', async () => {
    let rejectStream: (error: Error) => void = () => {}
    vi.mocked(openMessageStream).mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectStream = reject
        }) as ReturnType<typeof openMessageStream>,
    )
    vi.mocked(stopConversation).mockResolvedValue({
      success: true,
      code: 'OK',
      message: 'ok',
      data: { stopped: true, sessionId: 42 },
      traceId: 'trace-test',
    })
    useChatStore.setState({
      selectedSessionId: 42,
      message: '总结退款规则',
    })

    const sendPromise = useChatStore.getState().sendMessage()
    expect(useChatStore.getState().streaming).toBe(true)

    await useChatStore.getState().stopStreaming()
    rejectStream(new DOMException('The operation was aborted.', 'AbortError'))
    await sendPromise

    const state = useChatStore.getState()
    expect(state.streaming).toBe(false)
    expect(state.streamStatus).toBe('done')
    expect(state.streamState.error).toBe('')
    expect(state.streamState.stopped).toBe(true)
  })

  it('does not turn a completed stream into an error when the transport closes noisily', async () => {
    vi.mocked(openMessageStream).mockImplementation(async (_sessionId, _payload, onChunk) => {
      const deltaChunk = 'event: delta\ndata: {"text":"退款规则摘要"}\n\n'
      onChunk(deltaChunk)
      onChunk(`${deltaChunk}event: done\ndata: {"assistantMessageId":99,"stopped":false}\n\n`)
      throw new Error('net::ERR_INCOMPLETE_CHUNKED_ENCODING')
    })
    useChatStore.setState({
      selectedSessionId: 42,
      message: '总结退款规则',
    })

    await useChatStore.getState().sendMessage()

    const state = useChatStore.getState()
    expect(state.streaming).toBe(false)
    expect(state.streamStatus).toBe('done')
    expect(state.streamState.error).toBe('')
    expect(state.messages.at(-1)).toMatchObject({
      role: 'assistant',
      content: '退款规则摘要',
      status: 'success',
    })
  })

  it('retries the latest user message without clearing the failed assistant content', async () => {
    const sendMessage = vi.fn<ChatStore['sendMessage']>().mockResolvedValue(undefined)
    useChatStore.setState({
      sendMessage,
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

    await useChatStore.getState().retryLastMessage()

    expect(sendMessage).toHaveBeenCalledWith('原始问题')
    expect(useChatStore.getState().messages[1].content).toBe('失败前内容')
  })
})
