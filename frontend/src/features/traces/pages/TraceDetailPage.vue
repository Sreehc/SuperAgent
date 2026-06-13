<template>
  <section class="trace-debugger workspace-page">
    <header class="workspace-strip">
      <div class="workspace-title">
        <p class="section-label">Run debugger</p>
        <h1>Trace #{{ traceStore.selectedTrace?.exchangeId ?? route.params.exchangeId }}</h1>
        <p v-if="traceStore.selectedTrace">
          {{ traceStore.selectedTrace.executionMode }} / {{ traceStore.selectedTrace.status }} / {{ formatDuration(traceStore.selectedTrace.durationMs) }}
        </p>
      </div>
      <div v-if="traceStore.selectedTrace" class="meta-row">
        <span class="badge" :class="statusClass(traceStore.selectedTrace.status)">{{ traceStore.selectedTrace.status }}</span>
        <span class="metric-chip">stages {{ traceStore.selectedTrace.stages.length }}</span>
        <span class="metric-chip">retrievals {{ traceStore.selectedTrace.retrievals.length }}</span>
        <span class="metric-chip">models {{ traceStore.selectedTrace.modelCalls.length }}</span>
        <span v-if="traceStore.selectedTrace.agentRunId" class="metric-chip">run #{{ traceStore.selectedTrace.agentRunId }}</span>
      </div>
    </header>

    <p v-if="traceStore.errorMessage || agentErrorMessage" class="error-banner">{{ traceStore.errorMessage || agentErrorMessage }}</p>
    <LoadingSpinner v-if="traceStore.loadingDetail" text="正在加载 Trace 详情..." />

    <template v-else-if="traceStore.selectedTrace">
      <section class="trace-tabs">
        <button v-for="tab in visibleTabs" :key="tab.id" class="tab-button" :class="{ 'tab-button--active': activeTab === tab.id }" type="button" @click="activeTab = tab.id">
          {{ tab.label }}
        </button>
      </section>

      <section v-if="activeTab === 'exchange'" class="trace-layout">
        <aside class="stage-rail">
          <div class="section-heading">
            <h2>阶段时间线</h2>
            <span class="metric-chip">{{ traceStore.selectedTrace.stages.length }}</span>
          </div>
          <button
            v-for="stage in traceStore.selectedTrace.stages"
            :key="stage.stageId"
            class="stage-node"
            :class="{ 'stage-node--active': selectedStageId === stage.stageId, 'stage-node--failed': stage.status === 'failed' }"
            type="button"
            @click="selectedStageId = stage.stageId"
          >
            <span></span>
            <div>
              <strong>{{ stage.stageCode }}</strong>
              <small>{{ stageStatusLabel(stage.status) }} / {{ formatDuration(stage.durationMs) }}</small>
            </div>
          </button>
        </aside>

        <main class="trace-main">
          <article class="trace-section">
            <div class="section-heading">
              <h2>详情检查器</h2>
              <span v-if="selectedStage" class="metric-chip">{{ selectedStage.stageCode }}</span>
            </div>
            <div v-if="selectedStage" class="detail-block" :class="{ 'detail-block--failed': selectedStage.status === 'failed' }">
              <div class="item-head">
                <strong>{{ selectedStage.stageCode }}</strong>
                <span class="badge" :class="statusClass(selectedStage.status)">{{ stageStatusLabel(selectedStage.status) }}</span>
              </div>
              <p>开始：{{ formatTime(selectedStage.startedAt) }} / 耗时：{{ formatDuration(selectedStage.durationMs) }}</p>
              <p>输入摘要：{{ selectedStage.inputSummary || '-' }}</p>
              <p>输出摘要：{{ selectedStage.outputSummary || '-' }}</p>
              <p v-if="selectedStage.errorMessage">错误：{{ selectedStage.errorMessage }}</p>
            </div>
          </article>

          <article class="trace-section">
            <div class="section-heading">
              <h2>关联模型调用</h2>
              <span class="metric-chip">{{ selectedModelCalls.length }}</span>
            </div>
            <div v-if="selectedModelCalls.length === 0" class="empty-line">此阶段暂无模型调用。</div>
            <div v-for="call in selectedModelCalls" :key="call.id" class="detail-block">
              <div class="item-head"><strong>{{ call.provider }} / {{ call.model }}</strong><span class="badge" :class="statusClass(call.status)">{{ callStatusLabel(call.status) }}</span></div>
              <p>类型：{{ call.callType }} / 延迟：{{ call.latencyMs ?? 0 }}ms / tokens {{ call.inputTokens ?? '-' }} + {{ call.outputTokens ?? '-' }}</p>
              <p>Prompt 摘要：{{ call.promptSummary || '-' }}</p>
              <p>输出摘要：{{ call.outputSummary || '-' }}</p>
              <p v-if="call.errorMessage">错误：{{ call.errorMessage }}</p>
            </div>
          </article>

          <article class="trace-section">
            <div class="section-heading">
              <h2>关联检索结果</h2>
              <span class="metric-chip">{{ selectedRetrievals.length }}</span>
            </div>
            <div v-if="selectedRetrievals.length === 0" class="empty-line">此阶段暂无检索记录。</div>
            <div v-for="retrieval in selectedRetrievals" :key="retrieval.id" class="detail-block">
              <strong>#{{ retrieval.subQuestionNo }} / {{ retrieval.channel }}</strong>
              <p>查询：{{ retrieval.queryText }}</p>
              <p>结果数：{{ retrieval.resultCount }} / 选中：{{ retrieval.selectedCount }} / 延迟：{{ retrieval.latencyMs ?? '-' }}ms</p>
              <ul class="retrieval-items">
                <li v-for="item in retrieval.items.slice(0, 5)" :key="item.id">
                  chunk {{ item.chunkId }} / rank {{ item.rankNo }} / score {{ item.fusedScore ?? item.rawScore ?? '-' }} / {{ item.selected ? 'selected' : 'skipped' }}
                </li>
              </ul>
            </div>
          </article>

          <article class="trace-section">
            <h2>重排序 / 错误</h2>
            <div v-if="traceStore.selectedTrace.reranks.length === 0" class="empty-line">暂无重排序记录。</div>
            <div v-for="rerank in traceStore.selectedTrace.reranks" :key="rerank.id" class="detail-block">
              <div class="item-head"><strong>{{ rerank.provider || 'rerank' }} / {{ rerank.model || '-' }}</strong><span class="badge" :class="statusClass(rerank.status)">{{ rerank.status }}</span></div>
              <p>enabled={{ rerank.enabled }} / input={{ rerank.inputCount }} / output={{ rerank.outputCount }}</p>
              <p v-if="rerank.skippedReason">跳过原因：{{ rerank.skippedReason }}</p>
              <p v-if="rerank.errorMessage">错误：{{ rerank.errorMessage }}</p>
            </div>
          </article>
        </main>
      </section>

      <section v-else-if="activeTab === 'agent'" class="trace-section">
        <LoadingSpinner v-if="loadingAgentDetail" text="正在加载智能体运行详情..." />
        <div v-else-if="!agentRunDetail" class="empty-line">当前 Trace 未绑定智能体运行。</div>
        <template v-else>
          <article class="summary-card">
            <strong>run #{{ agentRunDetail.summary.runId }}</strong>
            <p>route reason：{{ agentRunDetail.summary.routeReason || '-' }}</p>
            <p>memory：{{ agentRunDetail.summary.memoryStrategy }} / steps {{ agentRunDetail.summary.modelStepCount }} / tools {{ agentRunDetail.summary.toolCallCount }}</p>
            <p v-if="agentRunDetail.summary.errorMessage">错误：{{ agentRunDetail.summary.errorMessage }}</p>
          </article>
          <article v-for="step in agentRunDetail.steps" :key="step.id" class="detail-block">
            <div class="item-head"><strong>#{{ step.stepNo }} {{ step.phase }}</strong><span class="badge" :class="statusClass(step.status)">{{ stepStatusLabel(step.status) }}</span></div>
            <p>决策：{{ step.decisionSummary || '-' }}</p>
            <p>观察：{{ step.observationSummary || '-' }}</p>
            <p v-if="step.selectedToolId">工具：{{ step.selectedToolId }} / {{ step.selectedToolReason || '-' }}</p>
            <p v-if="step.errorMessage">错误：{{ step.errorMessage }}</p>
          </article>
        </template>
      </section>

      <section v-else-if="activeTab === 'tools'" class="trace-section">
        <div v-if="!agentRunDetail" class="empty-line">当前 Trace 未绑定智能体运行。</div>
        <article v-for="call in agentRunDetail?.toolCalls ?? []" :key="call.id" class="detail-block">
          <div class="item-head"><strong>{{ call.toolId }}</strong><span class="badge" :class="statusClass(call.status)">{{ call.status }}</span></div>
          <p>plugin：{{ call.pluginVersion || '-' }} / latency {{ call.latencyMs ?? '-' }}ms</p>
          <p>请求：{{ call.requestSummary || '-' }}</p>
          <p>响应：{{ call.responseSummary || '-' }}</p>
          <p v-if="call.errorMessage">错误：{{ call.errorMessage }}</p>
          <div class="evidence-grid">
            <div v-if="searchEvidence(call.metadata).length">
              <strong>检索证据</strong>
              <ul class="retrieval-items"><li v-for="item in searchEvidence(call.metadata).slice(0, 3)" :key="item.title || item.url">{{ item.title || item.url }} / {{ item.url || item.summary || '-' }}</li></ul>
            </div>
            <div v-if="graphEvidence(call.metadata).length">
              <strong>图谱证据</strong>
              <ul class="retrieval-items"><li v-for="item in graphEvidence(call.metadata).slice(0, 3)" :key="item.entity || item.path || JSON.stringify(item)">{{ item.entity || item.path || JSON.stringify(item) }}</li></ul>
            </div>
            <div v-if="sandboxExecution(call.metadata)">
              <strong>沙箱执行</strong>
              <pre class="metadata">{{ JSON.stringify(sandboxExecution(call.metadata), null, 2) }}</pre>
            </div>
          </div>
        </article>
        <div v-if="agentRunDetail && agentRunDetail.toolCalls.length === 0" class="empty-line">暂无工具调用。</div>
      </section>

      <section v-else-if="activeTab === 'checkpoints'" class="trace-section">
        <div v-if="!agentRunDetail" class="empty-line">当前 Trace 未绑定智能体运行。</div>
        <article v-for="checkpoint in agentRunDetail?.checkpoints ?? []" :key="checkpoint.id" class="detail-block">
          <div class="item-head"><strong>#{{ checkpoint.checkpointNo }} {{ checkpoint.checkpointType }}</strong><span class="badge">{{ checkpoint.stable ? '稳定' : '待定' }}</span></div>
          <p>step #{{ checkpoint.stepId ?? '-' }} / {{ formatTime(checkpoint.createdAt) }}</p>
          <pre class="metadata">{{ JSON.stringify(checkpoint.payload, null, 2) }}</pre>
        </article>
        <div v-if="agentRunDetail && agentRunDetail.checkpoints.length === 0" class="empty-line">暂无检查点。</div>
      </section>

      <section v-else class="trace-section">
        <div v-if="resumeSteps.length === 0" class="empty-line">暂无恢复链路记录。</div>
        <article v-for="step in resumeSteps" :key="step.id" class="detail-block">
          <strong>#{{ step.stepNo }} {{ step.phase }}</strong>
          <p>{{ step.decisionSummary || '-' }}</p>
          <p>{{ formatTime(step.startedAt) }}</p>
        </article>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { LoadingSpinner } from '../../../components'
