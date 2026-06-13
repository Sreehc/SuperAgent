<template>
  <section class="audit-page workspace-page">
    <header class="workspace-strip">
      <div class="workspace-title">
        <p class="section-label">Audit trail</p>
        <h1>审计日志</h1>
        <p>查询租户内设置、工具、插件和管理操作记录。</p>
      </div>
      <div class="meta-row">
        <span class="metric-chip">total {{ total }}</span>
      </div>
    </header>

    <section class="filter-row">
      <label class="field audit-filter">
        <span>Action</span>
        <input v-model="filters.action" type="search" placeholder="tools.secret.updated" @keyup.enter="fetchLogs" />
      </label>
      <label class="field audit-filter">
        <span>资源类型</span>
        <input v-model="filters.resourceType" type="search" placeholder="tool_secret" @keyup.enter="fetchLogs" />
      </label>
      <label class="field audit-filter">
        <span>操作人 ID</span>
        <input v-model="filters.actorId" type="search" placeholder="user id" @keyup.enter="fetchLogs" />
      </label>
      <button class="btn btn-secondary" type="button" @click="fetchLogs">刷新</button>
    </section>

    <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>

    <LoadingSpinner v-if="loading" text="正在加载审计日志..." />
    <EmptyState v-else-if="logs.length === 0" variant="search" title="暂无审计日志" description="没有找到匹配的审计记录。" />
    <section v-else class="data-frame">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Action</th>
            <th>资源</th>
            <th>操作人</th>
            <th>时间</th>
            <th>详情</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td class="mono">#{{ log.id }}</td>
            <td><strong>{{ log.action }}</strong></td>
            <td>{{ log.resourceType }}<span v-if="log.resourceId" class="mono"> #{{ log.resourceId }}</span></td>
            <td class="mono">{{ log.actorId ?? '-' }}</td>
            <td>{{ formatTime(log.createdAt) }}</td>
            <td><pre class="audit-detail">{{ formatDetail(log.detail) }}</pre></td>
          </tr>
        </tbody>
      </table>
      <div v-if="total > pageSize" class="pagination">
        <button class="btn btn-ghost btn-sm" type="button" :disabled="page <= 1" @click="goToPage(page - 1)">上一页</button>
        <span>第 {{ page }} 页 / 共 {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="page >= Math.ceil(total / pageSize)" @click="goToPage(page + 1)">下一页</button>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { EmptyState, LoadingSpinner } from '../../../components'
import { listAuditLogs } from '../api'
import type { AuditLogItem } from '../types'

const logs = ref<AuditLogItem[]>([])
const loading = ref(false)
const errorMessage = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filters = ref({
  action: '',
  resourceType: '',
  actorId: '',
})

onMounted(fetchLogs)

async function fetchLogs() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listAuditLogs({
      page: page.value,
      pageSize: pageSize.value,
      action: filters.value.action || undefined,
      resourceType: filters.value.resourceType || undefined,
      actorId: filters.value.actorId || undefined,
    })
    logs.value = response.data.items
    total.value = response.data.total
  } catch {
    errorMessage.value = '审计日志加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function goToPage(nextPage: number) {
  page.value = nextPage
  await fetchLogs()
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatDetail(detail: Record<string, unknown>) {
  return JSON.stringify(detail, null, 2)
}
</script>

<style scoped>
.audit-filter {
  width: min(260px, 100%);
}

.audit-detail {
  max-width: 360px;
  max-height: 110px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
}
</style>
