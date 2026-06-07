<template>
  <section class="chat-workspace">
    <aside class="chat-panel chat-sessions">
      <header class="chat-panel__header">
        <div>
          <p class="page-kicker">Sessions</p>
          <h2>会话</h2>
        </div>
        <button
          class="btn btn-primary btn-sm"
          data-testid="chat-new-conversation"
          :class="{ 'btn-loading': chatStore.creatingConversation }"
          :disabled="chatStore.creatingConversation"
          type="button"
          @click="newConversation"
        >
          {{ chatStore.creatingConversation ? '创建中...' : '新建会话' }}
        </button>
      </header>

      <label class="field chat-search">
        <span>搜索会话</span>
        <input v-model="chatStore.keyword" type="search" placeholder="输入标题" />
      </label>

      <LoadingSpinner v-if="chatStore.loadingConversations" text="正在加载会话列表..." />
      <EmptyState v-else-if="chatStore.filteredConversations.length === 0" variant="chat" title="暂无会话" description="创建一个会话后开始提问。" />
      <nav v-else class="conversation-list" aria-label="会话列表">
        <article
          v-for="conversation in chatStore.filteredConversations"
          :key="conversation.id"
          class="conversation-item"
          :class="{ 'conversation-item--active': chatStore.selectedSessionId === conversation.id }"
        >
          <button type="button" class="conversation-item__main" @click="selectConversation(conversation.id)">
            <strong v-if="chatStore.editingConversationId !== conversation.id">{{ conversation.title }}</strong>
            <input
              v-else
              :value="conversation.title"
              class="conversation-item__rename"
              type="text"
              @keyup.enter="renameConversation(conversation.id, ($event.target as HTMLInputElement).value)"
              @blur="renameConversation(conversation.id, ($event.target as HTMLInputElement).value)"
            />
            <small>{{ formatTime(conversation.lastMessageAt) }}</small>
          </button>
          <div class="conversation-item__actions">
            <button class="btn-text" type="button" @click="chatStore.editingConversationId = conversation.id">重命名</button>
            <button class="btn-text" type="button" @click="archiveConversation(conversation.id)">归档</button>
            <button class="btn-text btn-danger" type="button" @click="removeConversation(conversation.id)">删除</button>
          </div>
        </article>
      </nav>
    </aside>

    <main class="chat-main">
      <header class="chat-main__header">
        <div>
          <p class="page-kicker">/chat</p>
          <h2>{{ chatStore.selectedConversation?.title ?? '新会话' }}</h2>
        </div>
        <div class="runtime-meta">
          <span class="badge">{{ chatStore.memoryStrategy }}</span>
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

      <section class="message-region" aria-live="polite">
        <LoadingSpinner v-if="chatStore.loadingMessages" text="正在加载消息..." />
        <EmptyState
          v-else-if="!chatStore.hasMessages"
          variant="chat"
          title="先发出第一个问题"
          description="选择知识库和记忆策略后发送消息，系统会展示回答、引用和运行状态。"
        />
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
            <div v-if="message.references.length" class="chip-row">
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
            <div v-if="isAdmin && message.role === 'assistant' && chatStore.streamState.exchangeId" class="chip-row">
              <button class="reference-chip" type="button" @click="openTrace(chatStore.streamState.exchangeId)">查看 Trace</button>
            </div>
          </article>
        </div>

        <section v-if="chatStore.streamState.timeline.length" class="agent-timeline">
          <header>
            <h3>Agent timeline</h3>
            <span class="badge">run #{{ chatStore.streamState.runId }}</span>
          </header>
          <article v-for="(item, index) in chatStore.streamState.timeline" :key="`${item.type}-${index}`" class="agent-timeline__item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.summary }}</p>
          </article>
        </section>
      </section>

      <footer class="composer-card">
        <div class="composer-card__controls">
          <label class="field">
            <span>记忆</span>
            <select v-model="chatStore.memoryStrategy">
              <option value="NONE">不启用记忆</option>
              <option value="SLIDING_WINDOW">滑动窗口</option>
              <option value="SUMMARY_WINDOW">摘要窗口</option>
              <option value="SUMMARY_PLUS_WINDOW">摘要 + 最近消息</option>
            </select>
          </label>
          <label class="field">
            <span>知识库</span>
            <select :value="chatStore.selectedKnowledgeBaseId ?? ''" data-testid="chat-knowledge-base" @change="updateKnowledgeBase">
              <option value="">不指定</option>
              <option v-for="knowledgeBase in chatStore.availableKnowledgeBases" :key="knowledgeBase.id" :value="knowledgeBase.id">
                {{ knowledgeBase.name }}
              </option>
            </select>
          </label>
        </div>

        <textarea
          v-model="chatStore.composerMessage"
          data-testid="chat-composer"
          class="composer-card__input"
          placeholder="输入问题"
          rows="4"
          :disabled="chatStore.streaming"
        />

        <div class="composer-card__actions">
          <p v-if="chatStore.streamState.error" class="composer-card__error">{{ chatStore.streamState.error }}</p>
          <div class="composer-card__buttons">
            <button v-if="chatStore.streaming" class="btn btn-ghost" data-testid="chat-stop" type="button" @click="chatStore.stopStreaming">
              停止生成
            </button>
            <button
              class="btn btn-primary"
              :class="{ 'btn-loading': chatStore.streaming }"
              data-testid="chat-send"
              type="button"
              :disabled="chatStore.streaming || !chatStore.composerMessage.trim()"
              @click="sendMessage"
            >
              {{ chatStore.streaming ? '生成中...' : '发送' }}
            </button>
          </div>
        </div>

        <div v-if="chatStore.streamState.recommendations.length" class="recommendations">
          <span>推荐追问</span>
          <div class="chip-row">
            <button v-for="question in chatStore.streamState.recommendations" :key="question" class="reference-chip" type="button" @click="chatStore.composerMessage = question">
              {{ question }}
            </button>
          </div>
        </div>
      </footer>
    </main>

    <aside class="chat-panel reference-panel">
      <header class="chat-panel__header">
        <div>
          <p class="page-kicker">Context</p>
          <h2>引用来源</h2>
        </div>
      </header>
      <template v-if="chatStore.selectedReference">
        <article class="reference-detail">
          <h3>{{ chatStore.selectedReference.title }}</h3>
          <p>{{ chatStore.selectedReference.quote }}</p>
          <dl>
            <div><dt>chunk</dt><dd>#{{ chatStore.selectedReference.chunkId }}</dd></div>
            <div><dt>score</dt><dd>{{ chatStore.selectedReference.score ?? '-' }}</dd></div>
          </dl>
          <div class="reference-panel__actions">
            <button class="btn btn-secondary btn-sm" type="button" @click="openDocument(chatStore.selectedReference.documentId)">查看文档</button>
            <button v-if="isAdmin && chatStore.streamState.exchangeId" class="btn btn-secondary btn-sm" type="button" @click="openTrace(chatStore.streamState.exchangeId)">
              查看 Trace
            </button>
          </div>
        </article>
      </template>
      <EmptyState v-else variant="search" title="等待引用来源" description="收到 reference 事件后，这里会展示文档摘录。" />
    </aside>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LoadingSpinner, EmptyState } from '../../../components'
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
  grid-template-columns: 280px minmax(0, 1fr) 320px;
  gap: 12px;
  height: calc(100vh - var(--topbar-height) - 36px);
  min-height: 680px;
}

