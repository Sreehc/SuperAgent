import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { MembersConsolePage } from './MembersConsolePage'
import { useAuthStore } from '../../auth/store/auth'
import { createInvitation, createMember, listInvitations, listMembers, removeMember, resetMemberPassword, updateMember } from '../api'
import type { InvitationItem, MemberItem } from '../api'
import type { ApiEnvelope } from '@/api/types'

vi.mock('../api', async () => {
  const actual = await vi.importActual<typeof import('../api')>('../api')
  return {
    ...actual,
    createInvitation: vi.fn(),
    createMember: vi.fn(),
    listInvitations: vi.fn(),
    listMembers: vi.fn(),
    removeMember: vi.fn(),
    resetMemberPassword: vi.fn(),
    updateMember: vi.fn(),
  }
})

function envelope<T>(data: T): ApiEnvelope<T> {
  return {
    success: true,
    code: 'OK',
    message: 'ok',
    data,
    traceId: 'test-trace',
  }
}

const members: MemberItem[] = [
  {
    userId: 1,
    username: 'owner',
    displayName: 'Owner User',
    role: 'OWNER',
    status: 'active',
    joinedAt: '2026-06-20T10:00:00',
  },
  {
    userId: 2,
    username: 'analyst',
    displayName: 'Data Analyst',
    role: 'MEMBER',
    status: 'active',
    joinedAt: '2026-06-20T11:00:00',
  },
]

const invitations: InvitationItem[] = [
  {
    id: 10,
    email: 'invitee@example.com',
    role: 'ADMIN',
    status: 'pending',
    invitedBy: 1,
    expiresAt: '2026-06-28T10:00:00',
    acceptedAt: null,
    createdAt: '2026-06-20T10:00:00',
  },
]

