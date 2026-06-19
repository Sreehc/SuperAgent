import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createInvitation, createMember, listInvitations, listMembers, removeMember, resetMemberPassword, updateMember } from '../api'
import type { MemberRole } from '../api'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'
import { Button } from '../../../shared/ui/button'
import { Dialog, DialogContent } from '../../../shared/ui/dialog'
import { useAuthStore, selectCurrentTenant } from '../../auth/store/auth'
import { toast } from '../../../utils/toast'

const ROLE_TONE: Record<MemberRole, 'accent' | 'success' | 'neutral'> = {
  OWNER: 'accent',
  ADMIN: 'success',
  MEMBER: 'neutral',
}

export function MembersConsolePage() {
  const tenant = useAuthStore(selectCurrentTenant)
  const tenantId = tenant?.id ?? null
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<MemberRole>('MEMBER')
  const [newMember, setNewMember] = useState({
    username: '',
    displayName: '',
    email: '',
    password: '',
    role: 'MEMBER' as MemberRole,
  })
  const [resetTarget, setResetTarget] = useState<{ userId: number; name: string } | null>(null)
  const [resetPassword, setResetPassword] = useState('')

  const membersQuery = useQuery({
    queryKey: ['members', tenantId],
    queryFn: () => listMembers(tenantId as number),
    enabled: tenantId != null,
  })

  const invitationsQuery = useQuery({
    queryKey: ['invitations', tenantId],
    queryFn: () => listInvitations(tenantId as number),
    enabled: tenantId != null,
  })

  const inviteMutation = useMutation({
    mutationFn: () => createInvitation(tenantId as number, { email: email.trim(), role }),
    onSuccess: async () => {
      toast.success('邀请已发送')
      setEmail('')
      await queryClient.invalidateQueries({ queryKey: ['invitations', tenantId] })
    },
    onError: () => toast.error('邀请失败，请稍后重试'),
  })

  const createMemberMutation = useMutation({
    mutationFn: () =>
      createMember(tenantId as number, {
        username: newMember.username.trim(),
        displayName: newMember.displayName.trim() || undefined,
        email: newMember.email.trim() || undefined,
        password: newMember.password,
        role: newMember.role,
      }),
    onSuccess: async () => {
      toast.success('成员账号已创建')
      setNewMember({ username: '', displayName: '', email: '', password: '', role: 'MEMBER' })
      await queryClient.invalidateQueries({ queryKey: ['members', tenantId] })
    },
    onError: () => toast.error('创建成员失败，请检查用户名、密码或角色权限'),
  })

  const resetPasswordMutation = useMutation({
    mutationFn: () => resetMemberPassword(tenantId as number, resetTarget?.userId as number, { password: resetPassword }),
    onSuccess: () => {
      toast.success('密码已重置')
      setResetTarget(null)
      setResetPassword('')
    },
    onError: () => toast.error('重置密码失败，请检查权限或密码长度'),
  })

  async function changeRole(userId: number, nextRole: MemberRole) {
    await updateMember(tenantId as number, userId, { role: nextRole })
    await queryClient.invalidateQueries({ queryKey: ['members', tenantId] })
  }

  async function kick(userId: number) {
    if (!window.confirm('确认移除该成员？')) return
    await removeMember(tenantId as number, userId)
    await queryClient.invalidateQueries({ queryKey: ['members', tenantId] })
  }

  const canCreateMember = newMember.username.trim() && newMember.password.length >= 8
  const canResetPassword = resetPassword.length >= 8

  return (
    <ConsolePage title="成员" description="管理当前租户的成员角色与邀请。">
      <section className="surface-box" style={{ display: 'grid', gap: 12 }}>
        <p className="section-label">创建组织账号</p>
        <div className="filter-row">
          <label className="field" style={{ flex: '1 1 180px' }}>
            <span>用户名</span>
            <input
              value={newMember.username}
              onChange={(e) => setNewMember((value) => ({ ...value, username: e.target.value }))}
              placeholder="test"
            />
          </label>
          <label className="field" style={{ flex: '1 1 180px' }}>
            <span>显示名</span>
            <input
              value={newMember.displayName}
              onChange={(e) => setNewMember((value) => ({ ...value, displayName: e.target.value }))}
              placeholder="组织成员"
            />
          </label>
          <label className="field" style={{ flex: '1 1 220px' }}>
            <span>邮箱</span>
            <input
              type="email"
              value={newMember.email}
              onChange={(e) => setNewMember((value) => ({ ...value, email: e.target.value }))}
              placeholder="name@example.com"
            />
          </label>
        </div>
        <div className="filter-row">
          <label className="field" style={{ flex: '1 1 220px' }}>
            <span>初始密码</span>
            <input
              type="password"
              value={newMember.password}
              onChange={(e) => setNewMember((value) => ({ ...value, password: e.target.value }))}
              placeholder="至少 8 位"
            />
          </label>
          <label className="field" style={{ flex: '0 0 160px' }}>
            <span>角色</span>
            <select
              value={newMember.role}
              onChange={(e) => setNewMember((value) => ({ ...value, role: e.target.value as MemberRole }))}
            >
              <option value="MEMBER">MEMBER</option>
              <option value="ADMIN">ADMIN</option>
              <option value="OWNER">OWNER</option>
            </select>
          </label>
        </div>
        <div className="action-row">
          <Button loading={createMemberMutation.isPending} disabled={!canCreateMember} onClick={() => createMemberMutation.mutate()}>
            创建并加入组织
          </Button>
        </div>
      </section>

      <section className="surface-box" style={{ display: 'grid', gap: 12 }}>
        <p className="section-label">邀请成员</p>
        <div className="filter-row">
          <label className="field" style={{ flex: '1 1 260px' }}>
            <span>邮箱</span>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@example.com" />
          </label>
          <label className="field" style={{ flex: '0 0 160px' }}>
            <span>角色</span>
            <select value={role} onChange={(e) => setRole(e.target.value as MemberRole)}>
              <option value="MEMBER">MEMBER</option>
              <option value="ADMIN">ADMIN</option>
              <option value="OWNER">OWNER</option>
            </select>
          </label>
        </div>
        <div className="action-row">
          <Button loading={inviteMutation.isPending} disabled={!email.trim()} onClick={() => inviteMutation.mutate()}>
            发送邀请
          </Button>
        </div>
      </section>

      <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
        <p className="section-label">成员列表</p>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>用户</th>
                <th>角色</th>
                <th>状态</th>
                <th>加入时间</th>
                <th aria-label="操作" />
              </tr>
            </thead>
            <tbody>
              {(membersQuery.data?.data ?? []).map((member) => (
                <tr key={member.userId}>
                  <td>
                    <strong>{member.displayName || member.username}</strong>
                    <div className="mono" style={{ color: 'var(--text-muted)' }}>
                      {member.username}
                    </div>
                  </td>
                  <td>
                    <select value={member.role} onChange={(e) => changeRole(member.userId, e.target.value as MemberRole)}>
                      <option value="MEMBER">MEMBER</option>
                      <option value="ADMIN">ADMIN</option>
                      <option value="OWNER">OWNER</option>
                    </select>
                  </td>
                  <td>
                    <Badge tone={ROLE_TONE[member.role]}>{member.status}</Badge>
                  </td>
                  <td className="mono">{new Date(member.joinedAt).toLocaleDateString('zh-CN')}</td>
                  <td>
                    <div className="action-row" style={{ justifyContent: 'flex-end' }}>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => {
                          setResetTarget({ userId: member.userId, name: member.displayName || member.username })
                          setResetPassword('')
                        }}
                      >
                        重置密码
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => kick(member.userId)}>
                      移除
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
              {(membersQuery.data?.data.length ?? 0) === 0 && (
                <tr>
                  <td colSpan={5}>
                    <div className="empty-line">{membersQuery.isLoading ? '加载中…' : '暂无成员'}</div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
        <p className="section-label">待处理邀请</p>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>邮箱</th>
                <th>角色</th>
                <th>状态</th>
                <th>到期</th>
              </tr>
            </thead>
            <tbody>
              {(invitationsQuery.data?.data ?? []).map((invitation) => (
                <tr key={invitation.id}>
                  <td>{invitation.email}</td>
                  <td className="mono">{invitation.role}</td>
                  <td>
                    <Badge tone={invitation.status === 'pending' ? 'warning' : 'neutral'}>{invitation.status}</Badge>
                  </td>
                  <td className="mono">{new Date(invitation.expiresAt).toLocaleDateString('zh-CN')}</td>
                </tr>
              ))}
              {(invitationsQuery.data?.data.length ?? 0) === 0 && (
                <tr>
                  <td colSpan={4}>
                    <div className="empty-line">没有待处理的邀请。</div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <Dialog open={resetTarget != null} onOpenChange={(open) => !open && setResetTarget(null)}>
        <DialogContent title="重置成员密码">
          <div style={{ display: 'grid', gap: 14 }}>
            <p style={{ margin: 0, color: 'var(--text-muted)' }}>
              为 <strong style={{ color: 'var(--text)' }}>{resetTarget?.name}</strong> 设置新的登录密码。
            </p>
            <label className="field">
              <span>新密码</span>
              <input
                type="password"
                value={resetPassword}
                onChange={(event) => setResetPassword(event.target.value)}
                placeholder="至少 8 位"
              />
            </label>
            <div className="action-row">
              <Button variant="ghost" onClick={() => setResetTarget(null)}>
                取消
              </Button>
              <Button
                variant="primary"
                loading={resetPasswordMutation.isPending}
                disabled={!canResetPassword}
                onClick={() => resetPasswordMutation.mutate()}
              >
                确认重置
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </ConsolePage>
  )
}

export default MembersConsolePage