import { getAgentRunDetail } from '../../agent/api'
import type { AgentRunDetail } from '../../agent/types'
import { useTraceStore } from '../store/traces'

const route = useRoute()
const traceStore = useTraceStore()
const agentRunDetail = ref<AgentRunDetail | null>(null)
const loadingAgentDetail = ref(false)
const agentErrorMessage = ref('')
const activeTab = ref<'exchange' | 'agent' | 'tools' | 'checkpoints' | 'resume'>('exchange')
const selectedStageId = ref<number | null>(null)

const visibleTabs = computed(() => {
  const items: Array<{ id: 'exchange' | 'agent' | 'tools' | 'checkpoints' | 'resume'; label: string }> = [{ id: 'exchange', label: 'Exchange追踪' }]
  if (traceStore.selectedTrace?.agentRunId) {
    items.push(
      { id: 'agent', label: '智能体运行' },
      { id: 'tools', label: '工具调用' },
      { id: 'checkpoints', label: '检查点' },
      { id: 'resume', label: '恢复链路' },
    )
  }
  return items
})

const selectedStage = computed(() =>
  traceStore.selectedTrace?.stages.find((stage) => stage.stageId === selectedStageId.value) ?? traceStore.selectedTrace?.stages[0] ?? null,
)

const selectedModelCalls = computed(() =>
  (traceStore.selectedTrace?.modelCalls ?? []).filter((call) => !selectedStage.value || call.stageId === selectedStage.value.stageId),
)