.chat-panel,
.chat-main {
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.chat-panel {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 12px;
  padding: 14px;
  overflow: hidden;
}

.chat-panel__header,
.chat-main__header,
.composer-card__actions,
.agent-timeline header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.chat-panel__header h2,
.chat-main__header h2 {
  margin: 0;
  font-size: 17px;
}

.chat-search {
  gap: 5px;
}

.conversation-list {
  display: grid;
  align-content: start;
  gap: 8px;
  min-height: 0;
  overflow: auto;
  padding-right: 2px;
}

.conversation-item {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
}

.conversation-item--active {
  border-color: color-mix(in srgb, var(--color-accent), transparent 55%);
  background: var(--color-accent-soft);
}

.conversation-item__main {
  display: grid;
  gap: 5px;
  width: 100%;
  padding: 0;
  color: var(--color-text);
  text-align: left;
  border: 0;
  background: transparent;
}

.conversation-item__main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-item__main small {
  color: var(--color-text-muted);
}

.conversation-item__rename {
  min-height: 32px;
}

.conversation-item__actions,
.chip-row,
.composer-card__buttons,
.reference-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.chat-main {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
}

.chat-main__header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-border);
}

.runtime-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.message-region {
  display: grid;
  align-content: start;
  gap: 12px;
  min-height: 0;
  overflow: auto;
  padding: 16px;
  background: var(--color-surface-muted);
}

.message-list {
  display: grid;
  gap: 12px;
}

.message-card {
  display: grid;
  gap: 8px;
  max-width: min(820px, 100%);
  padding: 13px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.message-card--user {
  justify-self: end;
  width: min(680px, 92%);
  background: var(--color-accent-soft);
  border-color: color-mix(in srgb, var(--color-accent), transparent 68%);
}

.message-card--assistant {
  justify-self: start;
}

.message-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.message-card__header strong {
  color: var(--color-text);
  font-family: var(--font-mono);
}

.message-card__content {
  min-width: 0;
}

.message-card__status {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.message-card__status--error,
.composer-card__error {
  color: var(--color-danger);
}

.reference-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 9px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  color: var(--color-accent);
  background: var(--color-surface);
  font-size: 12px;
  font-weight: 700;
}

.reference-chip:hover {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

.agent-timeline {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.agent-timeline h3 {
  margin: 0;
  font-size: 14px;
}

.agent-timeline__item {
  padding-top: 8px;
  border-top: 1px solid var(--color-border);
}

.agent-timeline__item p {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.composer-card {
  display: grid;
  gap: 10px;
  padding: 14px;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
}

.composer-card__controls {
  display: grid;
  grid-template-columns: minmax(0, 220px) minmax(0, 1fr);
  gap: 10px;
}

.composer-card__input {
  min-height: 92px;
}

.composer-card__actions {
  align-items: center;
}

.composer-card__error {
  margin: 0;
  font-size: 13px;
}

.recommendations {
  display: grid;
  gap: 8px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.reference-panel {
  grid-template-rows: auto minmax(0, 1fr);
}

.reference-detail {
  display: grid;
  gap: 12px;
  min-height: 0;
  overflow: auto;
}

.reference-detail h3 {
  margin: 0;
  font-size: 16px;
}

.reference-detail p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.7;
}

.reference-detail dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin: 0;
}

.reference-detail dl div {
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
}

.reference-detail dt {
  color: var(--color-text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
}

.reference-detail dd {
  margin: 4px 0 0;
  font-weight: 760;
}

@media (max-width: 1220px) {
  .chat-workspace {
    grid-template-columns: 260px minmax(0, 1fr);
    height: auto;
    min-height: 0;
  }

  .reference-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 860px) {
  .chat-workspace {
    grid-template-columns: 1fr;
  }

  .composer-card__controls {
    grid-template-columns: 1fr;
  }
}
</style>
