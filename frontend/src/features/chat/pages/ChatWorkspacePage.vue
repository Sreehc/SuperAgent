<template>
  <section class="chat-workspace">
    <aside class="chat-workspace__sidebar">
      <div class="chat-workspace__sidebar-header">
        <div>
          <p class="eyebrow">会话</p>
          <h2>对话工作台</h2>
        </div>
        <button class="pill-button" data-testid="chat-new-conversation" :disabled="chatStore.creatingConversation" type="button" @click="newConversation">
          {{ chatStore.creatingConversation ? '创建中...' : '+ 新建会话' }}
        </button>
      </div>

      <input
        v-model="chatStore.keyword"
        class="chat-workspace__search"
        type="search"
        placeholder="搜索会话..."
      />

      <div v-if="chatStore.loadingConversations" class="muted-card">正在加载会话列表...</div>
      <div v-else-if="chatStore.filteredConversations.length === 0" class="muted-card">
        暂无会话，先创建一个对话开始提问。
      </div>
      <nav v-else class="conversation-list" aria-label="会话列表">
        <div
          v-for="conversation in chatStore.filteredConversations"
          :key="conversation.id"
          class="conversation-list__item"
          :class="{ 'conversation-list__item--active': chatStore.selectedSessionId === conversation.id }"
        >
          <button type="button" class="conversation-list__main" @click="selectConversation(conversation.id)">
            <strong v-if="chatStore.editingConversationId !== conversation.id">{{ conversation.title }}</strong>
            <input
              v-else
              :value="conversation.title"
              class="conversation-list__rename"
              type="text"
              @keyup.enter="renameConversation(conversation.id, ($event.target as HTMLInputElement).value)"
              @blur="renameConversation(conversation.id, ($event.target as HTMLInputElement).value)"
            />
            <small>{{ formatTime(conversation.lastMessageAt) }}</small>
          </button>
          <div class="conversation-list__actions">
            <button class="table-link" type="button" @click="chatStore.editingConversationId = conversation.id">重命名</button>
            <button class="table-link" type="button" @click="archiveConversation(conversation.id)">归档</button>
            <button class="table-link table-link--danger" type="button" @click="removeConversation(conversation.id)">删除</button>
          </div>
        </div>
      </nav>
    </aside>

    <main class="chat-workspace__timeline">
      <header class="timeline-header">
        <div>
          <p class="eyebrow">/chat</p>
          <h2>{{ chatStore.selectedConversation?.title ?? '新会话' }}</h2>
        </div>
        <div class="timeline-header__meta">
          <span>记忆：{{ chatStore.memoryStrategy }}</span>
          <span v-if="chatStore.streamState.stage">阶段：{{ chatStore.streamState.stage }}</span>
          <button
            v-if="chatStore.selectedSessionId && chatStore.streamState.runId && !chatStore.streaming"
            class="ghost-button"
            type="button"
            @click="chatStore.resumeConversationRun"
          >
            恢复 Agent
          </button>
        </div>
      </header>

      <div v-if="chatStore.loadingMessages" class="timeline-empty">正在加载消息...</div>
      <div v-else-if="!chatStore.hasMessages" class="timeline-empty">
        <h3>先发出第一个问题</h3>
        <p>这个阶段已经接通会话列表、消息时间线、SSE 流式回答和停止生成。</p>
      </div>
      <div v-else class="message-list">
        <article
          v-for="message in chatStore.messages"
          :key="message.id"
          class="message-card"
          :class="`message-card--${message.role}`"
        >
          <header class="message-card__header">
            <strong>{{ roleLabel(message.role) }}</strong>
            <span>{{ formatTime(message.createdAt) }}</span>
          </header>
          <div class="message-card__content markdown-body" v-html="renderMessage(message.content || '...')" />
          <p v-if="message.status === 'stopped'" class="message-card__status">已停止生成</p>
          <p v-if="message.status === 'error'" class="message-card__status message-card__status--error">生成失败</p>
          <div v-if="message.references.length" class="message-card__references">
            <button
              v-for="reference in message.references"
              :key="`${message.id}-${reference.ordinal}`"
              class="reference-chip"
              data-testid="chat-reference-chip"
              type="button"
              @click="chatStore.selectedReference = reference"
            >
              [{{ reference.ordinal }}] {{ reference.title }}
            </button>
          </div>
          <div
            v-if="isAdmin && message.role === 'assistant' && chatStore.streamState.exchangeId"
            class="message-card__references"
          >
            <button class="reference-chip" type="button" @click="openTrace(chatStore.streamState.exchangeId)">查看 Trace</button>
          </div>
        </article>
      </div>

      <section v-if="chatStore.streamState.timeline.length" class="agent-timeline">
        <div class="agent-timeline__header">
          <p class="eyebrow">智能体时间线</p>
          <small>run #{{ chatStore.streamState.runId }}</small>
        </div>
        <article
          v-for="(item, index) in chatStore.streamState.timeline"
          :key="`${item.type}-${index}`"
          class="agent-timeline__item"
        >
          <strong>{{ item.title }}</strong>
          <p>{{ item.summary }}</p>
        </article>
      </section>

      <footer class="composer-card">
        <div class="composer-card__controls">
          <label>
            记忆
            <select v-model="chatStore.memoryStrategy">
              <option value="NONE">不启用记忆</option>
              <option value="SLIDING_WINDOW">滑动窗口</option>
              <option value="SUMMARY_WINDOW">摘要窗口</option>
              <option value="SUMMARY_PLUS_WINDOW">摘要 + 最近消息</option>
            </select>
          </label>
          <label>
            知识库
            <select
              :value="chatStore.selectedKnowledgeBaseId ?? ''"
              data-testid="chat-knowledge-base"
              @change="updateKnowledgeBase"
            >
              <option value="">不指定</option>
              <option
                v-for="knowledgeBase in chatStore.availableKnowledgeBases"
                :key="knowledgeBase.id"
                :value="knowledgeBase.id"
              >
                {{ knowledgeBase.name }}
              </option>
            </select>
          </label>
        </div>

        <textarea
          v-model="chatStore.composerMessage"
          data-testid="chat-composer"
          class="composer-card__input"
          placeholder="输入问题，开始最小可用流式对话..."
          rows="4"
          :disabled="chatStore.streaming"
        />

        <div class="composer-card__actions">
          <p v-if="chatStore.streamState.error" class="composer-card__error">{{ chatStore.streamState.error }}</p>
          <div class="composer-card__buttons">
            <button
              v-if="chatStore.streaming"
              class="ghost-button"
              data-testid="chat-stop"
              type="button"
              @click="chatStore.stopStreaming"
            >
              停止生成
            </button>
            <button
              class="pill-button"
              data-testid="chat-send"
              type="button"
              :disabled="chatStore.streaming || !chatStore.composerMessage.trim()"
              @click="sendMessage"
            >
              {{ chatStore.streaming ? '生成中...' : '发送' }}
            </button>
          </div>
        </div>

        <div v-if="chatStore.streamState.recommendations.length" class="composer-card__recommendations">
          <span>推荐追问：</span>
          <div class="recommendations-list">
            <button
              v-for="question in chatStore.streamState.recommendations"
              :key="question"
              class="recommendation-chip"
              type="button"
              @click="chatStore.composerMessage = question"
            >
              {{ question }}
            </button>
          </div>
        </div>
      </footer>
    </main>

    <aside class="chat-workspace__reference">
      <div class="reference-panel">
        <p class="eyebrow">引用来源</p>
        <template v-if="chatStore.selectedReference">
          <h3>{{ chatStore.selectedReference.title }}</h3>
          <p>{{ chatStore.selectedReference.quote }}</p>
          <small>chunk #{{ chatStore.selectedReference.chunkId }} · score {{ chatStore.selectedReference.score ?? '-' }}</small>
          <div class="reference-panel__actions">
            <button class="ghost-button" type="button" @click="openDocument(chatStore.selectedReference.documentId)">查看文档</button>
            <button
              v-if="isAdmin && chatStore.streamState.exchangeId"
              class="ghost-button"
              type="button"
              @click="openTrace(chatStore.streamState.exchangeId)"
            >
              查看 Trace
            </button>
          </div>
        </template>
        <template v-else>
          <h3>等待引用来源</h3>
          <p>收到 `reference` 事件后，这里会展示文档摘录。</p>
        </template>
      </div>
    </aside>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../auth/store/auth'
