<template>
  <section class="tools-console workspace-page">
    <header class="workspace-strip">
      <div class="workspace-title">
        <p class="section-label">Tool execution console</p>
        <h1>工具控制台</h1>
        <p>查看最近工具调用、插件版本和租户启停状态。</p>
      </div>
      <button class="btn btn-ghost btn-sm" type="button" @click="loadAll">刷新</button>
    </header>

    <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>

    <section class="tools-layout">
      <aside class="registry-pane">
        <div class="pane-title">
          <p class="section-label">Registry</p>
          <h2>插件</h2>
        </div>
        <LoadingSpinner v-if="loadingPlugins" size="small" text="正在加载插件列表..." />
        <div v-else-if="plugins.length === 0" class="empty-line">暂无插件。</div>
        <template v-else>
          <button
            v-for="plugin in plugins"
            :key="plugin.pluginId"
            class="plugin-row"
            :class="{ 'plugin-row--active': selectedPlugin?.pluginId === plugin.pluginId }"
            type="button"
            @click="selectedPlugin = plugin"
          >
            <strong>{{ plugin.displayName }}</strong>
            <span>{{ plugin.pluginKey }} · v{{ plugin.version }}</span>
            <em>{{ plugin.enabled ? 'enabled' : 'disabled' }}</em>
          </button>
        </template>
      </aside>

      <main class="calls-pane">
        <section class="filter-row">
          <label class="field filter-field">
            <span>工具 ID</span>
            <input v-model="toolIdFilter" type="search" placeholder="例如 web.search" @keyup.enter="loadToolCalls" />
          </label>
          <button class="btn btn-secondary" type="button" @click="loadToolCalls">查询</button>
        </section>

        <LoadingSpinner v-if="loadingToolCalls" text="正在加载工具调用..." />
        <div v-else-if="toolCalls.length === 0" class="empty-line">暂无工具调用。</div>
        <section v-else class="data-frame">
          <table class="data-table">
            <thead>
              <tr>
                <th>工具</th>
                <th>状态</th>
                <th>Run</th>
                <th>耗时</th>
                <th>请求</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="call in toolCalls" :key="call.id" :class="{ 'row-hot': call.status === 'failed' }" @click="selectedCall = call">
                <td><strong>{{ call.toolId }}</strong></td>
                <td><span class="badge" :class="statusClass(call.status)">{{ statusLabel(call.status) }}</span></td>
                <td class="mono">#{{ call.agentRunId }}</td>
                <td class="numeric">{{ call.latencyMs ?? '-' }}ms</td>
                <td>{{ call.requestSummary || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </main>

      <aside class="tool-inspector inspector-box">
        <template v-if="selectedCall">
          <p class="section-label">Selected call</p>
          <h2>{{ selectedCall.toolId }}</h2>
          <span class="badge" :class="statusClass(selectedCall.status)">{{ statusLabel(selectedCall.status) }}</span>
          <dl>
            <div><dt>run</dt><dd>#{{ selectedCall.agentRunId }}</dd></div>
            <div><dt>latency</dt><dd>{{ selectedCall.latencyMs ?? '-' }}ms</dd></div>
            <div><dt>plugin</dt><dd>{{ selectedCall.pluginVersion || '-' }}</dd></div>
          </dl>
          <p>请求：{{ selectedCall.requestSummary || '-' }}</p>
          <p>响应：{{ selectedCall.responseSummary || '-' }}</p>
          <p v-if="selectedCall.errorMessage">错误：{{ selectedCall.errorMessage }}</p>
          <details><summary>metadata</summary><pre class="metadata">{{ formatMetadata(selectedCall.metadata) }}</pre></details>
        </template>
        <template v-else-if="selectedPlugin">
          <p class="section-label">Selected plugin</p>
          <h2>{{ selectedPlugin.displayName }}</h2>
          <label class="switch-row">
            <span>{{ selectedPlugin.enabled ? '已启用' : '已关闭' }}</span>
            <input :checked="selectedPlugin.enabled" type="checkbox" @change="togglePlugin(selectedPlugin.pluginId, !selectedPlugin.enabled)" />
          </label>
          <dl>
            <div><dt>key</dt><dd>{{ selectedPlugin.pluginKey }}</dd></div>
            <div><dt>version</dt><dd>{{ selectedPlugin.version }}</dd></div>
            <div><dt>status</dt><dd>{{ selectedPlugin.status }}</dd></div>
            <div><dt>recent errors</dt><dd>{{ selectedPlugin.recentErrorCount }}</dd></div>
          </dl>
          <p>权限：{{ permissionsLabel(selectedPlugin.manifest.permissions) }}</p>
          <p>启用工具：{{ listLabel(selectedPlugin.enabledTools) }}</p>
          <p>密钥引用：{{ listLabel(selectedPlugin.secretKeys) }}</p>
          <details><summary>installation config</summary><pre class="metadata">{{ formatMetadata(selectedPlugin.installationConfig) }}</pre></details>
        </template>
        <EmptyState v-else variant="search" title="选择一条记录" description="点击工具调用或插件后在这里查看详情。" />
      </aside>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { EmptyState, LoadingSpinner } from '../../../components'
import { listPlugins, listToolCalls, updatePlugin } from '../api'
import type { PluginItem, ToolCallDetail } from '../types'

const toolCalls = ref<ToolCallDetail[]>([])
const plugins = ref<PluginItem[]>([])
const selectedCall = ref<ToolCallDetail | null>(null)
const selectedPlugin = ref<PluginItem | null>(null)
const loadingToolCalls = ref(false)
const loadingPlugins = ref(false)
const errorMessage = ref('')
const toolIdFilter = ref('')

onMounted(async () => {
  await loadAll()
})

watch(toolCalls, (items) => {
  selectedCall.value = items[0] ?? null
})

watch(plugins, (items) => {
  selectedPlugin.value = items[0] ?? null
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

function statusLabel(status: string) {
  const map: Record<string, string> = {
    success: '成功',
    failed: '失败',
    running: '运行中',
    pending: '待处理',
  }
  return map[status] ?? status
}

function statusClass(status: string) {
  if (status === 'success') {
    return 'badge--success'
  }
  if (status === 'failed') {
    return 'badge--danger'
  }
  if (status === 'running') {
    return 'badge--accent'
  }
  return 'badge--warning'
}
</script>

<style scoped>
.tools-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 340px;
  gap: 12px;
}

.registry-pane,
.calls-pane {
  display: grid;
  align-content: start;
  gap: 10px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--bg-surface);
}

.pane-title h2,
.tool-inspector h2 {
  margin: 0;
  font-size: 17px;
}

.plugin-row {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 10px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  color: var(--text-main);
  background: transparent;
  text-align: left;
}

.plugin-row--active,
.plugin-row:hover {
  border-color: color-mix(in srgb, var(--accent), transparent 58%);
  background: var(--accent-soft);
}

.plugin-row span,
.plugin-row em,
.tool-inspector p {
  color: var(--text-muted);
  font-size: 12px;
  font-style: normal;
}

.filter-field {
  width: min(340px, 100%);
}

.data-table tbody tr {
  cursor: pointer;
}

.tool-inspector dl {
  display: grid;
  gap: 8px;
  margin: 0;
}

.tool-inspector dl div,
.switch-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  padding: 9px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background: var(--bg-inset);
}

.tool-inspector dt {
  color: var(--text-subtle);
  font-size: 12px;
}

.tool-inspector dd {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 12px;
}

details summary {
  cursor: pointer;
  color: var(--accent);
  font-weight: 720;
}

@media (max-width: 1180px) {
  .tools-layout {
    grid-template-columns: 260px minmax(0, 1fr);
  }

  .tool-inspector {
    grid-column: 1 / -1;
  }
}

@media (max-width: 820px) {
  .tools-layout {
    grid-template-columns: 1fr;
  }
}
</style>
