<template>
  <footer class="composer-dock">
    <div class="composer-chips">
      <label class="composer-chip">
        <span>模式</span>
        <select :value="composerStore.executionMode" @change="updateExecutionMode">
          <option value="AUTO">Auto</option>
          <option value="RAG_QA">RAG</option>
          <option value="REACT_AGENT">Agent</option>
        </select>
      </label>
      <label class="composer-chip">
        <span>记忆</span>
        <select v-model="composerStore.memoryStrategy">
          <option value="NONE">不启用记忆</option>
          <option value="SLIDING_WINDOW">滑动窗口</option>
          <option value="SUMMARY_WINDOW">摘要窗口</option>
          <option value="SUMMARY_PLUS_WINDOW">摘要 + 最近消息</option>
        </select>
      </label>
      <label class="composer-chip composer-chip--wide">
        <span>知识库</span>
        <select :value="composerStore.selectedKnowledgeBaseId ?? ''" data-testid="chat-knowledge-base" @change="updateKnowledgeBase">
          <option value="">不指定</option>
          <option v-for="knowledgeBase in composerStore.availableKnowledgeBases" :key="knowledgeBase.id" :value="knowledgeBase.id">
            {{ knowledgeBase.name }}
          </option>
        </select>
      </label>
    </div>

    <div v-if="composerStore.executionMode === 'REACT_AGENT' && composerStore.toolCapabilities.length" class="tool-capability-strip">
      <span>Agent 工具</span>
      <button
        v-for="tool in visibleToolCapabilities"
        :key="tool.toolId"
        class="tool-capability"
        :class="{ 'tool-capability--blocked': !tool.executable, 'tool-capability--risk': tool.requiresConfirmation }"
        type="button"
        :title="`${tool.description} / ${tool.reason}`"
      >
        {{ tool.name }}
        <small>{{ tool.executable ? '可用' : '受限' }}</small>
      </button>
    </div>

    <textarea
      v-model="composerStore.message"
      data-testid="chat-composer"
      class="composer-input"
      placeholder="输入问题，Enter 发送，Shift + Enter 换行"
      rows="4"
      :disabled="chatStore.streaming"
      @keydown.enter.exact.prevent="send"
    />

    <div class="composer-actions">
      <p v-if="chatStore.streamState.error" class="composer-error">{{ chatStore.streamState.error }}</p>
      <div class="composer-buttons">
        <button v-if="chatStore.streaming" class="btn btn-ghost" data-testid="chat-stop" type="button" @click="chatStore.stopStreaming">
          <PhStop :size="15" weight="bold" aria-hidden="true" />
          停止
        </button>
        <button
          class="btn btn-primary"
          :class="{ 'btn-loading': chatStore.streaming }"
          data-testid="chat-send"
          type="button"
          :disabled="chatStore.streaming || !composerStore.message.trim()"
          @click="send"
        >
          <PhPaperPlaneTilt v-if="!chatStore.streaming" :size="15" weight="bold" aria-hidden="true" />
          {{ chatStore.streaming ? '生成中...' : '发送' }}
        </button>
      </div>
    </div>

    <div v-if="chatStore.streamState.recommendations.length" class="recommendations">
      <span>推荐追问</span>
      <div class="chip-row">
        <button
          v-for="question in chatStore.streamState.recommendations"
          :key="question"
          class="reference-chip"
          type="button"
          @click="composerStore.useRecommendation(question)"
        >
          {{ question }}
        </button>
      </div>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { PhPaperPlaneTilt, PhStop } from '@phosphor-icons/vue'
import { computed } from 'vue'
import { useChatStore } from '../store/chat'
import { useChatComposerStore } from '../store/composer'
import type { RequestedExecutionMode } from '../types'

const emit = defineEmits<{ send: [] }>()
const chatStore = useChatStore()
const composerStore = useChatComposerStore()
const visibleToolCapabilities = computed(() => composerStore.toolCapabilities.slice(0, 4))

function send() {
  if (!chatStore.streaming && composerStore.message.trim()) {
    emit('send')
  }
}

function updateKnowledgeBase(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  const parsed = Number(value)
  composerStore.setSelectedKnowledgeBaseId(value && Number.isInteger(parsed) && parsed > 0 ? parsed : null)
}

function updateExecutionMode(event: Event) {
  composerStore.setExecutionMode((event.target as HTMLSelectElement).value as RequestedExecutionMode)
}
</script>