import { useChatStore } from '../store/chat'
import type { MessageRole } from '../types'
import { renderMarkdown } from '../utils/renderMarkdown'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const authStore = useAuthStore()
const isAdmin = computed(() => ['OWNER', 'ADMIN'].includes(authStore.currentRole ?? ''))

onMounted(async () => {
  await chatStore.bootstrap(readSessionId())
  await syncRouteWithStore()
})

onBeforeUnmount(() => {
  chatStore.clearOnRouteLeave()
})

watch(
  () => route.params.sessionId,
  async (value, oldValue) => {
    if (value === oldValue) {
      return
    }

    const sessionId = normalizeSessionId(value)
    if (sessionId && sessionId !== chatStore.selectedSessionId) {
      await chatStore.selectConversation(sessionId)
      return
    }

    if (!sessionId && chatStore.selectedSessionId) {
      await syncRouteWithStore()
    }
  },
)

async function newConversation() {
  await chatStore.createAndSelectConversation()
  await syncRouteWithStore()
}

async function selectConversation(sessionId: number) {
  await chatStore.selectConversation(sessionId)
  await router.replace(`/chat/${sessionId}`)
}

async function sendMessage() {
  try {
    await chatStore.sendMessage()
    await syncRouteWithStore()
  } catch {
    // Error text is already normalized into the store.
  }
}

