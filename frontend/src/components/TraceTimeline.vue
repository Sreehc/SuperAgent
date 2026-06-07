<template>
  <section class="trace-timeline">
    <header class="trace-timeline__header">
      <h3>执行时间线</h3>
      <div class="trace-timeline__stats">
        <span class="badge">总耗时 {{ formatDuration(totalDuration) }}</span>
        <span class="badge">步骤 {{ steps.length }}</span>
      </div>
    </header>

    <div class="trace-timeline__list">
      <button
        v-for="(step, index) in steps"
        :key="`${step.title}-${index}`"
        class="trace-step"
        :class="[`trace-step--${step.status}`, { 'trace-step--active': activeStep === index }]"
        type="button"
        @click="selectStep(index)"
      >
        <span class="trace-step__index">{{ index + 1 }}</span>
        <span class="trace-step__body">
          <strong>{{ step.title }}</strong>
          <small>{{ step.type }} / {{ formatTime(step.timestamp) }} / {{ formatDuration(step.duration) }}</small>
        </span>
        <span class="badge" :class="statusClass(step.status)">{{ step.status }}</span>
      </button>
    </div>

    <Transition name="detail">
      <article v-if="activeStep !== null" class="trace-detail">
        <header>
          <h4>{{ steps[activeStep].title }}</h4>
          <button class="btn-text" type="button" @click="activeStep = null">关闭</button>
        </header>
        <dl>
          <div><dt>类型</dt><dd>{{ steps[activeStep].type }}</dd></div>
          <div><dt>状态</dt><dd>{{ steps[activeStep].status }}</dd></div>
          <div><dt>开始时间</dt><dd>{{ formatTime(steps[activeStep].timestamp) }}</dd></div>
          <div><dt>耗时</dt><dd>{{ formatDuration(steps[activeStep].duration) }}</dd></div>
        </dl>
        <pre v-if="steps[activeStep].data" class="metadata">{{ JSON.stringify(steps[activeStep].data, null, 2) }}</pre>
      </article>
    </Transition>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

interface TraceStep {
  title: string
  type: 'agent' | 'tool' | 'query' | 'reasoning' | 'response'
  status: 'success' | 'failed' | 'running' | 'pending'
  timestamp: string
  duration: number
  data?: unknown
}

interface Props {
  steps: TraceStep[]
}

const props = defineProps<Props>()
const activeStep = ref<number | null>(null)

const totalDuration = computed(() => props.steps.reduce((sum, step) => sum + step.duration, 0))

function selectStep(index: number) {
  activeStep.value = activeStep.value === index ? null : index
}

function formatTime(timestamp: string): string {
  return new Date(timestamp).toLocaleTimeString('zh-CN')
}

function formatDuration(ms: number): string {
  if (ms < 1000) {
    return `${ms}ms`
  }
  return `${(ms / 1000).toFixed(2)}s`
}

function statusClass(status: TraceStep['status']) {
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
.trace-timeline {
  display: grid;
  gap: 12px;
}

.trace-timeline__header,
.trace-timeline__stats,
.trace-detail header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.trace-timeline__header h3,
.trace-detail h4 {
  margin: 0;
}

.trace-timeline__stats {
  flex-wrap: wrap;
}

.trace-timeline__list {
  display: grid;
  gap: 8px;
}

.trace-step {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text);
  text-align: left;
}

.trace-step:hover,
.trace-step--active {
  border-color: color-mix(in srgb, var(--color-accent), transparent 58%);
  background: var(--color-accent-soft);
}

.trace-step__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xs);
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 800;
}

.trace-step__body {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.trace-step__body small {
  color: var(--color-text-muted);
}

.trace-detail {
  display: grid;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted);
}

.trace-detail dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.trace-detail dl div {
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}

.trace-detail dt {
  color: var(--color-text-subtle);
  font-size: 12px;
}

.trace-detail dd {
  margin: 4px 0 0;
  font-weight: 700;
}

.detail-enter-active,
.detail-leave-active {
  transition: opacity var(--duration-base) var(--ease-standard), transform var(--duration-base) var(--ease-standard);
}

.detail-enter-from,
.detail-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

@media (max-width: 720px) {
  .trace-step {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .trace-step > .badge {
    grid-column: 2;
    justify-self: start;
  }

  .trace-detail dl {
    grid-template-columns: 1fr;
  }
}
</style>
