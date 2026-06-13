<template>
  <main class="conversation-surface">
    <header class="conversation-header">
      <div>
        <p class="section-label">Conversation</p>
        <h1>{{ chatStore.selectedConversation?.title ?? '新会话' }}</h1>
      </div>
      <div class="runtime-meta">
        <span class="metric-chip">{{ composerStore.memoryStrategy }}</span>
        <span v-if="chatStore.streamState.stage" class="badge badge--accent">{{ chatStore.streamState.stage }}</span>
        <button
          v-if="chatStore.selectedSessionId && chatStore.streamState.runId && !chatStore.streaming"
          class="btn btn-ghost btn-sm"
          type="button"
          @click="chatStore.resumeConversationRun"
        >
          恢复 Agent
        </button>
      </div>
    </header>

    <MessageList @open-trace="$emit('open-trace', $event)" />
    <ChatComposer @send="$emit('send')" />
  </main>
</template>

<script setup lang="ts">
import { useChatStore } from '../store/chat'
import { useChatComposerStore } from '../store/composer'
import ChatComposer from './ChatComposer.vue'
import MessageList from './MessageList.vue'

defineEmits<{
  send: []
  'open-trace': [exchangeId: number]
}>()

const chatStore = useChatStore()
const composerStore = useChatComposerStore()
</script>
