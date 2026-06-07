<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="isOpen" class="command-palette-overlay" @click="close">
        <section class="command-palette" aria-label="命令面板" @click.stop>
          <div class="command-palette__search">
            <span class="search-token" aria-hidden="true">CMD</span>
            <input
              ref="searchInput"
              v-model="query"
              type="text"
              placeholder="搜索页面"
              @keydown.down.prevent="selectNext"
              @keydown.up.prevent="selectPrev"
              @keydown.enter="executeSelected"
              @keydown.esc="close"
            />
            <kbd class="kbd">ESC</kbd>
          </div>

          <div v-if="filteredItems.length > 0" class="command-palette__results">
            <button
              v-for="(item, index) in filteredItems"
              :key="item.id"
              class="result-item"
              :class="{ 'result-item--active': index === selectedIndex }"
              type="button"
              @click="execute(item)"
              @mouseenter="selectedIndex = index"
            >
              <span class="result-icon">{{ item.icon }}</span>
              <span class="result-content">
                <strong class="result-title">{{ item.title }}</strong>
                <span v-if="item.description" class="result-description">{{ item.description }}</span>
              </span>
              <kbd v-if="item.shortcut" class="kbd">{{ item.shortcut }}</kbd>
            </button>
          </div>

          <div v-else class="command-palette__empty">
            <EmptyState variant="search" title="未找到结果" description="换一个页面名称或模块关键词。" />
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import EmptyState from './EmptyState.vue'

interface CommandItem {
  id: string
  title: string
  description?: string
  icon: string
  shortcut?: string
  action: () => void
}

const router = useRouter()
const isOpen = ref(false)
const query = ref('')
const searchInput = ref<HTMLInputElement>()
const selectedIndex = ref(0)

const commands = ref<CommandItem[]>([
  { id: 'chat', title: '对话工作台', description: '进入对话页面', icon: 'CH', action: () => router.push('/chat') },
  { id: 'knowledge', title: '知识库', description: '管理知识库和文档', icon: 'KB', action: () => router.push('/knowledge') },
  { id: 'traces', title: 'Trace 列表', description: '查看执行追踪', icon: 'TR', action: () => router.push('/traces') },
  { id: 'settings', title: '系统设置', description: '配置模型和参数', icon: 'ST', action: () => router.push('/settings') },
  { id: 'governance', title: '治理控制台', description: '知识域和切块策略', icon: 'GV', action: () => router.push('/governance') },
  { id: 'tools', title: '工具控制台', description: '工具调用和插件状态', icon: 'TL', action: () => router.push('/tools') },
])

const filteredItems = computed(() => {
  const normalizedQuery = query.value.trim().toLowerCase()
  if (!normalizedQuery) {
    return commands.value
  }

  return commands.value.filter((item) =>
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
  if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
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
.command-palette-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 14vh;
  background: rgba(15, 23, 42, 0.48);
}

.command-palette {
  display: grid;
  width: min(640px, calc(100vw - 32px));
  max-height: min(560px, 72vh);
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-raised);
  box-shadow: var(--shadow-popover);
}

.command-palette__search {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-bottom: 1px solid var(--color-border);
}

.search-token,
.kbd,
.result-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 26px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xs);
  color: var(--color-text-muted);
  background: var(--color-surface-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 800;
}

.command-palette__search input {
  min-height: 38px;
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
  font-size: 16px;
}

.command-palette__search input:focus {
  box-shadow: none;
}

.command-palette__results {
  display: grid;
  max-height: 440px;
  overflow-y: auto;
  padding: 8px;
}

.result-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  color: var(--color-text);
  text-align: left;
  background: transparent;
}

.result-item:hover,
.result-item--active {
  border-color: color-mix(in srgb, var(--color-accent), transparent 62%);
  background: var(--color-accent-soft);
}

.result-content {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.result-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-description {
  overflow: hidden;
  color: var(--color-text-muted);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.command-palette__empty {
  padding: 12px;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity var(--duration-base) var(--ease-standard);
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-active .command-palette,
.modal-leave-active .command-palette {
  transition: transform var(--duration-base) var(--ease-standard);
}

.modal-enter-from .command-palette,
.modal-leave-to .command-palette {
  transform: translateY(-8px);
}
</style>
