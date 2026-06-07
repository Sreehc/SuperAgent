<template>
  <div class="app-shell" :class="{ 'app-shell--nav-open': navOpen }">
    <aside class="app-shell__sidebar" aria-label="主导航">
      <div class="sidebar-brand">
        <BrandLogo size="medium" show-text />
        <button class="sidebar-brand__close" type="button" aria-label="关闭导航" @click="navOpen = false">Esc</button>
      </div>

      <nav class="sidebar-nav">
        <RouterLink
          v-for="item in visibleMenuItems"
          :key="item.to"
          :to="item.to"
          class="sidebar-nav__item"
          :class="{ 'sidebar-nav__item--active': isActive(item.to) }"
          @click="navOpen = false"
        >
          <span class="sidebar-nav__icon" aria-hidden="true">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-footer">
        <span>Role</span>
        <strong>{{ authStore.currentRole ?? '未加载' }}</strong>
      </div>
    </aside>

    <div class="app-shell__overlay" aria-hidden="true" @click="navOpen = false"></div>

    <section class="app-shell__workspace">
      <header class="app-topbar">
        <div class="app-topbar__title">
          <button class="app-topbar__menu" type="button" aria-label="打开导航" @click="navOpen = true">Menu</button>
          <div>
            <p class="page-kicker">{{ route.path }}</p>
            <h1>{{ pageTitle }}</h1>
          </div>
        </div>

        <div class="app-topbar__actions">
          <label class="tenant-switcher">
            <span>租户</span>
            <select v-model="selectedTenantId" :disabled="tenantSwitching" @change="switchTenant">
              <option v-for="tenant in authStore.tenants" :key="tenant.id" :value="tenant.id">
                {{ tenant.name }} / {{ tenant.role }}
              </option>
            </select>
          </label>
          <ThemeToggle />
          <div class="identity-block">
            <strong>{{ authStore.user?.displayName || authStore.user?.username || 'User' }}</strong>
            <span>{{ authStore.user?.username || 'unknown' }}</span>
          </div>
          <button class="btn btn-ghost btn-sm" type="button" @click="logout">退出登录</button>
        </div>
      </header>

      <main class="app-content">
        <RouterView />
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { BrandLogo, ThemeToggle } from '../../components'
import { useAuthStore } from '../../features/auth/store/auth'
import type { TenantRole } from '../../features/auth/types'

interface MenuItem {
  to: string
  label: string
  icon: string
  roles: TenantRole[]
}

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const navOpen = ref(false)
const tenantSwitching = ref(false)
const selectedTenantId = ref<number | null>(authStore.currentTenantId)

const menuItems: MenuItem[] = [
  { to: '/chat', label: '对话', icon: 'CH', roles: ['OWNER', 'ADMIN', 'MEMBER'] },
  { to: '/knowledge', label: '知识库', icon: 'KB', roles: ['OWNER', 'ADMIN', 'MEMBER'] },
  { to: '/traces', label: 'Trace', icon: 'TR', roles: ['OWNER', 'ADMIN'] },
  { to: '/tools', label: 'Tools', icon: 'TL', roles: ['OWNER', 'ADMIN'] },
  { to: '/governance', label: '治理', icon: 'GV', roles: ['OWNER', 'ADMIN'] },
  { to: '/settings', label: '设置', icon: 'ST', roles: ['OWNER', 'ADMIN'] },
]

const visibleMenuItems = computed(() => {
  const role = authStore.currentRole
  return menuItems.filter((item) => role && item.roles.includes(role))
})

const pageTitle = computed(() => {
  const matched = [...route.matched].reverse().find((record) => record.meta.menuLabel)
  return (matched?.meta.menuLabel as string | undefined) ?? 'SuperAgent'
})

watch(
  () => authStore.currentTenantId,
  (value) => {
    selectedTenantId.value = value
  },
  { immediate: true },
)

function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`)
}

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
.app-shell {
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  min-height: 100vh;
}

.app-shell__sidebar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  height: 100vh;
  padding: 14px;
  border-right: 1px solid var(--color-border);
  background: var(--color-surface);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 48px;
  padding: 4px 2px 14px;
  border-bottom: 1px solid var(--color-border);
}

.sidebar-brand__close,
.app-topbar__menu {
  display: none;
}

.sidebar-nav {
  display: grid;
  align-content: start;
  gap: 6px;
  padding: 14px 0;
}

.sidebar-nav__item {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  color: var(--color-text-muted);
  font-weight: 700;
}

.sidebar-nav__item:hover {
  color: var(--color-text);
  background: var(--color-surface-subtle);
}

.sidebar-nav__item--active {
  color: var(--color-accent);
  border-color: color-mix(in srgb, var(--color-accent), transparent 64%);
  background: var(--color-accent-soft);
}

.sidebar-nav__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xs);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 800;
}

.sidebar-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
  color: var(--color-text-muted);
  font-size: 12px;
}

.sidebar-footer strong {
  color: var(--color-text);
  font-family: var(--font-mono);
}

.app-shell__workspace {
  display: grid;
  grid-template-rows: var(--topbar-height) minmax(0, 1fr);
  min-width: 0;
}

.app-topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  padding: 0 18px;
  border-bottom: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-bg), var(--color-surface) 72%);
}

.app-topbar__title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.app-topbar__title h1 {
  margin: 0;
  font-size: 18px;
  letter-spacing: -0.01em;
}

.app-topbar__title .page-kicker {
  margin-bottom: 2px;
}

.app-topbar__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 0;
}

.tenant-switcher {
  display: grid;
  grid-template-columns: auto minmax(170px, 240px);
  align-items: center;
  gap: 8px;
}

.tenant-switcher span {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.tenant-switcher select {
  min-height: 34px;
}

.identity-block {
  display: grid;
  justify-items: end;
  line-height: 1.2;
}

.identity-block strong {
  font-size: 13px;
}

.identity-block span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.app-content {
  min-width: 0;
  padding: 18px;
}

.app-shell__overlay {
  display: none;
}

@media (max-width: 900px) {
  .app-shell {
    grid-template-columns: 1fr;
  }

  .app-shell__sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    width: min(82vw, var(--sidebar-width));
    transform: translateX(-100%);
    transition: transform var(--duration-base) var(--ease-standard);
  }

  .app-shell--nav-open .app-shell__sidebar {
    transform: translateX(0);
  }

  .app-shell__overlay {
    position: fixed;
    inset: 0;
    z-index: 25;
    display: block;
    visibility: hidden;
    background: rgba(15, 23, 42, 0.42);
    opacity: 0;
    transition: opacity var(--duration-base) var(--ease-standard), visibility var(--duration-base) var(--ease-standard);
  }

  .app-shell--nav-open .app-shell__overlay {
    visibility: visible;
    opacity: 1;
  }

  .sidebar-brand__close,
  .app-topbar__menu {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 32px;
    padding: 0 9px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    background: var(--color-surface);
    color: var(--color-text-muted);
    font-family: var(--font-mono);
    font-size: 11px;
    font-weight: 800;
  }

  .app-topbar {
    min-height: var(--topbar-height);
  }

  .app-topbar__actions {
    gap: 8px;
  }

  .tenant-switcher,
  .identity-block {
    display: none;
  }
}

@media (max-width: 620px) {
  .app-topbar {
    padding: 0 12px;
  }

  .app-content {
    padding: 12px;
  }
}
</style>
