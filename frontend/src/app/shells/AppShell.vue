<template>
  <div class="console-shell" :class="{ 'console-shell--nav-open': navOpen }">
    <aside class="global-rail" aria-label="主导航">
      <RouterLink class="global-rail__mark" to="/chat" aria-label="SuperAgent 对话">
        <BrandLogo size="small" />
      </RouterLink>

      <nav class="global-rail__nav" aria-label="主导航">
        <RouterLink
          v-for="item in visibleMenuItems"
          :key="item.to"
          :to="item.to"
          class="global-rail__item"
          :class="{ 'global-rail__item--active': isActive(item.to) }"
          :aria-label="item.label"
          :title="item.label"
          @click="navOpen = false"
        >
          <component :is="item.icon" :size="20" weight="regular" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="global-rail__tools">
        <button class="global-rail__item" type="button" aria-label="打开命令面板" title="命令面板 ⌘K" @click="openCommand">
          <PhCommand :size="20" weight="regular" aria-hidden="true" />
          <span>命令</span>
        </button>
        <ThemeToggle />
      </div>
    </aside>

    <div class="console-shell__overlay" aria-hidden="true" @click="navOpen = false"></div>

    <section class="console-workspace">
      <header class="utility-bar">
        <div class="utility-bar__left">
          <button class="utility-bar__menu icon-button" type="button" aria-label="打开导航" @click="navOpen = true">
            <PhSidebarSimple :size="18" weight="regular" aria-hidden="true" />
          </button>
          <div class="tenant-chip">
            <span>Tenant</span>
            <select v-model="selectedTenantId" :disabled="tenantSwitching" @change="switchTenant">
              <option v-for="tenant in authStore.tenants" :key="tenant.id" :value="tenant.id">
                {{ tenant.name }} / {{ tenant.role }}
              </option>
            </select>
          </div>
          <span class="role-chip">{{ authStore.currentRole ?? '未加载' }}</span>
        </div>

        <button class="command-trigger" type="button" @click="openCommand">
          <PhMagnifyingGlass :size="16" weight="regular" aria-hidden="true" />
          <span>搜索页面或操作</span>
          <kbd>⌘K</kbd>
        </button>

        <div class="utility-bar__right">
          <div class="identity-block">
            <strong>{{ authStore.user?.displayName || authStore.user?.username || 'User' }}</strong>
            <span>{{ authStore.user?.username || 'unknown' }}</span>
          </div>
          <button class="btn btn-ghost btn-sm" type="button" @click="logout">
            <PhSignOut :size="15" weight="regular" aria-hidden="true" />
            退出
          </button>
        </div>
      </header>

      <main class="console-content">
        <RouterView />
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, type Component } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { PhBooks, PhChatCenteredText, PhCommand, PhGearSix, PhGraph, PhMagnifyingGlass, PhPuzzlePiece, PhShieldCheck, PhSidebarSimple, PhSignOut } from '@phosphor-icons/vue'
import { BrandLogo, ThemeToggle } from '../../components'
import { useAuthStore } from '../../features/auth/store/auth'
import type { TenantRole } from '../../features/auth/types'

interface MenuItem {
  to: string
  label: string
  icon: Component
  roles: TenantRole[]
}

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const navOpen = ref(false)
const tenantSwitching = ref(false)
const selectedTenantId = ref<number | null>(authStore.currentTenantId)

const menuItems: MenuItem[] = [
  { to: '/chat', label: '对话', icon: PhChatCenteredText, roles: ['OWNER', 'ADMIN', 'MEMBER'] },
  { to: '/knowledge', label: '知识库', icon: PhBooks, roles: ['OWNER', 'ADMIN', 'MEMBER'] },
  { to: '/traces', label: 'Trace', icon: PhGraph, roles: ['OWNER', 'ADMIN'] },
  { to: '/tools', label: 'Tools', icon: PhPuzzlePiece, roles: ['OWNER', 'ADMIN'] },
  { to: '/governance', label: '治理', icon: PhShieldCheck, roles: ['OWNER', 'ADMIN'] },
  { to: '/settings', label: '设置', icon: PhGearSix, roles: ['OWNER', 'ADMIN'] },
]

