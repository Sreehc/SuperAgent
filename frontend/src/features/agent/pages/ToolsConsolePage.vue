<template>
  <section class="tools-page">
    <header class="card-shell page-header">
      <div>
        <p class="eyebrow">/tools</p>
        <h2>工具控制台</h2>
        <p>查看最近工具调用、插件版本和租户启停状态。</p>
      </div>
      <button class="ghost-button" type="button" @click="loadAll">刷新</button>
    </header>

    <section class="card-shell tabs">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="tab-button"
        :class="{ 'tab-button--active': activeTab === tab.id }"
        type="button"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
      </button>
    </section>

    <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>

    <section v-if="activeTab === 'toolCalls'" class="card-shell">
      <div class="filter-row">
        <input v-model="toolIdFilter" type="search" placeholder="按工具 ID 筛选" @keyup.enter="loadToolCalls" />
        <button class="ghost-button" type="button" @click="loadToolCalls">查询</button>
      </div>
      <div v-if="loadingToolCalls" class="empty-line">正在加载工具调用...</div>
      <div v-else-if="toolCalls.length === 0" class="empty-line">暂无工具调用。</div>
      <div v-else class="stack">
        <article v-for="call in toolCalls" :key="call.id" class="item-card">
          <div class="item-head">
            <strong>{{ call.toolId }}</strong>
            <span class="status-chip">{{ statusLabel(call.status) }}</span>
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
          <p>启用工具：{{ listLabel(plugin.enabledTools) }}</p>
          <p>密钥引用：{{ listLabel(plugin.secretKeys) }}</p>
          <p>最近失败：{{ plugin.recentErrorCount }}</p>
          <pre class="metadata">{{ formatMetadata(plugin.installationConfig) }}</pre>
        </article>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listPlugins, listToolCalls, updatePlugin } from '../api'
import type { PluginItem, ToolCallDetail } from '../types'

const tabs = [
  { id: 'toolCalls' as const, label: '工具调用' },
  { id: 'plugins' as const, label: '插件管理' },
]
const activeTab = ref<'toolCalls' | 'plugins'>('toolCalls')
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

function listLabel(value: unknown) {
  if (!Array.isArray(value) || value.length === 0) {
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

function statusLabel(status: string) {
  const map: Record<string, string> = {
    success: '成功',
    failed: '失败',
    running: '运行中',
    pending: '待处理',
  }
  return map[status] ?? status
}
</script>

<style scoped>
.tools-page {
  display: grid;
  gap: 16px;
}

.card-shell {
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.page-header,
.item-head,
.filter-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px;
}

.stack {
  display: grid;
  gap: 10px;
}

.tab-button,
.ghost-button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font-weight: 700;
}

.tab-button--active {
  color: var(--color-accent);
  border-color: color-mix(in srgb, var(--color-accent), transparent 58%);
  background: var(--color-accent-soft);
}

.item-card {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted);
}

.item-card p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xs);
  background: var(--color-surface-subtle);
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.toggle-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.eyebrow,
.error-banner,
.empty-line {
  color: var(--color-text-muted);
}

.eyebrow {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 11px;
}

.page-header h2 {
  margin: 0;
  font-size: 22px;
}

.page-header p {
  margin: 6px 0 0;
  color: var(--color-text-muted);
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
