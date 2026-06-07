<template>
  <section class="trace-page page-stack">
    <header class="page-header">
      <div>
        <p class="page-kicker">/traces</p>
        <h2>Trace 列表</h2>
        <p>定位失败请求、慢请求和 Agent/RAG 运行链路。</p>
      </div>
    </header>

    <section class="toolbar">
      <label class="field toolbar-field">
        <span>状态</span>
        <select v-model="traceStore.statusFilter" @change="traceStore.fetchTraces">
          <option value="">全部状态</option>
          <option value="success">成功</option>
          <option value="failed">失败</option>
          <option value="stopped">已停止</option>
          <option value="running">运行中</option>
        </select>
      </label>
      <label class="field toolbar-field">
        <span>模式</span>
        <select v-model="traceStore.modeFilter" @change="traceStore.fetchTraces">
          <option value="">全部模式</option>
          <option value="RAG_QA">RAG问答</option>
          <option value="CLARIFICATION">澄清</option>
          <option value="REACT_AGENT">智能体</option>
        </select>
      </label>
      <label class="field toolbar-field">
        <span>用户 ID</span>
        <input v-model="traceStore.userIdFilter" type="search" placeholder="按用户 ID 筛选" @keyup.enter="traceStore.fetchTraces" />
      </label>
      <button class="btn btn-secondary" data-testid="trace-refresh" type="button" @click="traceStore.fetchTraces">刷新</button>
    </section>

    <p v-if="traceStore.errorMessage" class="error-banner">{{ traceStore.errorMessage }}</p>

    <LoadingSpinner v-if="traceStore.loadingList" text="正在加载 Trace 列表..." />
    <EmptyState v-else-if="traceStore.traces.length === 0" variant="search" title="暂无 Trace" description="没有找到匹配的 Trace 记录。" />
    <section v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>Exchange ID</th>
            <th>模式</th>
            <th>状态</th>
            <th>耗时</th>
            <th>会话</th>
            <th>用户</th>
            <th>开始时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="trace in traceStore.traces" :key="trace.exchangeId" class="table-row" :data-testid="`trace-row-${trace.exchangeId}`" @click="openTrace(trace.exchangeId)">
            <td class="mono">#{{ trace.exchangeId }}</td>
            <td>{{ modeLabel(trace.executionMode) }}</td>
            <td><span class="badge" :class="statusClass(trace.status)">{{ statusLabel(trace.status) }}</span></td>
            <td :class="{ 'duration-hot': trace.durationMs > 4000 }">{{ formatDuration(trace.durationMs) }}</td>
            <td class="mono">{{ trace.sessionId }}</td>
            <td class="mono">{{ trace.userId }}</td>
            <td>{{ formatTime(trace.startedAt) }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="traceStore.total > traceStore.pageSize" class="pagination">
        <button class="btn btn-ghost btn-sm" type="button" :disabled="traceStore.page <= 1" @click="traceStore.goToPage(traceStore.page - 1)">上一页</button>
        <span>第 {{ traceStore.page }} 页 / 共 {{ Math.ceil(traceStore.total / traceStore.pageSize) }} 页</span>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="traceStore.page >= Math.ceil(traceStore.total / traceStore.pageSize)" @click="traceStore.goToPage(traceStore.page + 1)">下一页</button>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { LoadingSpinner, EmptyState } from '../../../components'
import { useTraceStore } from '../store/traces'

const router = useRouter()
const traceStore = useTraceStore()

onMounted(async () => {
  await traceStore.fetchTraces()
})

async function openTrace(exchangeId: number) {
  await router.push(`/traces/${exchangeId}`)
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatDuration(durationMs: number) {
  if (durationMs < 1000) {
    return `${durationMs}ms`
  }
  return `${(durationMs / 1000).toFixed(1)}s`
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    success: '成功',
    failed: '失败',
    stopped: '已停止',
    running: '运行中',
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

function modeLabel(mode: string) {
  const map: Record<string, string> = {
    RAG_QA: 'RAG问答',
    CLARIFICATION: '澄清',
    REACT_AGENT: '智能体',
  }
  return map[mode] ?? mode
}
</script>

<style scoped>
.toolbar-field {
  width: min(220px, 100%);
}

.table-row {
  cursor: pointer;
}

.duration-hot {
  color: var(--color-danger);
  font-weight: 800;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px;
  border-top: 1px solid var(--color-border);
  color: var(--color-text-muted);
}
</style>
