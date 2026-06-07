<template>
  <section class="trace-detail-page page-stack">
    <header class="page-header">
      <div>
        <p class="page-kicker">/traces/{{ route.params.exchangeId }}</p>
        <h2>Trace #{{ traceStore.selectedTrace?.exchangeId ?? route.params.exchangeId }}</h2>
        <p v-if="traceStore.selectedTrace">
          {{ traceStore.selectedTrace.executionMode }} / {{ traceStore.selectedTrace.status }} / {{ formatDuration(traceStore.selectedTrace.durationMs) }}
        </p>
      </div>
      <div v-if="traceStore.selectedTrace?.agentRunId" class="meta-row">
        <span class="badge">Agent run #{{ traceStore.selectedTrace.agentRunId }}</span>
        <span class="badge" :class="statusClass(traceStore.selectedTrace.agentRunStatus)">{{ agentRunStatusLabel(traceStore.selectedTrace.agentRunStatus) }}</span>
      </div>
    </header>

    <p v-if="traceStore.errorMessage || agentErrorMessage" class="error-banner">{{ traceStore.errorMessage || agentErrorMessage }}</p>
    <LoadingSpinner v-if="traceStore.loadingDetail" text="正在加载 Trace 详情..." />

    <template v-else-if="traceStore.selectedTrace">
      <section class="tabs panel">
        <button v-for="tab in visibleTabs" :key="tab.id" class="tab-button" :class="{ 'tab-button--active': activeTab === tab.id }" type="button" @click="activeTab = tab.id">
          {{ tab.label }}
        </button>
      </section>

      <section v-if="activeTab === 'exchange'" class="trace-layout">
        <aside class="panel timeline-panel">
          <h3>阶段时间线</h3>
          <article v-for="stage in traceStore.selectedTrace.stages" :key="stage.stageId" class="timeline-item" :class="{ 'timeline-item--failed': stage.status === 'failed' }">
            <div>
              <strong>{{ stage.stageCode }}</strong>
              <p>{{ stageStatusLabel(stage.status) }}</p>
            </div>
            <span>{{ formatDuration(stage.durationMs) }}</span>
          </article>
        </aside>

        <section class="detail-panel">
          <article class="panel">
            <h3>阶段详情</h3>
            <div v-for="stage in traceStore.selectedTrace.stages" :key="`detail-${stage.stageId}`" class="detail-block" :class="{ 'detail-block--failed': stage.status === 'failed' }">
              <strong>{{ stage.stageCode }}</strong>
              <p>输入摘要：{{ stage.inputSummary || '-' }}</p>
              <p>输出摘要：{{ stage.outputSummary || '-' }}</p>
              <p v-if="stage.errorMessage">错误：{{ stage.errorMessage }}</p>
            </div>
          </article>

          <article class="panel">
            <h3>模型调用</h3>
            <div v-if="traceStore.selectedTrace.modelCalls.length === 0" class="empty-line">暂无模型调用。</div>
            <div v-for="call in traceStore.selectedTrace.modelCalls" :key="call.id" class="detail-block">
              <div class="item-head"><strong>{{ call.provider }} / {{ call.model }}</strong><span class="badge" :class="statusClass(call.status)">{{ callStatusLabel(call.status) }}</span></div>
              <p>类型：{{ call.callType }} / 延迟：{{ call.latencyMs ?? 0 }}ms</p>
              <p>Prompt 摘要：{{ call.promptSummary || '-' }}</p>
              <p>输出摘要：{{ call.outputSummary || '-' }}</p>
            </div>
          </article>

          <article class="panel">
            <h3>检索结果</h3>
            <div v-if="traceStore.selectedTrace.retrievals.length === 0" class="empty-line">暂无检索记录。</div>
            <div v-for="retrieval in traceStore.selectedTrace.retrievals" :key="retrieval.id" class="detail-block">
              <strong>#{{ retrieval.subQuestionNo }} / {{ retrieval.channel }}</strong>
              <p>查询：{{ retrieval.queryText }}</p>
              <p>结果数：{{ retrieval.resultCount }} / 选中：{{ retrieval.selectedCount }} / 延迟：{{ retrieval.latencyMs ?? '-' }}ms</p>
              <ul class="retrieval-items">
                <li v-for="item in retrieval.items.slice(0, 5)" :key="item.id">块 {{ item.chunkId }} / 排名 {{ item.rankNo }} / {{ item.selected ? '已选中' : '未选中' }}</li>
              </ul>
            </div>
          </article>

          <article class="panel">
            <h3>重排序 / 错误</h3>
            <div v-if="traceStore.selectedTrace.reranks.length === 0" class="empty-line">暂无重排序记录。</div>
            <div v-for="rerank in traceStore.selectedTrace.reranks" :key="rerank.id" class="detail-block">
              <div class="item-head"><strong>{{ rerank.provider || 'rerank' }} / {{ rerank.model || '-' }}</strong><span class="badge" :class="statusClass(rerank.status)">{{ rerank.status }}</span></div>
              <p>enabled={{ rerank.enabled }} / input={{ rerank.inputCount }} / output={{ rerank.outputCount }}</p>
              <p v-if="rerank.skippedReason">跳过原因：{{ rerank.skippedReason }}</p>
              <p v-if="rerank.errorMessage">错误：{{ rerank.errorMessage }}</p>
            </div>
          </article>
        </section>
      </section>

      <section v-else-if="activeTab === 'agent'" class="panel stack">
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

      <section v-else-if="activeTab === 'tools'" class="panel stack">
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

      <section v-else-if="activeTab === 'checkpoints'" class="panel stack">
        <div v-if="!agentRunDetail" class="empty-line">当前 Trace 未绑定智能体运行。</div>
        <article v-for="checkpoint in agentRunDetail?.checkpoints ?? []" :key="checkpoint.id" class="detail-block">
          <div class="item-head"><strong>#{{ checkpoint.checkpointNo }} {{ checkpoint.checkpointType }}</strong><span class="badge">{{ checkpoint.stable ? '稳定' : '待定' }}</span></div>
          <p>step #{{ checkpoint.stepId ?? '-' }} / {{ formatTime(checkpoint.createdAt) }}</p>
          <pre class="metadata">{{ JSON.stringify(checkpoint.payload, null, 2) }}</pre>
        </article>
        <div v-if="agentRunDetail && agentRunDetail.checkpoints.length === 0" class="empty-line">暂无检查点。</div>
      </section>

      <section v-else class="panel stack">
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

