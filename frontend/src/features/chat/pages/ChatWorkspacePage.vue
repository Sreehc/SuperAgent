<template>
  <section class="chat-workspace">
    <aside class="session-rail" aria-label="会话列表">
      <div class="session-rail__top">
        <div>
          <p class="section-label">Sessions</p>
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
          <PhPlus :size="14" weight="bold" aria-hidden="true" />
          {{ chatStore.creatingConversation ? '创建中' : '新建' }}
        </button>
      </div>

      <label class="field session-search">
        <span>搜索会话</span>
        <input v-model="chatStore.keyword" type="search" placeholder="输入标题" />
      </label>

      <LoadingSpinner v-if="chatStore.loadingConversations" text="正在加载会话列表..." />
      <EmptyState v-else-if="chatStore.filteredConversations.length === 0" variant="chat" title="暂无会话" description="创建会话后开始提问。" />
      <nav v-else class="conversation-list" aria-label="会话列表">
        <article
          v-for="conversation in chatStore.filteredConversations"
          :key="conversation.id"
          class="conversation-row"
          :class="{ 'conversation-row--active': chatStore.selectedSessionId === conversation.id }"
        >
          <button type="button" class="conversation-row__main" @click="selectConversation(conversation.id)">
            <strong v-if="chatStore.editingConversationId !== conversation.id">{{ conversation.title }}</strong>
            <input
              v-else
              :value="conversation.title"
              class="conversation-row__rename"
              type="text"
              @keyup.enter="renameConversation(conversation.id, ($event.target as HTMLInputElement).value)"
              @blur="renameConversation(conversation.id, ($event.target as HTMLInputElement).value)"
            />
            <small>{{ formatTime(conversation.lastMessageAt) }}</small>
          </button>
          <div class="conversation-row__actions">
            <button class="btn-text" type="button" @click="chatStore.editingConversationId = conversation.id">重命名</button>
            <button class="btn-text" type="button" @click="archiveConversation(conversation.id)">归档</button>
            <button class="btn-text danger-text" type="button" @click="removeConversation(conversation.id)">删除</button>
          </div>
        </article>
      </nav>
    </aside>

    <main class="conversation-surface">
      <header class="conversation-header">
        <div>
          <p class="section-label">Conversation</p>
          <h1>{{ chatStore.selectedConversation?.title ?? '新会话' }}</h1>
        </div>
        <div class="runtime-meta">
          <span class="metric-chip">{{ chatStore.memoryStrategy }}</span>
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
            class="message-block"
            :class="`message-block--${message.role}`"
          >
            <header class="message-block__header">
              <strong>{{ roleLabel(message.role) }}</strong>
              <span>{{ formatTime(message.createdAt) }}</span>
            </header>
            <div class="message-block__content markdown-body" v-html="renderMessage(message.content || '...')" />
            <p v-if="message.status === 'stopped'" class="message-block__status">已停止生成</p>
            <p v-if="message.status === 'error'" class="message-block__status message-block__status--error">生成失败</p>
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
      </section>

      <footer class="composer-dock">
        <div class="composer-chips">
          <label class="composer-chip">
            <span>记忆</span>
            <select v-model="chatStore.memoryStrategy">
              <option value="NONE">不启用记忆</option>
              <option value="SLIDING_WINDOW">滑动窗口</option>
              <option value="SUMMARY_WINDOW">摘要窗口</option>
              <option value="SUMMARY_PLUS_WINDOW">摘要 + 最近消息</option>
            </select>
          </label>
          <label class="composer-chip composer-chip--wide">
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
          class="composer-input"
          placeholder="输入问题"
          rows="4"
          :disabled="chatStore.streaming"
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
              :disabled="chatStore.streaming || !chatStore.composerMessage.trim()"
              @click="sendMessage"
            >
              <PhPaperPlaneTilt v-if="!chatStore.streaming" :size="15" weight="bold" aria-hidden="true" />
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

    <aside class="evidence-inspector reference-panel" aria-label="引用来源">
      <header class="evidence-inspector__header">
        <div>
          <p class="section-label">Evidence</p>
          <h2>引用来源</h2>
        </div>
      </header>

      <template v-if="chatStore.selectedReference">
        <article class="reference-detail">
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
            <button class="btn btn-secondary btn-sm" type="button" @click="openDocument(chatStore.selectedReference.documentId)">查看文档</button>
            <button v-if="isAdmin && chatStore.streamState.exchangeId" class="btn btn-secondary btn-sm" type="button" @click="openTrace(chatStore.streamState.exchangeId)">
              查看 Trace
            </button>
          </div>
        </article>
      </template>
      <EmptyState v-else variant="search" title="等待引用来源" description="收到 reference 事件后，这里会展示文档摘录和 score。" />

      <section v-if="chatStore.streamState.timeline.length" class="run-timeline">
        <header>
          <h3>Run timeline</h3>
          <span class="metric-chip">run #{{ chatStore.streamState.runId }}</span>
        </header>
        <article v-for="(item, index) in chatStore.streamState.timeline" :key="`${item.type}-${index}`" class="run-step">
          <span></span>
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.summary }}</p>
          </div>
        </article>
      </section>
    </aside>
  </section>
</template>

<script setup lang="ts">
import { PhPaperPlaneTilt, PhPlus, PhStop } from '@phosphor-icons/vue'
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

function scoreWidth(score: number | null) {
  if (score == null) {
    return '0%'
  }
  return `${Math.max(0, Math.min(1, score)) * 100}%`
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
  grid-template-columns: 292px minmax(0, 1fr) 332px;
  gap: 12px;
  height: calc(100vh - var(--utility-height) - 28px);
  min-height: 690px;
}

.session-rail,
.conversation-surface,
.evidence-inspector {
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--bg-surface);
}