const selectedRetrievals = computed(() =>
  (traceStore.selectedTrace?.retrievals ?? []).filter((retrieval) => !selectedStage.value || retrieval.stageId === selectedStage.value.stageId),
)

const resumeSteps = computed(() =>
  (agentRunDetail.value?.steps ?? []).filter((step) => (step.decisionSummary || '').toLowerCase().includes('resume')),
)

onMounted(async () => {
  await loadTrace()
})

watch(
  () => route.params.exchangeId,
  async () => {
    await loadTrace()
  },
)

async function loadTrace() {
  const exchangeId = Number(route.params.exchangeId)
  if (!Number.isInteger(exchangeId) || exchangeId < 1) {
    return
  }
  await traceStore.selectTrace(exchangeId)
  selectedStageId.value = traceStore.selectedTrace?.stages[0]?.stageId ?? null
  activeTab.value = 'exchange'
  await loadAgentRun()
}

async function loadAgentRun() {
  agentRunDetail.value = null
  agentErrorMessage.value = ''
  const runId = traceStore.selectedTrace?.agentRunId
  if (!runId) {
    return
  }
  loadingAgentDetail.value = true
  try {
    const response = await getAgentRunDetail(runId)
    agentRunDetail.value = response.data
  } catch {
    agentErrorMessage.value = 'Agent Run 详情加载失败，请稍后重试。'
  } finally {
    loadingAgentDetail.value = false
  }
}

