<template>
  <section class="trace-detail-page">
    <header class="card-shell page-header">
      <div>
        <p class="eyebrow">/traces/{{ route.params.exchangeId }}</p>
        <h2>Trace #{{ traceStore.selectedTrace?.exchangeId ?? route.params.exchangeId }}</h2>
        <p v-if="traceStore.selectedTrace">
          {{ traceStore.selectedTrace.executionMode }} · {{ traceStore.selectedTrace.status }} · {{ formatDuration(traceStore.selectedTrace.durationMs) }}
        </p>
      </div>
      <div v-if="traceStore.selectedTrace?.agentRunId" class="header-meta">
        <span class="status-chip">Agent run #{{ traceStore.selectedTrace.agentRunId }}</span>
        <span class="status-chip">{{ traceStore.selectedTrace.agentRunStatus || 'unknown' }}</span>
      </div>
    </header>

    <p v-if="traceStore.errorMessage || agentErrorMessage" class="error-banner">{{ traceStore.errorMessage || agentErrorMessage }}</p>
    <section v-if="traceStore.loadingDetail" class="card-shell">正在加载 Trace 详情...</section>

    <template v-else-if="traceStore.selectedTrace">
      <section class="card-shell tabs">
        <button
          v-for="tab in visibleTabs"
          :key="tab.id"
          class="tab-button"
          :class="{ 'tab-button--active': activeTab === tab.id }"
          type="button"
          @click="activeTab = tab.id"
        >
          {{ tab.label }}
        </button>
      </section>

      <section v-if="activeTab === 'exchange'" class="trace-layout">
        <aside class="card-shell timeline-panel">
          <h3>阶段时间线</h3>
          <div
            v-for="stage in traceStore.selectedTrace.stages"
            :key="stage.stageId"
            class="timeline-item"
            :class="{ 'timeline-item--failed': stage.status === 'failed' }"
          >
            <div>
              <strong>{{ stage.stageCode }}</strong>
              <p>{{ stage.status }}</p>
            </div>
            <span>{{ formatDuration(stage.durationMs) }}</span>
          </div>
        </aside>

        <section class="detail-panel">
          <article class="card-shell">
            <h3>阶段详情</h3>
            <div
              v-for="stage in traceStore.selectedTrace.stages"
              :key="`detail-${stage.stageId}`"
              class="detail-block"
              :class="{ 'detail-block--failed': stage.status === 'failed' }"
            >
              <strong>{{ stage.stageCode }}</strong>
              <p>输入摘要：{{ stage.inputSummary || '-' }}</p>
              <p>输出摘要：{{ stage.outputSummary || '-' }}</p>
              <p v-if="stage.errorMessage">错误：{{ stage.errorMessage }}</p>
            </div>
          </article>

          <article class="card-shell">
            <h3>模型调用</h3>
            <div v-if="traceStore.selectedTrace.modelCalls.length === 0" class="empty-line">暂无模型调用。</div>
            <div v-for="call in traceStore.selectedTrace.modelCalls" :key="call.id" class="detail-block">
              <strong>{{ call.provider }} / {{ call.model }}</strong>
              <p>状态：{{ call.status }} · 延迟：{{ call.latencyMs ?? 0 }}ms</p>
              <p>Prompt 摘要：{{ call.promptSummary || '-' }}</p>
              <p>输出摘要：{{ call.outputSummary || '-' }}</p>
            </div>
          </article>

          <article class="card-shell">
            <h3>检索结果</h3>
            <div v-if="traceStore.selectedTrace.retrievals.length === 0" class="empty-line">暂无检索记录。</div>
            <div v-for="retrieval in traceStore.selectedTrace.retrievals" :key="retrieval.id" class="detail-block">
              <strong>#{{ retrieval.subQuestionNo }} · {{ retrieval.channel }}</strong>
              <p>查询：{{ retrieval.queryText }}</p>
              <p>结果数：{{ retrieval.resultCount }} · 选中：{{ retrieval.selectedCount }}</p>
              <ul class="retrieval-items">
                <li v-for="item in retrieval.items.slice(0, 5)" :key="item.id">
                  chunk {{ item.chunkId }} · rank {{ item.rankNo }} · selected {{ item.selected ? 'yes' : 'no' }}
                </li>
              </ul>
            </div>
          </article>

          <article class="card-shell">
            <h3>Rerank / 错误</h3>
            <div v-if="traceStore.selectedTrace.reranks.length === 0" class="empty-line">暂无 Rerank 记录。</div>
            <div v-for="rerank in traceStore.selectedTrace.reranks" :key="rerank.id" class="detail-block">
              <strong>{{ rerank.status }}</strong>
              <p>enabled={{ rerank.enabled }} · input={{ rerank.inputCount }} · output={{ rerank.outputCount }}</p>
              <p v-if="rerank.skippedReason">跳过原因：{{ rerank.skippedReason }}</p>
              <p v-if="rerank.errorMessage">错误：{{ rerank.errorMessage }}</p>
            </div>
          </article>
        </section>
      </section>

      <section v-else-if="activeTab === 'agent'" class="card-shell stack">
        <div v-if="loadingAgentDetail" class="empty-line">正在加载 Agent Run...</div>
        <div v-else-if="!agentRunDetail" class="empty-line">当前 Trace 未绑定 Agent Run。</div>
        <template v-else>
          <article class="summary-card">
            <strong>run #{{ agentRunDetail.summary.runId }}</strong>
            <p>route reason：{{ agentRunDetail.summary.routeReason || '-' }}</p>
            <p>memory：{{ agentRunDetail.summary.memoryStrategy }} · steps {{ agentRunDetail.summary.modelStepCount }} · tools {{ agentRunDetail.summary.toolCallCount }}</p>
            <p v-if="agentRunDetail.summary.errorMessage">错误：{{ agentRunDetail.summary.errorMessage }}</p>
          </article>
          <article v-for="step in agentRunDetail.steps" :key="step.id" class="detail-block">
            <div class="item-head">
              <strong>#{{ step.stepNo }} {{ step.phase }}</strong>
              <span class="status-chip">{{ step.status }}</span>
            </div>
            <p>决策：{{ step.decisionSummary || '-' }}</p>
            <p>观察：{{ step.observationSummary || '-' }}</p>
            <p v-if="step.selectedToolId">工具：{{ step.selectedToolId }} · {{ step.selectedToolReason || '-' }}</p>
            <p v-if="step.errorMessage">错误：{{ step.errorMessage }}</p>
          </article>
        </template>
      </section>

      <section v-else-if="activeTab === 'tools'" class="card-shell stack">
        <div v-if="!agentRunDetail" class="empty-line">当前 Trace 未绑定 Agent Run。</div>
        <article v-for="call in agentRunDetail?.toolCalls ?? []" :key="call.id" class="detail-block">
          <div class="item-head">
            <strong>{{ call.toolId }}</strong>
            <span class="status-chip">{{ call.status }}</span>
          </div>
          <p>plugin：{{ call.pluginVersion || '-' }} · latency {{ call.latencyMs ?? '-' }}ms</p>
          <p>请求：{{ call.requestSummary || '-' }}</p>
          <p>响应：{{ call.responseSummary || '-' }}</p>
          <p v-if="call.errorMessage">错误：{{ call.errorMessage }}</p>
          <div class="evidence-grid">
            <div v-if="searchEvidence(call.metadata).length">
              <strong>Search Evidence</strong>
              <ul class="retrieval-items">
                <li v-for="item in searchEvidence(call.metadata).slice(0, 3)" :key="item.title || item.url">
                  {{ item.title || item.url }} · {{ item.url || item.summary || '-' }}
                </li>
              </ul>
            </div>
            <div v-if="graphEvidence(call.metadata).length">
              <strong>Graph Evidence</strong>
              <ul class="retrieval-items">
                <li v-for="item in graphEvidence(call.metadata).slice(0, 3)" :key="item.entity || item.path || JSON.stringify(item)">
                  {{ item.entity || item.path || JSON.stringify(item) }}
                </li>
              </ul>
            </div>
            <div v-if="sandboxExecution(call.metadata)">
              <strong>Sandbox</strong>
              <pre class="metadata">{{ JSON.stringify(sandboxExecution(call.metadata), null, 2) }}</pre>
            </div>
          </div>
        </article>
        <div v-if="agentRunDetail && agentRunDetail.toolCalls.length === 0" class="empty-line">暂无工具调用。</div>
      </section>

      <section v-else-if="activeTab === 'checkpoints'" class="card-shell stack">
        <div v-if="!agentRunDetail" class="empty-line">当前 Trace 未绑定 Agent Run。</div>
        <article v-for="checkpoint in agentRunDetail?.checkpoints ?? []" :key="checkpoint.id" class="detail-block">
          <div class="item-head">
            <strong>#{{ checkpoint.checkpointNo }} {{ checkpoint.checkpointType }}</strong>
            <span class="status-chip">{{ checkpoint.stable ? 'stable' : 'pending' }}</span>
          </div>
          <p>step #{{ checkpoint.stepId ?? '-' }} · {{ formatTime(checkpoint.createdAt) }}</p>
          <pre class="metadata">{{ JSON.stringify(checkpoint.payload, null, 2) }}</pre>
        </article>
        <div v-if="agentRunDetail && agentRunDetail.checkpoints.length === 0" class="empty-line">暂无 checkpoint。</div>
      </section>

      <section v-else class="card-shell stack">
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
  const items: Array<{ id: 'exchange' | 'agent' | 'tools' | 'checkpoints' | 'resume'; label: string }> = [
    { id: 'exchange', label: 'Exchange Trace' },
  ]
  if (traceStore.selectedTrace?.agentRunId) {
    items.push(
      { id: 'agent', label: 'Agent Run' },
      { id: 'tools', label: 'Tool Calls' },
      { id: 'checkpoints', label: 'Checkpoints' },
      { id: 'resume', label: 'Resume Chain' },
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
</script>

<style scoped>
.trace-detail-page {
  display: grid;
  gap: 1rem;
}

.trace-layout {
  display: grid;
  gap: 1rem;
  grid-template-columns: 280px minmax(0, 1fr);
}

.card-shell {
  border-radius: calc(var(--radius-md) + 4px);
  border: 1px solid var(--line-soft);
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
  padding: 1rem 1.2rem;
}

.page-header,
.header-meta,
.item-head,
.tabs {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.page-header {
  justify-content: space-between;
}

.tabs {
  flex-wrap: wrap;
}

.tab-button {
  border-radius: 999px;
  padding: 0.75rem 1rem;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.78);
}

.tab-button--active {
  background: rgba(199, 109, 63, 0.12);
  border-color: rgba(199, 109, 63, 0.36);
}

.timeline-panel,
.detail-panel,
.stack {
  display: grid;
  gap: 1rem;
}

.timeline-item,
.detail-block,
.summary-card {
  padding: 0.8rem 0;
  border-bottom: 1px solid var(--line-soft);
}

.timeline-item--failed,
.detail-block--failed {
  border-color: rgba(166, 54, 32, 0.28);
}

.timeline-item--failed strong,
.detail-block--failed strong {
  color: #8f2f18;
}

.timeline-item {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}

.timeline-item p,
.detail-block p,
.summary-card p,
.empty-line,
.error-banner {
  margin: 0.2rem 0 0;
  color: var(--text-secondary);
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

.status-chip {
  display: inline-flex;
  padding: 0.2rem 0.65rem;
  border-radius: 999px;
  background: rgba(27, 47, 61, 0.08);
}

.retrieval-items {
  margin: 0.5rem 0 0;
  padding-left: 1rem;
  color: var(--text-secondary);
}

.metadata {
  margin: 0.75rem 0 0;
  padding: 0.8rem;
  border-radius: var(--radius-sm);
  background: rgba(17, 37, 52, 0.06);
  overflow: auto;
}

.evidence-grid {
  display: grid;
  gap: 0.8rem;
  margin-top: 0.8rem;
}

@media (max-width: 960px) {
  .trace-layout {
    grid-template-columns: 1fr;
  }

  .page-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