.session-rail {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 12px;
  padding: 14px;
  overflow: hidden;
}

.session-rail__top,
.conversation-header,
.composer-actions,
.run-timeline header,
.evidence-inspector__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.session-rail__top h2,
.evidence-inspector__header h2,
.conversation-header h1 {
  margin: 0;
}

.session-rail__top h2,
.evidence-inspector__header h2 {
  font-size: 16px;
}

.conversation-header h1 {
  font-size: 19px;
}

.conversation-list {
  display: grid;
  align-content: start;
  gap: 4px;
  min-height: 0;
  overflow: auto;
  padding-right: 2px;
}

.conversation-row {
  position: relative;
  display: grid;
  gap: 7px;
  padding: 9px 9px 9px 12px;
  border-radius: var(--radius-1);
}

.conversation-row::before {
  position: absolute;
  inset: 8px auto 8px 0;
  width: 3px;
  border-radius: 999px;
  background: transparent;
  content: "";
}

.conversation-row:hover {
  background: var(--bg-subtle);
}

.conversation-row--active {
  background: var(--accent-soft);
}

.conversation-row--active::before {
  background: var(--accent);
}

.conversation-row__main {
  display: grid;
  gap: 5px;
  width: 100%;
  padding: 0;
  color: var(--text-main);
  text-align: left;
  border: 0;
  background: transparent;
}

.conversation-row__main strong,
.conversation-row__main small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-row__main small {
  color: var(--text-muted);
}

.conversation-row__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-standard);
}

.conversation-row:hover .conversation-row__actions,
.conversation-row:focus-within .conversation-row__actions {
  opacity: 1;
}

.danger-text {
  color: var(--danger);
}

.conversation-surface {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
}

.conversation-header {
  align-items: center;
  padding: 13px 15px;
  border-bottom: 1px solid var(--line-soft);
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
  background:
    linear-gradient(180deg, rgba(47, 111, 94, 0.06), transparent 34%),
    color-mix(in srgb, var(--bg-inset), var(--bg-surface) 34%);
}

.message-list {
  display: grid;
  gap: 12px;
}

.message-block {
  display: grid;
  gap: 9px;
  max-width: min(860px, 100%);
  padding: 13px 14px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--bg-surface);
}

.message-block--user {
  justify-self: end;
  width: min(680px, 92%);
  background: color-mix(in srgb, var(--accent-soft), var(--bg-surface) 55%);
  border-color: color-mix(in srgb, var(--accent), transparent 68%);
}

.message-block--assistant {
  justify-self: start;
}

.message-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-muted);
  font-size: 12px;
}

.message-block__header strong {
  color: var(--text-main);
  font-family: var(--font-mono);
}

.message-block__content {
  min-width: 0;
}

.message-block__status {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.message-block__status--error,
.composer-error {
  color: var(--danger);
}

.reference-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 9px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  color: var(--accent);
  background: var(--bg-surface);
  font-size: 12px;
  font-weight: 720;
}

.reference-chip:hover {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.composer-dock {
  display: grid;
  gap: 10px;
  padding: 12px;
  border-top: 1px solid var(--line-soft);
  background: var(--bg-surface);
}

.composer-chips {
  display: grid;
  grid-template-columns: minmax(0, 220px) minmax(0, 1fr);
  gap: 8px;
}

.composer-chip {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.composer-chip span {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 720;
}

.composer-chip select {
  min-height: 32px;
}

.composer-input {
  min-height: 88px;
  background: color-mix(in srgb, var(--bg-surface), var(--bg-inset) 18%);
}

.composer-actions {
  align-items: center;
}

.composer-error {
  margin: 0;
  font-size: 13px;
}

.composer-buttons {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.recommendations {
  display: grid;
  gap: 8px;
  color: var(--text-muted);
  font-size: 13px;
}

.evidence-inspector {
  display: grid;
  align-content: start;
  gap: 12px;
  overflow: auto;
  padding: 14px;
}

.reference-detail {
  display: grid;
  gap: 12px;
}

.reference-detail h3 {
  margin: 0;
  font-size: 16px;
}

.reference-detail blockquote {
  margin: 0;
  padding: 12px;
  color: var(--text-muted);
  border-left: 3px solid var(--accent);
  border-radius: var(--radius-1);
  background: var(--accent-soft);
  line-height: 1.68;
}

.score-meter {
  display: grid;
  grid-template-columns: auto auto;
  gap: 7px 10px;
  align-items: center;
}

.score-meter span {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.score-meter strong {
  justify-self: end;
  font-family: var(--font-mono);
  font-size: 12px;
}

.score-meter div {
  grid-column: 1 / -1;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--bg-inset);
}

.score-meter i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--accent);
}

.reference-detail dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin: 0;
}

.reference-detail dl div {
  padding: 10px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background: var(--bg-inset);
}

.reference-detail dt {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
}

.reference-detail dd {
  margin: 4px 0 0;
  font-weight: 760;
}

.run-timeline {
  display: grid;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--line-soft);
}

.run-timeline h3 {
  margin: 0;
  font-size: 14px;
}

.run-step {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 9px;
}

.run-step > span {
  width: 9px;
  height: 9px;
  margin-top: 5px;
  border-radius: 999px;
  background: var(--accent);
  box-shadow: 0 0 0 5px var(--accent-soft);
}

.run-step p {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.5;
}

@media (max-width: 1240px) {
  .chat-workspace {
    grid-template-columns: 270px minmax(0, 1fr);
    height: auto;
    min-height: 0;
  }

  .evidence-inspector {
    grid-column: 1 / -1;
  }
}

@media (max-width: 860px) {
  .chat-workspace,
  .composer-chips {
    grid-template-columns: 1fr;
  }

  .conversation-row__actions {
    opacity: 1;
  }
}
</style>
