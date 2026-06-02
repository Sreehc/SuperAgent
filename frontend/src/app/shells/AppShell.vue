<template>
  <div class="shell">
    <aside class="shell__nav">
      <div class="brand-card">
        <p class="brand-card__eyebrow">SuperAgent</p>
        <h1>AI 工作台</h1>
        <p class="brand-card__body">
          直接进入对话、知识和运行态控制。菜单会根据当前租户角色收敛。
        </p>
      </div>

      <nav class="nav-list" aria-label="主导航">
        <RouterLink
          v-for="item in visibleMenuItems"
          :key="item.to"
          :to="item.to"
          class="nav-list__item"
          :class="{ 'nav-list__item--active': route.path === item.to || route.path.startsWith(`${item.to}/`) }"
        >
          <span>{{ item.label }}</span>
          <small>{{ item.caption }}</small>
        </RouterLink>
      </nav>

      <div class="nav-footer">
        <span class="nav-footer__label">当前角色</span>
        <strong>{{ authStore.currentRole ?? '未加载' }}</strong>
      </div>
    </aside>

    <div class="shell__main">
      <header class="topbar">
        <div>
          <p class="topbar__eyebrow">当前租户</p>
          <div class="topbar__tenant">
            <select
              v-model="selectedTenantId"
              class="topbar__select"
              :disabled="tenantSwitching"
              @change="switchTenant"
            >
              <option
                v-for="tenant in authStore.tenants"
                :key="tenant.id"
                :value="tenant.id"
              >
                {{ tenant.name }} · {{ tenant.role }}
              </option>
            </select>
          </div>
        </div>

        <div class="topbar__actions">
          <div class="topbar__identity">
            <span>{{ authStore.user?.displayName }}</span>
            <small>{{ authStore.user?.username }}</small>
          </div>
          <button class="topbar__logout" type="button" @click="logout">
            退出登录
          </button>
        </div>
      </header>

      <main class="content-panel">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../features/auth/store/auth'
import type { TenantRole } from '../../features/auth/types'

interface MenuItem {
  to: string
  label: string
  caption: string
  roles: TenantRole[]
}

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const tenantSwitching = ref(false)
const selectedTenantId = ref<number | null>(authStore.currentTenantId)

watch(
  () => authStore.currentTenantId,
  (value) => {
    selectedTenantId.value = value
  },
  { immediate: true },
)

const visibleMenuItems = computed(() => {
  const base: MenuItem[] = [
    { to: '/chat', label: '对话', caption: '登录后的默认工作台', roles: ['OWNER', 'ADMIN', 'MEMBER'] },
    { to: '/knowledge', label: '知识库', caption: '成员可见，支持版本和图谱查看', roles: ['OWNER', 'ADMIN', 'MEMBER'] },
    { to: '/traces', label: 'Trace', caption: '问答 Trace 与 Agent Run 双视角', roles: ['OWNER', 'ADMIN'] },
    { to: '/tools', label: 'Tools', caption: '插件、工具调用与风控入口', roles: ['OWNER', 'ADMIN'] },
    { to: '/governance', label: '治理', caption: '知识域、切块策略与图谱文档', roles: ['OWNER', 'ADMIN'] },
    { to: '/settings', label: '设置', caption: '模型、RAG、Agent 与 Tools', roles: ['OWNER', 'ADMIN'] },
  ]

  const role = authStore.currentRole
  return base.filter((item) => role && item.roles.includes(role))
})

async function switchTenant() {
  if (!selectedTenantId.value || selectedTenantId.value === authStore.currentTenantId) {
    return
  }

  tenantSwitching.value = true
  try {
    await authStore.switchTenant(selectedTenantId.value)
    const allowedRoles = route.meta.roles as Array<'OWNER' | 'ADMIN' | 'MEMBER'> | undefined
    if (allowedRoles?.length && authStore.currentRole && !allowedRoles.includes(authStore.currentRole)) {
      await router.push('/forbidden')
    }
  } finally {
    tenantSwitching.value = false
  }
}

async function logout() {
  await authStore.logout()
  await router.replace('/login')
}
</script>

<style scoped>
.shell {
  display: grid;
  min-height: 100vh;
  grid-template-columns: 292px minmax(0, 1fr);
}

.shell__nav {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding: 1.5rem;
  background:
    linear-gradient(180deg, rgba(17, 37, 52, 0.98), rgba(25, 48, 65, 0.96)),
    radial-gradient(circle at bottom left, rgba(199, 109, 63, 0.22), transparent 35%);
  color: var(--text-contrast);
}

.brand-card {
  padding: 1.4rem;
  border: 1px solid rgba(255, 247, 239, 0.14);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.04);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.brand-card h1 {
  margin: 0.1rem 0 0.8rem;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
  font-size: 2rem;
}

.brand-card__eyebrow,
.topbar__eyebrow,
.nav-footer__label {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(255, 247, 239, 0.65);
}

.brand-card__body {
  margin: 0;
  color: rgba(255, 247, 239, 0.82);
}

.nav-list {
  display: grid;
  gap: 0.75rem;
}

.nav-list__item {
  display: grid;
  gap: 0.18rem;
  padding: 0.95rem 1rem;
  border-radius: var(--radius-md);
  color: rgba(255, 247, 239, 0.88);
  border: 1px solid rgba(255, 247, 239, 0.08);
  transition: transform 180ms ease, border-color 180ms ease, background-color 180ms ease;
}

.nav-list__item small {
  color: rgba(255, 247, 239, 0.56);
}

.nav-list__item:hover,
.nav-list__item--active {
  transform: translateX(4px);
  background: rgba(255, 247, 239, 0.08);
  border-color: rgba(255, 247, 239, 0.18);
}

.nav-footer {
  margin-top: auto;
  padding: 1rem 0.2rem 0;
  border-top: 1px solid rgba(255, 247, 239, 0.12);
}

.shell__main {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.2rem;
  padding: 1.35rem 1.5rem 1rem;
}

.topbar__tenant {
  margin-top: 0.35rem;
}

.topbar__select {
  min-width: 240px;
  padding: 0.85rem 1rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--line-soft);
  background: rgba(255, 251, 245, 0.86);
  color: var(--text-primary);
}

.topbar__actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.topbar__identity {
  display: grid;
  justify-items: end;
}

.topbar__identity small {
  color: var(--text-secondary);
}

.topbar__logout {
  border: 0;
  border-radius: 999px;
  padding: 0.8rem 1.2rem;
  background: var(--bg-dark);
  color: var(--text-contrast);
  box-shadow: var(--shadow-soft);
}

.content-panel {
  min-width: 0;
  padding: 0 1.5rem 1.5rem;
}

@media (max-width: 980px) {
  .shell {
    grid-template-columns: 1fr;
  }

  .shell__nav {
    padding-bottom: 1rem;
  }

  .nav-footer {
    margin-top: 0;
  }

  .topbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .topbar__actions {
    width: 100%;
    justify-content: space-between;
  }

  .topbar__identity {
    justify-items: start;
  }

  .topbar__select {
    min-width: min(100%, 320px);
  }
}
</style>