async function renameConversation(sessionId: number, title: string) {
  await chatStore.renameConversation(sessionId, title)
}

async function archiveConversation(sessionId: number) {
  await chatStore.archiveConversation(sessionId)
  await syncRouteWithStore()
}

async function removeConversation(sessionId: number) {
  if (!window.confirm('删除后无法恢复，确认继续吗？')) {
    return
  }
  await chatStore.removeConversation(sessionId)
  await syncRouteWithStore()
}

function normalizeSessionId(value: unknown) {
  if (typeof value !== 'string') {
    return null
  }
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function readSessionId() {
  return normalizeSessionId(route.params.sessionId)
}

async function syncRouteWithStore() {
  if (!chatStore.selectedSessionId) {
    if (route.path !== '/chat') {
      await router.replace('/chat')
    }
    return
  }

  const target = `/chat/${chatStore.selectedSessionId}`
  if (route.path !== target) {
    await router.replace(target)
  }
}

function roleLabel(role: MessageRole) {
  if (role === 'assistant') {
    return 'AI'
  }
  if (role === 'system') {
    return '系统'
  }
  return '用户'
}

function formatTime(value: string | null) {
  if (!value) {
    return '刚刚'
  }
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function updateKnowledgeBase(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (!value) {
    chatStore.setSelectedKnowledgeBaseId(null)
    return
  }
  const parsed = Number(value)
  chatStore.setSelectedKnowledgeBaseId(Number.isInteger(parsed) && parsed > 0 ? parsed : null)
}

function renderMessage(content: string) {
  return renderMarkdown(content)
}

async function openDocument(documentId: number) {
  await router.push(`/documents/${documentId}`)
}

async function openTrace(exchangeId: number) {
  await router.push(`/traces/${exchangeId}`)
}
</script>

<style scoped>
.chat-workspace {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 300px;
  gap: 1rem;
  min-height: calc(100vh - 160px);
}

.agent-timeline {
  margin-top: 1rem;
  padding: 1rem;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 1rem;
  background: rgba(248, 250, 252, 0.9);
}

.agent-timeline__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.75rem;
}

.agent-timeline__item {
  padding: 0.75rem 0;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.agent-timeline__item:first-of-type {
  border-top: none;
  padding-top: 0;
}

.chat-workspace__sidebar,
.chat-workspace__timeline,
.chat-workspace__reference {
  min-width: 0;
}

.chat-workspace__sidebar,
.reference-panel,
.composer-card,
.timeline-header,
.timeline-empty,
.message-card {
  border-radius: calc(var(--radius-md) + 4px);
  border: 1px solid var(--line-soft);
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
}

.chat-workspace__sidebar {
  padding: 1rem;
}

.chat-workspace__search,
.conversation-list__rename {
  width: 100%;
  padding: 0.78rem 0.92rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.84);
}

.chat-workspace__sidebar-header,
.timeline-header,
.composer-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.eyebrow {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.chat-workspace__sidebar-header h2,
.timeline-header h2,
.reference-panel h3,
.timeline-empty h3 {
  margin: 0.2rem 0 0;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
}

.muted-card,
.timeline-empty,
.reference-panel {
  padding: 1rem;
}

.conversation-list {
  display: grid;
  gap: 0.75rem;
  margin-top: 1rem;
}

.conversation-list__item {
  display: grid;
  gap: 0.7rem;
  padding: 0.9rem 1rem;
  text-align: left;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.55);
}

.conversation-list__item--active {
  border-color: rgba(199, 109, 63, 0.45);
  background: rgba(199, 109, 63, 0.1);
}

.conversation-list__main,
.conversation-list__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.65rem;
}

