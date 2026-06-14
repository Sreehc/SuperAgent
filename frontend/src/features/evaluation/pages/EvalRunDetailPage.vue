<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getEvalRun, listEvalRunCases } from '../api'
import type { EvalRun, EvalRunCase } from '../types'

const route = useRoute()
const router = useRouter()

const run = ref<EvalRun | null>(null)
const cases = ref<EvalRunCase[]>([])
const statusFilter = ref<string>('')
const loading = ref(false)
const errorMessage = ref('')

const passRate = computed(() => {
  if (!run.value) return ''
  const total = run.value.passedCount + run.value.failedCount
  if (total === 0) return 'n/a'
  return `${((run.value.passedCount / total) * 100).toFixed(1)}%`
})

async function load() {
  const runId = Number(route.params.runId)
  if (!Number.isFinite(runId)) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    run.value = await getEvalRun(runId)
    cases.value = await listEvalRunCases(runId, statusFilter.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载评测运行失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.runId, load)
watch(statusFilter, load)
</script>

<template>
  <div class="page-eval-run-detail">
    <header class="page-header">
      <button type="button" class="link" @click="router.back()">← 返回</button>
      <h1 v-if="run">运行 #{{ run.id }} · {{ run.suiteKey }}</h1>
      <p v-if="run" class="muted">
        状态 {{ run.status }} · 通过 {{ run.passedCount }} · 失败 {{ run.failedCount }} · 通过率 {{ passRate }}
      </p>
    </header>

    <section v-if="errorMessage" class="error-banner">{{ errorMessage }}</section>

    <section class="filters">
      <label>
        Case 状态：
        <select v-model="statusFilter">
          <option value="">全部</option>
          <option value="passed">passed</option>
          <option value="failed">failed</option>
          <option value="error">error</option>
          <option value="skipped">skipped</option>
        </select>
      </label>
    </section>

    <section class="card">
      <h2>Case 结果（{{ cases.length }}）</h2>
      <p v-if="loading" class="muted">加载中...</p>
      <table class="data-table">
        <thead>
          <tr><th>caseKey</th><th>状态</th><th>得分</th><th>耗时(ms)</th><th>错误</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in cases" :key="row.id">
            <td>{{ row.caseKey || row.caseId }}</td>
            <td><span class="chip" :class="`chip--${row.status}`">{{ row.status }}</span></td>
            <td>{{ row.score ?? '-' }}</td>
            <td>{{ row.latencyMs ?? '-' }}</td>
            <td class="muted">{{ row.errorMessage || '-' }}</td>
          </tr>
          <tr v-if="!cases.length"><td colspan="5" class="muted">尚无 case 结果</td></tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.page-eval-run-detail { padding: 1.5rem; display: flex; flex-direction: column; gap: 1rem; }
.muted { color: var(--text-muted, #888); }
.link { background: none; border: none; color: var(--accent, #4f46e5); cursor: pointer; padding: 0; }
.card { background: var(--surface, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 8px; padding: 1rem; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { text-align: left; padding: 0.5rem; border-bottom: 1px solid var(--border, #e5e7eb); }
.chip { padding: 2px 8px; border-radius: 999px; font-size: 0.75rem; }
.chip--passed { background: #d1fae5; color: #065f46; }
.chip--failed { background: #fee2e2; color: #991b1b; }
.chip--error { background: #fef3c7; color: #92400e; }
.chip--skipped { background: #e5e7eb; color: #374151; }
.error-banner { background: #fee2e2; color: #991b1b; padding: 0.75rem 1rem; border-radius: 6px; }
.filters { display: flex; gap: 1rem; }
</style>
