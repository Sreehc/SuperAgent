import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { useChatStore } from '../store/chat'
import { createEmptyStreamState } from '../store/streamRuntime'
import { ChatWorkspacePage } from './ChatWorkspacePage'

vi.mock('../components/ConversationSurface', () => ({
  ConversationSurface: () => (
    <section className="conversation-surface" data-testid="conversation-surface">
      消息线程
    </section>
  ),
}))

vi.mock('../components/EvidenceInspector', () => ({
  EvidenceInspector: ({ framed = true }: { framed?: boolean }) =>
    framed ? (
      <aside className="evidence-inspector">桌面检查器</aside>
    ) : (
      <div className="evidence-inspector__drawer-body">检查器 drawer</div>
    ),
}))

vi.mock('../components/SessionRail', () => ({
  SessionRail: ({ onAfterNavigate }: { onAfterNavigate?: () => void }) => (
    <aside className="session-rail">
      <p>会话列表</p>
      <button type="button" onClick={onAfterNavigate}>
        选择会话
      </button>
    </aside>
  ),
}))

const originalActions = {
  bootstrap: useChatStore.getState().bootstrap,
  selectConversation: useChatStore.getState().selectConversation,
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
    bootstrap: vi.fn().mockResolvedValue(undefined),
    selectConversation: vi.fn().mockResolvedValue(undefined),
    clearOnRouteLeave: vi.fn(),
  })
}

function renderChatWorkspace(route = '/chat') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path="/chat" element={<ChatWorkspacePage />} />
        <Route path="/chat/:sessionId" element={<ChatWorkspacePage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ChatWorkspacePage mobile drawers', () => {
  beforeEach(() => {
    resetChatState()
    document.body.style.overflow = 'auto'
    document.body.style.overscrollBehavior = 'auto'
  })

  afterEach(() => {
    cleanup()
    document.body.style.overflow = ''
    document.body.style.overscrollBehavior = ''
    useChatStore.setState(originalActions)
  })

  it('opens session and inspector drawers from the chat toolbar without losing the desktop panels', async () => {
    renderChatWorkspace()

    const toolbar = screen.getByLabelText('聊天工具栏')
    expect(within(toolbar).getByRole('button', { name: '会话' })).toBeTruthy()
    expect(within(toolbar).getByRole('button', { name: '证据 / Trace' })).toBeTruthy()
    expect(screen.getByTestId('conversation-surface')).toBeTruthy()
    expect(screen.getByText('桌面检查器')).toBeTruthy()

    fireEvent.click(within(toolbar).getByRole('button', { name: '会话' }))

    const sessionDrawer = screen.getByRole('dialog', { name: '会话' })
    expect(within(sessionDrawer).getByText('会话列表')).toBeTruthy()
    expect(document.body.style.overflow).toBe('hidden')
    expect(document.body.style.overscrollBehavior).toBe('contain')

    fireEvent.click(within(sessionDrawer).getByRole('button', { name: '选择会话' }))

    await waitFor(() => expect(screen.queryByRole('dialog', { name: '会话' })).toBeNull())
    await waitFor(() => expect(document.body.style.overflow).toBe('auto'))

    fireEvent.click(within(toolbar).getByRole('button', { name: '证据 / Trace' }))

    const inspectorDrawer = screen.getByRole('dialog', { name: '证据 / Trace' })
    expect(within(inspectorDrawer).getByText('检查器 drawer')).toBeTruthy()
    expect(document.body.style.overflow).toBe('hidden')

    fireEvent.click(within(inspectorDrawer).getByRole('button', { name: '关闭证据 / Trace' }))

    await waitFor(() => expect(screen.queryByRole('dialog', { name: '证据 / Trace' })).toBeNull())
  })
})
