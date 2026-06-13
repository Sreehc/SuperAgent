<template>
  <section class="chat-workspace">
    <SessionRail
      @create="newConversation"
      @select="selectConversation"
      @rename="chatStore.renameConversation"
      @archive="archiveConversation"
      @remove="removeConversation"
    />
    <ConversationSurface @send="sendMessage" @open-trace="openTrace" />
    <EvidenceInspector @open-document="openDocument" @open-trace="openTrace" />
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ConversationSurface, EvidenceInspector, SessionRail } from '../components'
import { useChatStore } from '../store/chat'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()

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

async function openDocument(documentId: number) {
  await router.push(`/documents/${documentId}`)
}

async function openTrace(exchangeId: number) {
  await router.push(`/traces/${exchangeId}`)
}
</script>

<style>
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
  content-visibility: auto;
  contain-intrinsic-size: auto 160px;
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

.message-block__meta {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.message-copy-button {
  min-height: 24px;
  padding: 0 8px;
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

.message-feedback {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  padding-top: 2px;
}

.feedback-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 9px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  color: var(--text-muted);
  background: var(--bg-inset);
  font-size: 12px;
  font-weight: 760;
  transition:
    color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard),
    border-color var(--duration-fast) var(--ease-standard);
}

.feedback-chip:hover:not(:disabled),
.feedback-chip:focus-visible {
  color: var(--text-main);
  border-color: color-mix(in srgb, var(--accent), transparent 40%);
  background: var(--accent-soft);
}

.feedback-chip--active {
  color: var(--accent);
  border-color: color-mix(in srgb, var(--accent), transparent 35%);
  background: var(--accent-soft);
}

.feedback-chip--down {
  color: var(--warning);
  border-color: color-mix(in srgb, var(--warning), transparent 35%);
  background: color-mix(in srgb, var(--warning), transparent 90%);
}

.feedback-chip--correction {
  color: var(--success);
  border-color: color-mix(in srgb, var(--success), transparent 35%);
  background: color-mix(in srgb, var(--success), transparent 90%);
}

.feedback-chip--ghost {
  color: var(--text-subtle);
  background: transparent;
}

.message-feedback__note {
  margin: 0;
  padding: 8px 10px;
  color: var(--text-muted);
  border: 1px dashed var(--line-soft);
  border-radius: var(--radius-1);
  background: var(--bg-inset);
  font-size: 12px;
  line-height: 1.5;
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
  grid-template-columns: minmax(0, 150px) minmax(0, 220px) minmax(0, 1fr);
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

.tool-capability-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.tool-capability-strip > span {
  font-weight: 720;
}

.tool-capability {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 26px;
  padding: 0 8px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  color: var(--text-strong);
  background: var(--bg-inset);
  font-size: 12px;
  font-weight: 720;
}

.tool-capability small {
  color: var(--success);
  font-size: 11px;
}

.tool-capability--risk {
  border-color: color-mix(in srgb, var(--warning), transparent 35%);
}

.tool-capability--blocked {
  color: var(--text-muted);
}

.tool-capability--blocked small {
  color: var(--danger);
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