const visibleMenuItems = computed(() => {
  const role = authStore.currentRole
  return menuItems.filter((item) => role && item.roles.includes(role))
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

function openCommand() {
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', metaKey: true }))
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
.console-shell {
  display: grid;
  grid-template-columns: var(--rail-width) minmax(0, 1fr);
  min-height: 100vh;
}

.global-rail {
  position: sticky;
  top: 0;
  z-index: 40;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  justify-items: center;
  height: 100vh;
  padding: 12px 9px;
  background:
    linear-gradient(180deg, rgba(123, 210, 179, 0.08), transparent 30%),
    var(--bg-rail);
  border-right: 1px solid rgba(255, 255, 255, 0.08);
}

.global-rail__mark {
  display: inline-flex;
  margin-bottom: 18px;
}

.global-rail__nav,
.global-rail__tools {
  display: grid;
  gap: 8px;
}

.global-rail__nav {
  align-content: start;
}

.global-rail__tools {
  align-content: end;
}

.global-rail__item {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: 1px solid transparent;
  border-radius: var(--radius-2);
  color: rgba(238, 242, 236, 0.68);
  background: transparent;
  transition:
    background-color var(--duration-fast) var(--ease-standard),
    border-color var(--duration-fast) var(--ease-standard),
    color var(--duration-fast) var(--ease-standard);
}

.global-rail__item span {
  position: absolute;
  left: calc(100% + 10px);
  z-index: 2;
  padding: 6px 8px;
  color: #eef2ec;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--radius-1);
  background: #101417;
  box-shadow: var(--shadow-menu);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transform: translateX(-4px);
  transition: opacity var(--duration-fast) var(--ease-standard), transform var(--duration-fast) var(--ease-standard);
}

.global-rail__item:hover,
.global-rail__item--active {
  color: #ffffff;
  border-color: rgba(123, 210, 179, 0.22);
  background: rgba(123, 210, 179, 0.12);
}

.global-rail__item:hover span,
.global-rail__item:focus-visible span {
  opacity: 1;
  transform: translateX(0);
}

.console-workspace {
  display: grid;
  grid-template-rows: var(--utility-height) minmax(0, 1fr);
  min-width: 0;
}

.utility-bar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 420px) minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 0 14px;
  border-bottom: 1px solid var(--line-soft);
  background: color-mix(in srgb, var(--bg-canvas), var(--bg-surface) 72%);
  backdrop-filter: blur(18px);
}

.utility-bar__left,
.utility-bar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.utility-bar__right {
  justify-content: flex-end;
}

.utility-bar__menu {
  display: none;
}

.tenant-chip {
  display: grid;
  grid-template-columns: auto minmax(150px, 230px);
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.tenant-chip span,
.role-chip {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 760;
}

.tenant-chip select {
  min-height: 32px;
  background: color-mix(in srgb, var(--bg-surface), transparent 12%);
}

.role-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 8px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background: var(--bg-subtle);
}

.command-trigger {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 34px;
  padding: 0 10px;
  color: var(--text-muted);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--bg-surface);
  text-align: left;
}

.command-trigger span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.command-trigger kbd {
  min-width: 34px;
  padding: 3px 6px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background: var(--bg-inset);
  font-family: var(--font-mono);
  font-size: 10px;
}

.identity-block {
  display: grid;
  justify-items: end;
  min-width: 0;
  line-height: 1.15;
}

.identity-block strong,
.identity-block span {
  overflow: hidden;
  max-width: 180px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.identity-block strong {
  font-size: 13px;
}

.identity-block span {
  color: var(--text-muted);
  font-size: 12px;
}

.console-content {
  min-width: 0;
  padding: 14px;
}

.console-shell__overlay {
  display: none;
}

@media (max-width: 980px) {
  .console-shell {
    grid-template-columns: 1fr;
  }

  .global-rail {
    position: fixed;
    inset: 0 auto 0 0;
    width: var(--rail-width);
    transform: translateX(-100%);
    transition: transform var(--duration-base) var(--ease-standard);
  }

  .console-shell--nav-open .global-rail {
    transform: translateX(0);
  }

  .console-shell__overlay {
    position: fixed;
    inset: 0;
    z-index: 35;
    display: block;
    visibility: hidden;
    background: rgba(8, 10, 9, 0.48);
    opacity: 0;
    transition: opacity var(--duration-base) var(--ease-standard), visibility var(--duration-base) var(--ease-standard);
  }

  .console-shell--nav-open .console-shell__overlay {
    visibility: visible;
    opacity: 1;
  }

  .utility-bar {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  }

  .utility-bar__menu {
    display: inline-flex;
  }

  .tenant-chip {
    display: none;
  }
}

@media (max-width: 680px) {
  .utility-bar {
    grid-template-columns: auto minmax(0, 1fr) auto;
    padding: 0 10px;
  }

  .role-chip,
  .identity-block {
    display: none;
  }

  .command-trigger span {
    display: none;
  }

  .console-content {
    padding: 10px;
  }
}
</style>
