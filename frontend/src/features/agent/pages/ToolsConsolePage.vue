<template>
  <section class="tools-page">
    <header class="card-shell page-header">
      <div>
        <p class="eyebrow">/tools</p>
        <h2>Tools / Plugins</h2>
        <p>查看最近工具调用、插件版本和租户启停状态。</p>
      </div>
      <button class="ghost-button" type="button" @click="loadAll">刷新</button>
    </header>

    <section class="card-shell tabs">
      <button
        v-for="tab in tabs"
        :key="tab"
        class="tab-button"
        :class="{ 'tab-button--active': activeTab === tab }"
        type="button"
        @click="activeTab = tab"
      >
        {{ tab }}
      </button>
    </section>

    <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>

    <section v-if="activeTab === 'Tools'" class="card-shell">
      <div class="filter-row">
        <input v-model="toolIdFilter" type="search" placeholder="按 toolId 筛选" @keyup.enter="loadToolCalls" />
        <button class="ghost-button" type="button" @click="loadToolCalls">查询</button>
      </div>
      <div v-if="loadingToolCalls" class="empty-line">正在加载工具调用...</div>
      <div v-else-if="toolCalls.length === 0" class="empty-line">暂无工具调用。</div>
      <div v-else class="stack">
        <article v-for="call in toolCalls" :key="call.id" class="item-card">
          <div class="item-head">
            <strong>{{ call.toolId }}</strong>
            <span class="status-chip">{{ call.status }}</span>
          </div>
          <p>run #{{ call.agentRunId }} · plugin {{ call.pluginVersion || '-' }} · latency {{ call.latencyMs ?? '-' }}ms</p>
          <p>请求：{{ call.requestSummary || '-' }}</p>
          <p>响应：{{ call.responseSummary || '-' }}</p>
          <p v-if="call.errorMessage">错误：{{ call.errorMessage }}</p>
          <pre class="metadata">{{ formatMetadata(call.metadata) }}</pre>
        </article>
      </div>
    </section>

    <section v-else class="card-shell">
      <div v-if="loadingPlugins" class="empty-line">正在加载插件列表...</div>
      <div v-else-if="plugins.length === 0" class="empty-line">暂无插件。</div>
      <div v-else class="stack">
        <article v-for="plugin in plugins" :key="plugin.pluginId" class="item-card">
          <div class="item-head">
            <div>
              <strong>{{ plugin.displayName }}</strong>
              <p>{{ plugin.pluginKey }} · v{{ plugin.version }}</p>
            </div>
            <label class="toggle-inline">
              <span>{{ plugin.enabled ? '已启用' : '已关闭' }}</span>
              <input :checked="plugin.enabled" type="checkbox" @change="togglePlugin(plugin.pluginId, !plugin.enabled)" />
            </label>
          </div>
          <p>状态：{{ plugin.status }} · 更新时间：{{ formatTime(plugin.updatedAt) }}</p>
          <p>权限：{{ permissionsLabel(plugin.manifest.permissions) }}</p>
        </article>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listPlugins, listToolCalls, updatePlugin } from '../api'
import type { PluginItem, ToolCallDetail } from '../types'

const tabs = ['Tools', 'Plugins'] as const
const activeTab = ref<(typeof tabs)[number]>('Tools')
const toolCalls = ref<ToolCallDetail[]>([])
const plugins = ref<PluginItem[]>([])
const loadingToolCalls = ref(false)
const loadingPlugins = ref(false)
const errorMessage = ref('')
const toolIdFilter = ref('')

onMounted(async () => {
  await loadAll()
})

async function loadAll() {
  await Promise.all([loadToolCalls(), loadPlugins()])
}

async function loadToolCalls() {
  loadingToolCalls.value = true
  errorMessage.value = ''
  try {
    const response = await listToolCalls({ toolId: toolIdFilter.value || undefined })
    toolCalls.value = response.data
  } catch {
    errorMessage.value = '工具调用加载失败，请稍后重试。'
  } finally {
    loadingToolCalls.value = false
  }
}

async function loadPlugins() {
  loadingPlugins.value = true
  errorMessage.value = ''
  try {
    const response = await listPlugins()
    plugins.value = response.data
  } catch {
    errorMessage.value = '插件列表加载失败，请稍后重试。'
  } finally {
    loadingPlugins.value = false
  }
}

async function togglePlugin(pluginId: number, enabled: boolean) {
  try {
    await updatePlugin(pluginId, enabled)
    await loadPlugins()
  } catch {
    errorMessage.value = '插件状态更新失败，请稍后重试。'
  }
}

function formatMetadata(metadata: Record<string, unknown>) {
  return JSON.stringify(metadata, null, 2)
}

function permissionsLabel(value: unknown) {
  if (!Array.isArray(value)) {
    return '-'
  }
  return value.join(', ')
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<style scoped>
.tools-page {
  display: grid;
  gap: 1rem;
}

.card-shell {
  border-radius: calc(var(--radius-md) + 4px);
  border: 1px solid var(--line-soft);
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
  padding: 1rem 1.2rem;
}

.page-header,
.item-head,
.filter-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: center;
}

.tabs,
.stack {
  display: grid;
  gap: 0.8rem;
}

.tab-button,
.ghost-button {
  border-radius: 999px;
  padding: 0.75rem 1rem;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.78);
}

.tab-button--active {
  background: rgba(199, 109, 63, 0.12);
  border-color: rgba(199, 109, 63, 0.36);
}

.item-card {
  padding: 1rem;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.7);
}

.metadata {
  margin: 0.75rem 0 0;
  padding: 0.8rem;
  border-radius: var(--radius-sm);
  background: rgba(17, 37, 52, 0.06);
  overflow: auto;
}

.status-chip {
  display: inline-flex;
  padding: 0.2rem 0.65rem;
  border-radius: 999px;
  background: rgba(27, 47, 61, 0.08);
}

.toggle-inline {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.eyebrow,
.error-banner,
.empty-line,
.item-card p {
  color: var(--text-secondary);
}

.eyebrow {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.page-header h2 {
  margin: 0.2rem 0;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
}

@media (max-width: 960px) {
  .page-header,
  .item-head,
  .filter-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
