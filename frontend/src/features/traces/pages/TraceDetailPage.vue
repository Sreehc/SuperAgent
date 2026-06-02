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
    </header>

    <p v-if="traceStore.errorMessage" class="error-banner">{{ traceStore.errorMessage }}</p>
    <section v-if="traceStore.loadingDetail" class="card-shell">正在加载 Trace 详情...</section>

    <template v-else-if="traceStore.selectedTrace">
      <section class="trace-layout">
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
    </template>
  </section>
</template>

<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useTraceStore } from '../store/traces'

const route = useRoute()
const traceStore = useTraceStore()

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
}

function formatDuration(durationMs: number) {
  if (durationMs < 1000) {
    return `${durationMs}ms`
  }
  return `${(durationMs / 1000).toFixed(1)}s`
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

.timeline-panel,
.detail-panel {
  display: grid;
  gap: 1rem;
}

.timeline-item,
.detail-block {
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
.detail-block p {
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

.empty-line,
.error-banner {
  color: var(--text-secondary);
}

.retrieval-items {
  margin: 0.5rem 0 0;
  padding-left: 1rem;
  color: var(--text-secondary);
}
</style>
