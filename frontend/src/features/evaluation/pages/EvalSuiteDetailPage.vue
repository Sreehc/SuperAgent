<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getEvalSuite, listEvalRuns } from '../api'
import type { EvalRun, EvalSuiteDetail } from '../types'

const route = useRoute()
const router = useRouter()

const detail = ref<EvalSuiteDetail | null>(null)
const runs = ref<EvalRun[]>([])
const loading = ref(false)
const errorMessage = ref('')

async function load() {
  const suiteId = Number(route.params.suiteId)
  if (!Number.isFinite(suiteId)) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    detail.value = await getEvalSuite(suiteId)
    const runResp = await listEvalRuns({ suiteId, pageSize: 50 })
    runs.value = runResp.items
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载评测集失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.suiteId, load)
</script>

<template>
  <div class="page-eval-suite-detail">
    <header class="page-header">
      <button type="button" class="link" @click="router.push('/evals')">← 返回评测集列表</button>
      <h1 v-if="detail">{{ detail.suite.name }}</h1>
      <p v-if="detail" class="muted">{{ detail.suite.suiteKey }} · {{ detail.suite.caseCount }} cases · {{ detail.suite.runCount }} runs</p>
    </header>

    <section v-if="errorMessage" class="error-banner">{{ errorMessage }}</section>
    <section v-if="loading" class="muted">加载中...</section>

    <section v-if="detail" class="card">
      <h2>用例</h2>
      <table class="data-table">
        <thead><tr><th>caseKey</th><th>更新时间</th></tr></thead>
        <tbody>
          <tr v-for="evalCase in detail.cases" :key="evalCase.id">
            <td>{{ evalCase.caseKey }}</td>
            <td>{{ evalCase.updatedAt }}</td>
          </tr>
          <tr v-if="!detail.cases.length"><td colspan="2" class="muted">尚无用例</td></tr>
        </tbody>
      </table>
    </section>

    <section v-if="detail" class="card">
      <h2>运行历史</h2>
      <table class="data-table">
        <thead><tr><th>ID</th><th>状态</th><th>通过</th><th>失败</th><th>开始</th><th></th></tr></thead>
        <tbody>
          <tr v-for="run in runs" :key="run.id">
            <td>#{{ run.id }}</td>
            <td>{{ run.status }}</td>
            <td>{{ run.passedCount }}</td>
            <td>{{ run.failedCount }}</td>
            <td>{{ run.createdAt }}</td>
            <td><button type="button" class="link" @click="router.push(`/evals/runs/${run.id}`)">查看 cases →</button></td>
          </tr>
          <tr v-if="!runs.length"><td colspan="6" class="muted">尚无运行</td></tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.page-eval-suite-detail { padding: 1.5rem; display: flex; flex-direction: column; gap: 1rem; }
.muted { color: var(--text-muted, #888); }
.link { background: none; border: none; color: var(--accent, #4f46e5); cursor: pointer; padding: 0; }
.card { background: var(--surface, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 8px; padding: 1rem; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { text-align: left; padding: 0.5rem; border-bottom: 1px solid var(--border, #e5e7eb); }
.error-banner { background: #fee2e2; color: #991b1b; padding: 0.75rem 1rem; border-radius: 6px; }
</style>