function agentRunStatusLabel(status: string | null | undefined) {
  const map: Record<string, string> = { success: '成功', failed: '失败', running: '运行中', pending: '待处理', completed: '已完成' }
  return status ? (map[status] ?? status) : '未知'
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
.meta-row,
.item-head,
.tabs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.tabs {
  padding: 10px;
}

.tab-button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text-muted);
  font-weight: 700;
}

.tab-button--active {
  color: var(--color-accent);
  border-color: color-mix(in srgb, var(--color-accent), transparent 58%);
  background: var(--color-accent-soft);
}

.trace-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 12px;
}

.timeline-panel,
.detail-panel,
.stack,
.panel {
  display: grid;
  align-content: start;
  gap: 12px;
}

.timeline-panel h3,
.detail-panel h3,
.stack h3,
.panel h3 {
  margin: 0;
}

.timeline-item,
.detail-block,
.summary-card {
  display: grid;
  gap: 5px;
  padding: 10px 0;
  border-top: 1px solid var(--color-border);
}

.timeline-item:first-of-type,
.detail-block:first-of-type {
  border-top: 0;
}

.timeline-item {
  grid-template-columns: minmax(0, 1fr) auto;
}

.timeline-item--failed,
.detail-block--failed {
  border-left: 3px solid var(--color-danger);
  padding-left: 10px;
}

.timeline-item p,
.detail-block p,
.summary-card p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.retrieval-items {
  margin: 4px 0 0;
  padding-left: 18px;
  color: var(--color-text-muted);
}

.evidence-grid {
  display: grid;
  gap: 10px;
}

@media (max-width: 960px) {
  .trace-layout {
    grid-template-columns: 1fr;
  }
}
</style>