function searchEvidence(metadata: Record<string, unknown>) {
  const output = asRecord(metadata.output)
  const results = output.results
  if (Array.isArray(results)) {
    return results as Array<Record<string, string>>
  }
  return []
}

function graphEvidence(metadata: Record<string, unknown>) {
  const output = asRecord(metadata.output)
  const evidence = output.evidence
  if (Array.isArray(evidence)) {
    return evidence as Array<Record<string, string>>
  }
  return []
}

function sandboxExecution(metadata: Record<string, unknown>) {
  const output = asRecord(metadata.output)
  return Object.keys(output).length === 0 ? null : output
}

function asRecord(value: unknown) {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
}

function formatDuration(durationMs: number) {
  if (durationMs < 1000) {
    return `${durationMs}ms`
  }
  return `${(durationMs / 1000).toFixed(1)}s`
}

function formatTime(value: string | null) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function stageStatusLabel(status: string) {
  const map: Record<string, string> = { success: '成功', failed: '失败', running: '运行中', pending: '待处理' }
  return map[status] ?? status
}

function callStatusLabel(status: string) {
  const map: Record<string, string> = { success: '成功', failed: '失败' }
  return map[status] ?? status
}

function stepStatusLabel(status: string) {
  const map: Record<string, string> = { success: '成功', failed: '失败', running: '运行中', pending: '待处理' }
  return map[status] ?? status
}

function statusClass(status: string | null | undefined) {
  if (status === 'success' || status === 'completed') {
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
.trace-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--bg-surface);
}

.tab-button {
  color: var(--text-muted);
  background: var(--bg-subtle);
  border-color: var(--line-soft);
}

.tab-button--active {
  color: var(--accent);
  border-color: color-mix(in srgb, var(--accent), transparent 58%);
  background: var(--accent-soft);
}

.trace-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 12px;
}

.stage-rail,
.trace-section {
  display: grid;
  align-content: start;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--bg-surface);
}

.stage-rail {
  position: sticky;
  top: calc(var(--utility-height) + 14px);
  max-height: calc(100vh - var(--utility-height) - 28px);
  overflow: auto;
}

.stage-rail h2,
.trace-section h2,
.section-heading h2 {
  margin: 0;
  font-size: 16px;
}

.stage-node {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background: transparent;
  color: var(--text-main);
  text-align: left;
}

.stage-node > span {
  width: 9px;
  height: 9px;
  margin-top: 5px;
  border-radius: 999px;
  background: var(--accent);
  box-shadow: 0 0 0 5px var(--accent-soft);
}

.stage-node--failed > span {
  background: var(--danger);
  box-shadow: 0 0 0 5px var(--danger-soft);
}

.stage-node:hover,
.stage-node--active {
  border-color: color-mix(in srgb, var(--accent), transparent 58%);
  background: var(--accent-soft);
}

.stage-node strong,
.stage-node small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-node small {
  margin-top: 4px;
  color: var(--text-muted);
}

.trace-main {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.section-heading,
.item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.detail-block,
.summary-card {
  display: grid;
  gap: 6px;
  padding: 10px 0;
  border-top: 1px solid var(--line-soft);
}

.detail-block:first-of-type,
.summary-card:first-of-type {
  border-top: 0;
}

.detail-block--failed {
  border-left: 3px solid var(--danger);
  padding-left: 10px;
}

.detail-block p,
.summary-card p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.retrieval-items {
  margin: 4px 0 0;
  padding-left: 18px;
  color: var(--text-muted);
}

.evidence-grid {
  display: grid;
  gap: 10px;
}

@media (max-width: 960px) {
  .trace-layout {
    grid-template-columns: 1fr;
  }

  .stage-rail {
    position: static;
    max-height: none;
  }
}
</style>