.conversation-list__main {
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
}

.conversation-list__actions {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.chat-workspace__timeline {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 1rem;
}

.timeline-header {
  padding: 1rem 1.2rem;
}

.timeline-header__meta {
  display: grid;
  gap: 0.25rem;
  justify-items: end;
  color: var(--text-secondary);
}

.message-list {
  display: grid;
  gap: 0.9rem;
  align-content: start;
}

.message-card {
  padding: 1rem;
}

.message-card--user {
  background: rgba(255, 252, 247, 0.98);
}

.message-card--assistant {
  background:
    linear-gradient(180deg, rgba(255, 252, 247, 0.96), rgba(255, 247, 238, 0.96)),
    radial-gradient(circle at top right, rgba(199, 109, 63, 0.14), transparent 34%);
}

.message-card__header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  color: var(--text-secondary);
}

.message-card__content {
  margin: 0.6rem 0 0;
  line-height: 1.7;
}

.markdown-body :deep(p) {
  margin: 0 0 0.65rem;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.45rem 0 0.65rem;
  padding-left: 1.25rem;
}

.message-card__status {
  margin: 0.75rem 0 0;
  color: var(--text-secondary);
}

.message-card__status--error,
.composer-card__error {
  color: var(--danger);
}

.message-card__references,
.composer-card__recommendations {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 0.8rem;
  align-items: flex-start;
}

.composer-card__recommendations {
  flex-direction: column;
}

.recommendations-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  max-height: 200px;
  overflow-y: auto;
  padding: 0.5rem 0;
}

.reference-chip,
.recommendation-chip,
.ghost-button,
.pill-button {
  border-radius: 999px;
  padding: 0.55rem 0.85rem;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.75);
}

.pill-button {
  border: 0;
  background: linear-gradient(135deg, var(--bg-accent), #d78655);
  color: var(--text-contrast);
}

.ghost-button {
  background: rgba(27, 47, 61, 0.08);
}

.table-link {
  padding: 0.35rem 0.7rem;
}

.table-link--danger {
  color: var(--danger);
}

.composer-card {
  padding: 1rem;
}

.composer-card__controls {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.6rem;
  align-items: center;
}

.composer-card__controls select {
  margin-left: 0.25rem;
}

.composer-card__controls label {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--text-secondary);
  font-size: 0.9rem;
}

.composer-card__input {
  width: 100%;
  resize: vertical;
  min-height: 100px;
  padding: 0.85rem 1rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.85);
}

.composer-card__buttons {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.reference-panel small {
  color: var(--text-secondary);
}

.reference-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 1rem;
}

@media (max-width: 1180px) {
  .chat-workspace {
    grid-template-columns: 1fr;
  }
}
</style>