function renderMembers() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <MembersConsolePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('MembersConsolePage tabs and workflows', () => {
  beforeEach(() => {
    useAuthStore.setState({
      user: { id: 1, username: 'owner', displayName: 'Owner' },
      tenants: [{ id: 10, name: 'Acme', code: 'acme', role: 'ADMIN', status: 'active' }],
      currentTenantId: 10,
      initialized: true,
    })
    vi.mocked(listMembers).mockResolvedValue(envelope(members))
    vi.mocked(listInvitations).mockResolvedValue(envelope(invitations))
    vi.mocked(createMember).mockResolvedValue({
      userId: 3,
      username: 'new-user',
      displayName: 'New User',
      email: 'new@example.com',
      role: 'MEMBER',
      status: 'active',
    })
    vi.mocked(createInvitation).mockResolvedValue({
      id: 11,
      tenantId: 10,
      email: 'new-invite@example.com',
      role: 'MEMBER',
      status: 'pending',
      expiresAt: '2026-06-28T10:00:00',
      invitedBy: 1,
      token: 'token',
    })
    vi.mocked(updateMember).mockResolvedValue({ userId: 2, role: 'ADMIN', status: 'active' })
    vi.mocked(removeMember).mockResolvedValue({ removed: true })
    vi.mocked(resetMemberPassword).mockResolvedValue({ userId: 2, reset: true })
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('separates members, invitations, create account, and invite workflows into tabs', async () => {
    renderMembers()

    expect(await screen.findByRole('tab', { name: '成员列表' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '成员列表' }).getAttribute('aria-selected')).toBe('true')
    expect(await screen.findByText('Owner User')).toBeTruthy()
    expect(screen.queryByText('创建组织账号')).toBeNull()
    expect(screen.queryByText('invitee@example.com')).toBeNull()

    fireEvent.click(screen.getByRole('tab', { name: '待处理邀请' }))
    expect(await screen.findByText('invitee@example.com')).toBeTruthy()
    expect(screen.queryByText('Owner User')).toBeNull()

    fireEvent.click(screen.getByRole('tab', { name: '创建账号' }))
    expect(await screen.findByLabelText('用户名')).toBeTruthy()
    expect(screen.queryByText('invitee@example.com')).toBeNull()

    fireEvent.click(screen.getByRole('tab', { name: '邀请成员' }))
    expect(await screen.findByRole('button', { name: '发送邀请' })).toBeTruthy()
    expect(screen.queryByLabelText('用户名')).toBeNull()
  })

  it('keeps account creation and invitation submission working from their tabs', async () => {
    renderMembers()

    fireEvent.click(await screen.findByRole('tab', { name: '创建账号' }))
    fireEvent.change(screen.getByLabelText('用户名'), { target: { value: ' new-user ' } })
    fireEvent.change(screen.getByLabelText('显示名'), { target: { value: ' New User ' } })
    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: ' new@example.com ' } })
    fireEvent.change(screen.getByLabelText('初始密码'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByRole('button', { name: '创建并加入组织' }))

    await waitFor(() =>
      expect(vi.mocked(createMember)).toHaveBeenCalledWith(10, {
        username: 'new-user',
        displayName: 'New User',
        email: 'new@example.com',
        password: 'password123',
        role: 'MEMBER',
      }),
    )

    fireEvent.click(screen.getByRole('tab', { name: '邀请成员' }))
    const invitePanel = await screen.findByRole('tabpanel', { name: '邀请成员' })
    fireEvent.change(within(invitePanel).getByLabelText('邮箱'), { target: { value: ' new-invite@example.com ' } })
    fireEvent.change(within(invitePanel).getByLabelText('角色'), { target: { value: 'ADMIN' } })
    fireEvent.click(within(invitePanel).getByRole('button', { name: '发送邀请' }))

    await waitFor(() =>
      expect(vi.mocked(createInvitation)).toHaveBeenCalledWith(10, {
        email: 'new-invite@example.com',
        role: 'ADMIN',
      }),
    )
  })

  it('confirms role changes and restores the previous role when the update fails', async () => {
    vi.mocked(updateMember).mockRejectedValueOnce(new Error('forbidden'))
    renderMembers()

    const analystRow = (await screen.findByText('Data Analyst')).closest('tr') as HTMLTableRowElement
    const roleSelect = within(analystRow).getByDisplayValue('MEMBER') as HTMLSelectElement

    fireEvent.change(roleSelect, { target: { value: 'ADMIN' } })
    const dialog = await screen.findByRole('dialog', { name: '调整成员角色' })

    expect(dialog.textContent).toContain('Data Analyst')
    expect(dialog.textContent).toContain('MEMBER')
    expect(dialog.textContent).toContain('ADMIN')
    expect(vi.mocked(updateMember)).not.toHaveBeenCalled()

    fireEvent.click(within(dialog).getByRole('button', { name: '取消' }))
    expect(vi.mocked(updateMember)).not.toHaveBeenCalled()
    expect(roleSelect.value).toBe('MEMBER')

    fireEvent.change(roleSelect, { target: { value: 'ADMIN' } })
    fireEvent.click(await screen.findByRole('button', { name: '确认调整' }))

    await waitFor(() => expect(vi.mocked(updateMember)).toHaveBeenCalledWith(10, 2, { role: 'ADMIN' }))
    expect(await screen.findByText('角色调整失败，请检查权限后重试。')).toBeTruthy()
    expect(roleSelect.value).toBe('MEMBER')
  })

  it('requires confirmation for removing members and validates password reset before submitting', async () => {
    renderMembers()

    const analystRow = (await screen.findByText('Data Analyst')).closest('tr') as HTMLTableRowElement
    fireEvent.click(within(analystRow).getByRole('button', { name: '移除' }))
    const removeDialog = await screen.findByRole('dialog', { name: '移除成员' })

    expect(removeDialog.textContent).toContain('Data Analyst')
    expect(vi.mocked(removeMember)).not.toHaveBeenCalled()
    fireEvent.click(within(removeDialog).getByRole('button', { name: '取消' }))
    expect(vi.mocked(removeMember)).not.toHaveBeenCalled()

    fireEvent.click(within(analystRow).getByRole('button', { name: '移除' }))
    fireEvent.click(await screen.findByRole('button', { name: '确认移除' }))
    await waitFor(() => expect(vi.mocked(removeMember)).toHaveBeenCalledWith(10, 2))

    fireEvent.click(within(analystRow).getByRole('button', { name: '重置密码' }))
    const resetDialog = await screen.findByRole('dialog', { name: '重置成员密码' })
    const confirmReset = within(resetDialog).getByRole('button', { name: '确认重置' })
    expect(confirmReset.hasAttribute('disabled')).toBe(true)

    fireEvent.change(within(resetDialog).getByLabelText('新密码'), { target: { value: 'newpass123' } })
    fireEvent.click(confirmReset)
    await waitFor(() => expect(vi.mocked(resetMemberPassword)).toHaveBeenCalledWith(10, 2, { password: 'newpass123' }))
  })
})
