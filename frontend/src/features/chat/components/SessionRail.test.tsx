import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, useLocation } from 'react-router-dom'
import type { ConversationSummary } from '../types'
import { useChatStore, type ChatStore } from '../store/chat'
import { createEmptyStreamState } from '../store/streamRuntime'
import { SessionRail } from './SessionRail'

const originalActions = {
  bootstrap: useChatStore.getState().bootstrap,
  fetchConversations: useChatStore.getState().fetchConversations,
  createAndSelectConversation: useChatStore.getState().createAndSelectConversation,
  selectConversation: useChatStore.getState().selectConversation,
  sendMessage: useChatStore.getState().sendMessage,
  stopStreaming: useChatStore.getState().stopStreaming,
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

const conversations: ConversationSummary[] = [
  {
    id: 10,
    title: '退款政策',
    status: 'active',
    lastMessageAt: '2026-06-20T08:00:00Z',
  },
  {
    id: 11,
    title: '审批流程',
    status: 'active',
    lastMessageAt: null,
  },
]

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
    ...originalActions,
  })
}

function seedSessionRailState(items: ConversationSummary[] = conversations) {
  const actions = {
    createAndSelectConversation: vi.fn<ChatStore['createAndSelectConversation']>().mockResolvedValue(99),
    selectConversation: vi.fn<ChatStore['selectConversation']>().mockResolvedValue(undefined),
    renameConversation: vi.fn<ChatStore['renameConversation']>().mockResolvedValue(undefined),
    archiveConversation: vi.fn<ChatStore['archiveConversation']>().mockResolvedValue(undefined),
    removeConversation: vi.fn<ChatStore['removeConversation']>().mockResolvedValue(undefined),
    setKeyword: vi.fn<ChatStore['setKeyword']>(),
  }

  useChatStore.setState({
    conversations: items,
    selectedSessionId: items[0]?.id ?? null,
    keyword: '',
    ...actions,
  })

  return actions
}

function LocationProbe() {
  const location = useLocation()
  return <output aria-label="当前路径">{location.pathname}</output>
}

function renderSessionRail() {
  return render(
    <MemoryRouter initialEntries={['/chat']}>
      <SessionRail />
      <LocationProbe />
    </MemoryRouter>,
  )
}

describe('SessionRail', () => {
  beforeEach(() => {
    resetChatState()
  })

  afterEach(() => {
    cleanup()
    resetChatState()
    vi.restoreAllMocks()
  })

  it('renders an empty state with a new conversation action', async () => {
    const actions = seedSessionRailState([])

    renderSessionRail()

    expect(screen.getByRole('status', { name: '暂无会话' })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '新建会话' }))

    await waitFor(() => expect(actions.createAndSelectConversation).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(screen.getByLabelText('当前路径').textContent).toBe('/chat/99'))
  })

  it('supports search and conversation selection', async () => {
    const actions = seedSessionRailState()

    renderSessionRail()

    fireEvent.change(screen.getByPlaceholderText('搜索会话…'), { target: { value: '审批' } })
    expect(actions.setKeyword).toHaveBeenCalledWith('审批')

    fireEvent.click(screen.getByRole('button', { name: /审批流程/ }))

    await waitFor(() => expect(actions.selectConversation).toHaveBeenCalledWith(11))
    await waitFor(() => expect(screen.getByLabelText('当前路径').textContent).toBe('/chat/11'))
  })

  it('renames a conversation through PromptDialog without native prompt', async () => {
    const actions = seedSessionRailState()
    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue('原生输入不应使用')

    renderSessionRail()

    fireEvent.click(screen.getAllByRole('button', { name: '改名' })[0])

    const dialog = screen.getByRole('dialog', { name: '重命名会话' })
    fireEvent.change(within(dialog).getByLabelText('会话名称'), { target: { value: '  新标题  ' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '保存' }))

    await waitFor(() => expect(actions.renameConversation).toHaveBeenCalledWith(10, '新标题'))
    expect(promptSpy).not.toHaveBeenCalled()
  })

  it('archives a conversation only after confirmation', async () => {
    const actions = seedSessionRailState()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)

    renderSessionRail()

    fireEvent.click(screen.getAllByRole('button', { name: '归档' })[0])

    expect(actions.archiveConversation).not.toHaveBeenCalled()

    const dialog = screen.getByRole('dialog', { name: '归档会话' })
    fireEvent.click(within(dialog).getByRole('button', { name: '确认归档' }))

    await waitFor(() => expect(actions.archiveConversation).toHaveBeenCalledWith(10))
    expect(confirmSpy).not.toHaveBeenCalled()
  })

  it('deletes a conversation only after second confirmation', async () => {
    const actions = seedSessionRailState()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)

    renderSessionRail()

    fireEvent.click(screen.getAllByRole('button', { name: '删除' })[0])

    expect(actions.removeConversation).not.toHaveBeenCalled()

    const dialog = screen.getByRole('dialog', { name: '删除会话' })
    fireEvent.click(within(dialog).getByRole('button', { name: '确认删除' }))

    await waitFor(() => expect(actions.removeConversation).toHaveBeenCalledWith(10))
    expect(confirmSpy).not.toHaveBeenCalled()
  })

  it('keeps the dialog open and shows a local error when an action fails', async () => {
    const actions = seedSessionRailState()
    actions.renameConversation.mockRejectedValue(new Error('network failed'))

    renderSessionRail()

    fireEvent.click(screen.getAllByRole('button', { name: '改名' })[0])
    fireEvent.change(screen.getByLabelText('会话名称'), { target: { value: '新标题' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    const alert = await screen.findByRole('alert')

    expect(alert.textContent).toContain('重命名会话失败，请稍后重试。')
    expect(screen.getByRole('dialog', { name: '重命名会话' })).toBeTruthy()
  })
})
