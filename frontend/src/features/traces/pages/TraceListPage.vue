<template>
  <section class="trace-page">
    <header class="page-header card-shell">
      <div>
        <p class="eyebrow">/traces</p>
        <h2>Trace 列表</h2>
        <p>按状态、模式和用户快速定位失败请求与慢请求。</p>
      </div>
    </header>

    <section class="filters card-shell">
      <select v-model="traceStore.statusFilter" @change="traceStore.fetchTraces">
        <option value="">全部状态</option>
        <option value="success">success</option>
        <option value="failed">failed</option>
        <option value="stopped">stopped</option>
        <option value="running">running</option>
      </select>
      <select v-model="traceStore.modeFilter" @change="traceStore.fetchTraces">
        <option value="">全部模式</option>
        <option value="RAG_QA">RAG_QA</option>
        <option value="CLARIFICATION">CLARIFICATION</option>
      </select>
      <input v-model="traceStore.userIdFilter" type="search" placeholder="按用户 ID 筛选" @keyup.enter="traceStore.fetchTraces" />
      <button class="ghost-button" data-testid="trace-refresh" type="button" @click="traceStore.fetchTraces">刷新</button>
    </section>

    <p v-if="traceStore.errorMessage" class="error-banner">{{ traceStore.errorMessage }}</p>

    <section v-if="traceStore.loadingList" class="card-shell">正在加载 Trace 列表...</section>
    <section v-else class="card-shell">
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
          <tr v-if="traceStore.traces.length === 0">
            <td colspan="7">暂无 Trace。</td>
          </tr>
          <tr
            v-for="trace in traceStore.traces"
            :key="trace.exchangeId"
            class="table-row"
            :data-testid="`trace-row-${trace.exchangeId}`"
            @click="openTrace(trace.exchangeId)"
          >
            <td>#{{ trace.exchangeId }}</td>
            <td>{{ trace.executionMode }}</td>
            <td><span class="status-chip" :class="`status-chip--${trace.status}`">{{ trace.status }}</span></td>
            <td :class="{ 'duration-hot': trace.durationMs > 4000 }">{{ formatDuration(trace.durationMs) }}</td>
            <td>{{ trace.sessionId }}</td>
            <td>{{ trace.userId }}</td>
            <td>{{ formatTime(trace.startedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
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
</script>

<style scoped>
.trace-page {
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

.filters {
  display: flex;
  gap: 0.8rem;
  align-items: center;
  flex-wrap: wrap;
}

.filters input,
.filters select {
  padding: 0.8rem 0.95rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.84);
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.table th,
.table td {
  padding: 0.95rem 0.75rem;
  border-bottom: 1px solid var(--line-soft);
  text-align: left;
}

.table-row {
  cursor: pointer;
}

.table-row:hover {
  background: rgba(209, 148, 104, 0.08);
}

.status-chip {
  display: inline-flex;
  padding: 0.2rem 0.65rem;
  border-radius: 999px;
  background: rgba(27, 47, 61, 0.08);
}

.status-chip--failed {
  background: rgba(176, 56, 40, 0.14);
  color: #8e2e22;
}

.duration-hot {
  color: #9b4a1e;
  font-weight: 700;
}

.eyebrow {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.page-header h2 {
  margin: 0.2rem 0;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
}

.ghost-button {
  border-radius: 999px;
  padding: 0.75rem 1rem;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.78);
}

.error-banner {
  margin: 0;
  color: #8e2e22;
}
</style>
