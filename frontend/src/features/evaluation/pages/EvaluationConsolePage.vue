<template>
  <section class="evaluation-page workspace-page">
    <header class="workspace-strip">
      <div class="workspace-title">
        <p class="section-label">Evaluation</p>
        <h1>评测中心</h1>
        <p>管理评测集、用例和运行历史，把脚本产物与用户反馈沉淀到可查询的质量记录。</p>
      </div>
      <div class="meta-row">
        <span class="metric-chip">suites {{ total }}</span>
        <span class="metric-chip">runs {{ runsTotal }}</span>
      </div>
    </header>

    <section class="eval-layout">
      <aside class="eval-panel">
        <div class="filter-row">
          <label class="field eval-search">
            <span>搜索评测集</span>
            <input v-model="keyword" type="search" placeholder="suite key / name" @keyup.enter="fetchSuites" />
          </label>
          <button class="btn btn-secondary" type="button" @click="fetchSuites">刷新</button>
        </div>

        <form class="eval-form" @submit.prevent="createSuite">
          <label class="field">
            <span>Suite Key</span>
            <input v-model="suiteForm.suiteKey" required placeholder="agent-productization" />
          </label>
          <label class="field">
            <span>名称</span>
            <input v-model="suiteForm.name" required placeholder="Agent 产品化回归" />
          </label>
          <label class="field">
            <span>描述</span>
            <textarea v-model="suiteForm.description" rows="2" placeholder="覆盖范围和发布门禁" />
          </label>
          <button class="btn btn-primary" type="submit" :disabled="savingSuite">创建评测集</button>
        </form>

        <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>
        <LoadingSpinner v-if="loadingSuites" text="正在加载评测集..." />
        <EmptyState v-else-if="suites.length === 0" variant="search" title="暂无评测集" description="创建评测集后添加用例和运行记录。" />
        <div v-else class="eval-suite-list">
          <button
            v-for="suite in suites"
            :key="suite.id"
            class="eval-suite-row"
            :class="{ 'eval-suite-row--active': selectedSuite?.suite.id === suite.id }"
            type="button"
            @click="selectSuite(suite.id)"
          >
            <strong>{{ suite.name }}</strong>
            <span>{{ suite.suiteKey }}</span>
            <small>{{ suite.caseCount }} cases / {{ suite.runCount }} runs</small>
          </button>
        </div>
      </aside>

      <main class="eval-panel eval-detail">
        <EmptyState v-if="!selectedSuite && !loadingDetail" variant="search" title="选择评测集" description="查看用例、运行历史并录入评测报告。" />
        <LoadingSpinner v-else-if="loadingDetail" text="正在加载评测详情..." />
        <template v-else-if="selectedSuite">
          <header class="eval-detail__header">
            <div>
              <p class="section-label">Suite</p>
              <h2>{{ selectedSuite.suite.name }}</h2>
              <p>{{ selectedSuite.suite.description || '暂无描述' }}</p>
            </div>
            <div class="meta-row">
              <span class="metric-chip">{{ selectedSuite.suite.suiteKey }}</span>
              <span class="metric-chip">{{ selectedSuite.cases.length }} cases</span>
            </div>
          </header>

          <section class="eval-grid">
            <article class="eval-card">
              <h3>新增用例</h3>
              <form class="eval-form" @submit.prevent="createCase">
                <label class="field">
                  <span>Case Key</span>
                  <input v-model="caseForm.caseKey" required placeholder="tool-trace" />
                </label>
                <label class="field">
                  <span>输入 JSON</span>
                  <textarea v-model="caseForm.inputJson" rows="4" />
                </label>
                <label class="field">
                  <span>期望 JSON</span>
                  <textarea v-model="caseForm.expectedJson" rows="4" />
                </label>
                <button class="btn btn-secondary" type="submit" :disabled="savingCase">添加用例</button>
              </form>
            </article>

            <article class="eval-card">
              <h3>录入运行报告</h3>
              <form class="eval-form" @submit.prevent="createRun">
                <label class="field">
                  <span>状态</span>
                  <select v-model="runForm.status">
                    <option value="">根据报告推导</option>
                    <option value="pending">pending</option>
                    <option value="running">running</option>
                    <option value="success">success</option>
                    <option value="failed">failed</option>
                  </select>
                </label>
                <label class="field">
                  <span>报告 JSON</span>
                  <textarea v-model="runForm.reportJson" rows="8" />
                </label>
                <button class="btn btn-secondary" type="submit" :disabled="savingRun">录入运行</button>
              </form>
            </article>
          </section>

          <section class="eval-section">
            <h3>用例</h3>
            <div v-if="selectedSuite.cases.length === 0" class="empty-line">暂无用例。</div>
            <table v-else class="data-table">
              <thead>
                <tr>
                  <th>Case</th>
                  <th>输入</th>
                  <th>期望</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in selectedSuite.cases" :key="item.id">
                  <td><strong>{{ item.caseKey }}</strong></td>
                  <td><pre class="eval-json">{{ formatJson(item.input) }}</pre></td>
                  <td><pre class="eval-json">{{ formatJson(item.expected) }}</pre></td>
                  <td><button class="btn btn-ghost btn-sm danger-text" type="button" @click="removeCase(item.id)">删除</button></td>
                </tr>
              </tbody>
            </table>
          </section>

          <section class="eval-section">
            <h3>最近运行</h3>
            <div v-if="selectedSuite.recentRuns.length === 0" class="empty-line">暂无运行记录。</div>
            <table v-else class="data-table">
              <thead>
                <tr>
                  <th>Run</th>
                  <th>状态</th>
                  <th>结果</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="run in selectedSuite.recentRuns" :key="run.id">
                  <td class="mono">#{{ run.id }}</td>
                  <td><span class="badge" :class="statusClass(run.status)">{{ run.status }}</span></td>
                  <td>{{ run.passedCount }} passed / {{ run.failedCount }} failed</td>
                  <td>{{ formatTime(run.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </section>
        </template>
      </main>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { EmptyState, LoadingSpinner } from '../../../components'
import { createEvalCase, createEvalRun, createEvalSuite, deleteEvalCase, getEvalSuite, listEvalRuns, listEvalSuites } from '../api'
import type { EvalRunStatus, EvalSuite, EvalSuiteDetail } from '../types'

const suites = ref<EvalSuite[]>([])
const selectedSuite = ref<EvalSuiteDetail | null>(null)
const keyword = ref('')
const total = ref(0)
const runsTotal = ref(0)
const loadingSuites = ref(false)
const loadingDetail = ref(false)
const savingSuite = ref(false)
const savingCase = ref(false)
const savingRun = ref(false)
const errorMessage = ref('')

const suiteForm = ref({ suiteKey: '', name: '', description: '' })
const caseForm = ref({
  caseKey: '',
  inputJson: '{\n  "command": ""\n}',
  expectedJson: '{\n  "tags": []\n}',
})
const runForm = ref<{ status: EvalRunStatus | ''; reportJson: string }>({
  status: '',
  reportJson: '{\n  "passed": true,\n  "cases": []\n}',
})

onMounted(async () => {
  await Promise.all([fetchSuites(), fetchRunsTotal()])
})

async function fetchSuites() {
  loadingSuites.value = true
  errorMessage.value = ''
  try {
    const response = await listEvalSuites({ page: 1, pageSize: 50, keyword: keyword.value || undefined })
    suites.value = response.data.items
    total.value = response.data.total
    if (!selectedSuite.value && suites.value.length > 0) {
      await selectSuite(suites.value[0].id)
    }
  } catch {
    errorMessage.value = '评测集加载失败。'
  } finally {
    loadingSuites.value = false
  }
}

async function fetchRunsTotal() {
  const response = await listEvalRuns({ page: 1, pageSize: 1 })
  runsTotal.value = response.data.total
}

async function selectSuite(suiteId: number) {
  loadingDetail.value = true
  errorMessage.value = ''
  try {
    const response = await getEvalSuite(suiteId)
    selectedSuite.value = response.data
  } catch {
    errorMessage.value = '评测详情加载失败。'
  } finally {
    loadingDetail.value = false
  }
}

async function createSuite() {
  savingSuite.value = true
  errorMessage.value = ''
  try {
    const response = await createEvalSuite(suiteForm.value)
    suiteForm.value = { suiteKey: '', name: '', description: '' }
    await fetchSuites()
    await selectSuite(response.data.data.id)
  } catch {
    errorMessage.value = '创建评测集失败，请检查 suite key 是否重复。'
  } finally {
    savingSuite.value = false
  }
}

async function createCase() {
  if (!selectedSuite.value) {
    return
  }
  savingCase.value = true
  errorMessage.value = ''
  try {
    await createEvalCase(selectedSuite.value.suite.id, {
      caseKey: caseForm.value.caseKey,
      input: parseJson(caseForm.value.inputJson),
      expected: parseJson(caseForm.value.expectedJson),
    })
    caseForm.value.caseKey = ''
    await selectSuite(selectedSuite.value.suite.id)
    await fetchSuites()
  } catch {
    errorMessage.value = '添加用例失败，请确认 JSON 格式正确且 case key 不重复。'
  } finally {
    savingCase.value = false
  }
}

async function createRun() {
  if (!selectedSuite.value) {
    return
  }
  savingRun.value = true
  errorMessage.value = ''
  try {
    await createEvalRun(selectedSuite.value.suite.id, {
      status: runForm.value.status,
      report: parseJson(runForm.value.reportJson),
    })
    await selectSuite(selectedSuite.value.suite.id)
    await Promise.all([fetchSuites(), fetchRunsTotal()])
  } catch {
    errorMessage.value = '录入运行失败，请确认报告 JSON 格式正确。'
  } finally {
    savingRun.value = false
  }
}

async function removeCase(caseId: number) {
  if (!selectedSuite.value || !window.confirm('确认删除这个评测用例吗？')) {
    return
  }
  await deleteEvalCase(caseId)
  await selectSuite(selectedSuite.value.suite.id)
  await fetchSuites()
}

function parseJson(value: string) {
  return JSON.parse(value || '{}') as Record<string, unknown>
}

function formatJson(value: Record<string, unknown>) {
  return JSON.stringify(value, null, 2)
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function statusClass(status: EvalRunStatus) {
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
.eval-layout {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 12px;
  min-height: 0;
}

.eval-panel {
  display: grid;
  align-content: start;
  gap: 12px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--bg-surface);
}

.eval-search {
  width: min(260px, 100%);
}

.eval-form {
  display: grid;
  gap: 10px;
}

.eval-suite-list {
  display: grid;
  gap: 7px;
}

.eval-suite-row {
  display: grid;
  gap: 5px;
  width: 100%;
  padding: 10px;
  color: var(--text-main);
  text-align: left;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background: var(--bg-inset);
}

.eval-suite-row span,
.eval-suite-row small {
  color: var(--text-muted);
}

.eval-suite-row--active {
  border-color: color-mix(in srgb, var(--accent), transparent 35%);
  background: var(--accent-soft);
}

.eval-detail__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.eval-detail__header h2 {
  margin: 0;
}

.eval-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.eval-card,
.eval-section {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background: var(--bg-inset);
}

.eval-card h3,
.eval-section h3 {
  margin: 0;
}

.eval-json {
  max-width: 360px;
  max-height: 160px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
}

@media (max-width: 1180px) {
  .eval-layout,
  .eval-grid {
    grid-template-columns: 1fr;
  }
}
</style>
