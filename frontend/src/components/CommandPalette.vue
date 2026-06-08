<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="isOpen" class="command-overlay" @click="close">
        <section class="command-menu" aria-label="命令面板" @click.stop>
          <div class="command-menu__search">
            <PhCommand :size="18" weight="regular" aria-hidden="true" />
            <label class="sr-only" for="command-palette-search">搜索页面</label>
            <input
              id="command-palette-search"
              ref="searchInput"
              v-model="query"
              type="text"
              placeholder="搜索模块或操作"
              @keydown.down.prevent="selectNext"
              @keydown.up.prevent="selectPrev"
              @keydown.enter="executeSelected"
              @keydown.esc="close"
            />
            <kbd class="kbd">ESC</kbd>
          </div>

          <div v-if="filteredItems.length > 0" class="command-menu__results">
            <button
              v-for="(item, index) in filteredItems"
              :key="item.id"
              class="command-result"
              :class="{ 'command-result--active': index === selectedIndex }"
              type="button"
              @click="execute(item)"
              @mouseenter="selectedIndex = index"
            >
              <span class="command-result__icon"><component :is="item.icon" :size="18" weight="regular" /></span>
              <span class="command-result__content">
                <strong>{{ item.title }}</strong>
                <small v-if="item.description">{{ item.description }}</small>
              </span>
              <kbd v-if="item.shortcut" class="kbd">{{ item.shortcut }}</kbd>
            </button>
          </div>

          <div v-else class="command-menu__empty">
            <EmptyState variant="search" title="未找到结果" description="换一个模块名称或功能关键词。" />
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { PhBooks, PhChatCenteredText, PhCommand, PhGearSix, PhGraph, PhPuzzlePiece, PhShieldCheck } from '@phosphor-icons/vue'
import EmptyState from './EmptyState.vue'
import { useAuthStore } from '../features/auth/store/auth'
import type { TenantRole } from '../features/auth/types'

interface CommandItem {
  id: string
  title: string
  description?: string
  icon: Component
  shortcut?: string
  roles: TenantRole[]
  action: () => void
}

const router = useRouter()
const authStore = useAuthStore()
const isOpen = ref(false)
const query = ref('')
const searchInput = ref<HTMLInputElement>()
const selectedIndex = ref(0)

const commands = computed<CommandItem[]>(() => [
  { id: 'chat', title: '对话工作台', description: '进入 AI workspace', icon: PhChatCenteredText, shortcut: '1', roles: ['OWNER', 'ADMIN', 'MEMBER'], action: () => router.push('/chat') },
  { id: 'knowledge', title: '知识库', description: '查看知识 inventory', icon: PhBooks, shortcut: '2', roles: ['OWNER', 'ADMIN', 'MEMBER'], action: () => router.push('/knowledge') },
  { id: 'traces', title: 'Trace', description: '运行链路和模型调用', icon: PhGraph, shortcut: '3', roles: ['OWNER', 'ADMIN'], action: () => router.push('/traces') },
  { id: 'tools', title: 'Tools', description: '工具调用和插件状态', icon: PhPuzzlePiece, roles: ['OWNER', 'ADMIN'], action: () => router.push('/tools') },
  { id: 'governance', title: '治理', description: '知识域、切块和图谱', icon: PhShieldCheck, roles: ['OWNER', 'ADMIN'], action: () => router.push('/governance') },
  { id: 'settings', title: '设置', description: '模型和 RAG 运行时', icon: PhGearSix, roles: ['OWNER', 'ADMIN'], action: () => router.push('/settings') },
])

const visibleCommands = computed(() => {
  const role = authStore.currentRole
  return commands.value.filter((item) => role && item.roles.includes(role))
})

const filteredItems = computed(() => {
  const normalizedQuery = query.value.trim().toLowerCase()
  if (!normalizedQuery) {
    return visibleCommands.value
  }

  return visibleCommands.value.filter((item) =>
    item.title.toLowerCase().includes(normalizedQuery) ||
    item.description?.toLowerCase().includes(normalizedQuery),
  )
})

function open() {
  isOpen.value = true
  query.value = ''
  selectedIndex.value = 0
  window.setTimeout(() => searchInput.value?.focus(), 100)
}

function close() {
  isOpen.value = false
}

function selectNext() {
  selectedIndex.value = Math.min(selectedIndex.value + 1, filteredItems.value.length - 1)
}

function selectPrev() {
  selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
}

function execute(item: CommandItem) {
  item.action()
  close()
}

function executeSelected() {
  const selected = filteredItems.value[selectedIndex.value]
  if (selected) {
    execute(selected)
  }
}

function handleKeydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    open()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})

watch(
  () => filteredItems.value.length,
  () => {
    selectedIndex.value = 0
  },
)

defineExpose({ open, close })
</script>

<style scoped>
.command-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 13vh;
  background: rgba(8, 10, 9, 0.58);
}

.command-menu {
  display: grid;
  width: min(660px, calc(100vw - 32px));
  max-height: min(560px, 72vh);
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-3);
  background: var(--bg-lift);
  box-shadow: var(--shadow-menu);
}

.command-menu__search {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-bottom: 1px solid var(--line-soft);
  color: var(--text-muted);
}

.command-menu__search input {
  min-height: 40px;
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
  font-size: 16px;
}

.command-menu__search input:focus {
  box-shadow: none;
}

.kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 24px;
  padding: 0 7px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  color: var(--text-muted);
  background: var(--bg-inset);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 760;
}

.command-menu__results {
  display: grid;
  max-height: 440px;
  overflow-y: auto;
  padding: 8px;
}

.command-result {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-2);
  color: var(--text-main);
  text-align: left;
  background: transparent;
}

.command-result:hover,
.command-result--active {
  border-color: color-mix(in srgb, var(--accent), transparent 60%);
  background: var(--accent-soft);
}

.command-result__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  color: var(--accent);
  background: var(--bg-surface);
}

.command-result__content {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.command-result__content strong,
.command-result__content small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.command-result__content small {
  color: var(--text-muted);
  font-size: 12px;
}

.command-menu__empty {
  padding: 10px;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity var(--duration-base) var(--ease-standard);
}

.modal-enter-active .command-menu,
.modal-leave-active .command-menu {
  transition: transform var(--duration-base) var(--ease-standard);
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .command-menu,
.modal-leave-to .command-menu {
  transform: translateY(8px) scale(0.985);
}
</style>
