<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../../auth/store/auth'
import {
  createInvitation,
  listInvitations,
  listMembers,
  removeMember,
  revokeInvitation,
  updateMember,
  type InvitationItem,
  type MemberItem,
  type MemberRole,
} from '../api'

const authStore = useAuthStore()

const tenantId = computed(() => authStore.currentTenantId)
const members = ref<MemberItem[]>([])
const invitations = ref<InvitationItem[]>([])
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const showInviteForm = ref(false)
const inviteEmail = ref('')
const inviteRole = ref<MemberRole>('MEMBER')
const lastInviteToken = ref('')

async function refresh() {
  if (!tenantId.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    const [m, i] = await Promise.all([
      listMembers(tenantId.value),
      listInvitations(tenantId.value),
    ])
    members.value = m
    invitations.value = i
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载成员列表失败'
  } finally {
    loading.value = false
  }
}

async function submitInvite() {
  if (!tenantId.value || !inviteEmail.value.trim()) return
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await createInvitation(tenantId.value, {
      email: inviteEmail.value.trim(),
      role: inviteRole.value,
    })
    lastInviteToken.value = result.token
    successMessage.value = `已邀请 ${result.email}（请将邀请 token 发给受邀人）`
    inviteEmail.value = ''
    showInviteForm.value = false
    await refresh()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '邀请失败'
  }
}

async function onRevoke(invitation: InvitationItem) {
  if (!tenantId.value) return
  if (!confirm(`确认撤销邀请 ${invitation.email}？`)) return
  try {
    await revokeInvitation(tenantId.value, invitation.id)
    await refresh()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '撤销失败'
  }
}

async function onChangeRole(member: MemberItem, role: MemberRole) {
  if (!tenantId.value) return
  try {
    await updateMember(tenantId.value, member.userId, { role })
    await refresh()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '修改角色失败'
  }
}

async function onToggleStatus(member: MemberItem) {
  if (!tenantId.value) return
  const next = member.status === 'active' ? 'suspended' : 'active'
  try {
    await updateMember(tenantId.value, member.userId, { status: next })
    await refresh()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '更新状态失败'
  }
}

async function onRemove(member: MemberItem) {
  if (!tenantId.value) return
  if (!confirm(`确认从租户移除 ${member.username}？`)) return
  try {
    await removeMember(tenantId.value, member.userId)
    await refresh()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '移除失败'
  }
}

onMounted(refresh)
</script>

<template>
  <div class="page-members">
    <header class="page-header">
      <div>
        <h1>成员管理</h1>
        <p class="muted">管理当前租户下的成员、角色和邀请</p>
      </div>
      <button type="button" class="btn-primary" @click="showInviteForm = !showInviteForm">
        {{ showInviteForm ? '取消' : '邀请成员' }}
      </button>
    </header>

    <section v-if="errorMessage" class="error-banner">{{ errorMessage }}</section>
    <section v-if="successMessage" class="success-banner">{{ successMessage }}</section>

    <section v-if="lastInviteToken" class="card">
      <h2>邀请 token</h2>
      <p class="muted">将此 token 安全地发送给受邀人，TTL 7 天。</p>
      <code class="token-box">{{ lastInviteToken }}</code>
    </section>

    <section v-if="showInviteForm" class="card">
      <h2>新建邀请</h2>
      <form @submit.prevent="submitInvite">
        <label>邮箱
          <input v-model="inviteEmail" type="email" required placeholder="user@example.com" />
        </label>
        <label>角色
          <select v-model="inviteRole">
            <option value="MEMBER">MEMBER</option>
            <option value="ADMIN">ADMIN</option>
          </select>
        </label>
        <button class="btn-primary" type="submit">发送邀请</button>
      </form>
    </section>

    <section class="card">
      <h2>成员（{{ members.length }}）</h2>
      <table class="data-table">
        <thead>
          <tr><th>用户</th><th>角色</th><th>状态</th><th>加入时间</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="member in members" :key="member.userId">
            <td>
              <div>{{ member.displayName || member.username }}</div>
              <div class="muted small">{{ member.username }}</div>
            </td>
            <td>
              <select :value="member.role" @change="onChangeRole(member, ($event.target as HTMLSelectElement).value as MemberRole)">
                <option value="OWNER">OWNER</option>
                <option value="ADMIN">ADMIN</option>
                <option value="MEMBER">MEMBER</option>
              </select>
            </td>
            <td>
              <button class="link" @click="onToggleStatus(member)">
                {{ member.status === 'active' ? '活跃（点击停用）' : '停用（点击恢复）' }}
              </button>
            </td>
            <td>{{ member.joinedAt }}</td>
            <td>
              <button class="link danger" @click="onRemove(member)">移除</button>
            </td>
          </tr>
          <tr v-if="!members.length"><td colspan="5" class="muted">暂无成员</td></tr>
        </tbody>
      </table>
    </section>

    <section class="card">
      <h2>邀请记录（{{ invitations.length }}）</h2>
      <table class="data-table">
        <thead><tr><th>邮箱</th><th>角色</th><th>状态</th><th>过期</th><th></th></tr></thead>
        <tbody>
          <tr v-for="invitation in invitations" :key="invitation.id">
            <td>{{ invitation.email }}</td>
            <td>{{ invitation.role }}</td>
            <td>{{ invitation.status }}</td>
            <td>{{ invitation.expiresAt }}</td>
            <td>
              <button v-if="invitation.status === 'pending'" class="link danger" @click="onRevoke(invitation)">撤销</button>
            </td>
          </tr>
          <tr v-if="!invitations.length"><td colspan="5" class="muted">暂无邀请</td></tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.page-members { padding: 1.5rem; display: flex; flex-direction: column; gap: 1rem; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.muted { color: var(--text-muted, #888); }
.small { font-size: 0.85em; }
.card { background: var(--surface, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 8px; padding: 1rem; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { text-align: left; padding: 0.5rem; border-bottom: 1px solid var(--border, #e5e7eb); }
.btn-primary { background: var(--accent, #4f46e5); color: white; border: none; padding: 0.5rem 1rem; border-radius: 6px; cursor: pointer; }
.link { background: none; border: none; color: var(--accent, #4f46e5); cursor: pointer; padding: 0; }
.link.danger { color: #dc2626; }
.error-banner { background: #fee2e2; color: #991b1b; padding: 0.75rem 1rem; border-radius: 6px; }
.success-banner { background: #d1fae5; color: #065f46; padding: 0.75rem 1rem; border-radius: 6px; }
.token-box { display: block; padding: 0.5rem; background: #f3f4f6; border-radius: 4px; word-break: break-all; }
form { display: grid; grid-template-columns: 1fr 1fr auto; gap: 0.75rem; align-items: end; }
form label { display: flex; flex-direction: column; gap: 0.25rem; }
input, select { padding: 0.4rem; border: 1px solid var(--border, #e5e7eb); border-radius: 4px; }
</style>
