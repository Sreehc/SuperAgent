<template>
  <aside class="evidence-inspector reference-panel" aria-label="引用来源">
    <header class="evidence-inspector__header">
      <div>
        <p class="section-label">Evidence</p>
        <h2>引用来源</h2>
      </div>
    </header>

    <article v-if="chatStore.selectedReference" class="reference-detail">
      <h3>{{ chatStore.selectedReference.title }}</h3>
      <blockquote>{{ chatStore.selectedReference.quote }}</blockquote>
      <div class="score-meter">
        <span>score</span>
        <strong>{{ chatStore.selectedReference.score ?? '-' }}</strong>
        <div><i :style="{ width: scoreWidth(chatStore.selectedReference.score) }"></i></div>
      </div>
      <dl>
        <div><dt>chunk</dt><dd>#{{ chatStore.selectedReference.chunkId }}</dd></div>
        <div><dt>document</dt><dd>#{{ chatStore.selectedReference.documentId }}</dd></div>
      </dl>
      <div class="action-row">
        <button class="btn btn-secondary btn-sm" type="button" @click="$emit('open-document', chatStore.selectedReference.documentId)">查看文档</button>
        <button v-if="isAdmin && chatStore.streamState.exchangeId" class="btn btn-secondary btn-sm" type="button" @click="$emit('open-trace', chatStore.streamState.exchangeId)">
          查看 Trace
        </button>
      </div>
    </article>
    <EmptyState v-else variant="search" title="等待引用来源" description="收到 reference 事件后，这里会展示文档摘录和 score。" />

    <AgentRunTimeline />
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { EmptyState } from '../../../components'
import { useAuthStore } from '../../auth/store/auth'
import { useChatStore } from '../store/chat'
import AgentRunTimeline from './AgentRunTimeline.vue'

defineEmits<{
  'open-document': [documentId: number]
  'open-trace': [exchangeId: number]
}>()

const chatStore = useChatStore()
const authStore = useAuthStore()
const isAdmin = computed(() => ['OWNER', 'ADMIN'].includes(authStore.currentRole ?? ''))

function scoreWidth(score: number | null) {
  return `${Math.max(0, Math.min(1, score ?? 0)) * 100}%`
}
</script>
