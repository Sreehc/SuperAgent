<template>
  <section ref="region" class="message-region" aria-live="polite">
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
          <div class="message-block__meta">
            <button class="message-copy-button" type="button" @click="copyMessage(message.content)">复制</button>
            <span>{{ formatTime(message.createdAt) }}</span>
          </div>
        </header>
        <div
          class="message-block__content markdown-body"
          @click="copyCodeBlock"
          v-html="renderMarkdown(message.content || '...')"
        />
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
        <div v-if="message.role === 'assistant' && message.id > 0" class="message-feedback" aria-label="回答反馈">
          <button
            class="feedback-chip"
            :class="{ 'feedback-chip--active': message.feedback?.rating === 'up' }"
            type="button"
            :disabled="chatStore.updatingFeedbackMessageId === message.id"
            @click="chatStore.setMessageFeedback(message, 'up')"
          >
            有帮助
          </button>
          <button
            class="feedback-chip"
            :class="{ 'feedback-chip--active feedback-chip--down': message.feedback?.rating === 'down' }"
            type="button"
            :disabled="chatStore.updatingFeedbackMessageId === message.id"
            @click="chatStore.setMessageFeedback(message, 'down')"
          >
            不准确
          </button>
          <button
            class="feedback-chip"
            :class="{ 'feedback-chip--active feedback-chip--correction': message.feedback?.rating === 'correction' }"
            type="button"
            :disabled="chatStore.updatingFeedbackMessageId === message.id"
            @click="chatStore.correctMessageFeedback(message)"
          >
            纠错
          </button>
          <button
            v-if="message.feedback"
            class="feedback-chip feedback-chip--ghost"
            type="button"
            :disabled="chatStore.updatingFeedbackMessageId === message.id"
            @click="chatStore.clearMessageFeedback(message)"
          >
            清除
          </button>
        </div>
        <p v-if="message.feedback?.correction" class="message-feedback__note">纠错：{{ message.feedback.correction }}</p>
        <div v-if="isAdmin && message.role === 'assistant' && chatStore.streamState.exchangeId" class="chip-row">
          <button class="reference-chip" type="button" @click="$emit('open-trace', chatStore.streamState.exchangeId)">查看 Trace</button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { EmptyState, LoadingSpinner } from '../../../components'
import { useAuthStore } from '../../auth/store/auth'
import { useChatStore } from '../store/chat'
import { formatChatTime, roleLabel } from '../utils/presentation'
import { renderMarkdown } from '../utils/renderMarkdown'

defineEmits<{ 'open-trace': [exchangeId: number] }>()

const chatStore = useChatStore()
const authStore = useAuthStore()
const region = ref<HTMLElement | null>(null)
const isAdmin = computed(() => ['OWNER', 'ADMIN'].includes(authStore.currentRole ?? ''))
const formatTime = formatChatTime

watch(
  () => chatStore.messages.map((message) => `${message.id}:${message.content.length}`).join('|'),
  async () => {
    await nextTick()
    region.value?.scrollTo({ top: region.value.scrollHeight, behavior: chatStore.streaming ? 'smooth' : 'auto' })
  },
)

async function copyMessage(content: string) {
  await copyText(content)
}

async function copyCodeBlock(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof HTMLElement) || !target.matches('[data-code-copy]')) {
    return
  }

  const block = target.closest('.markdown-codeblock')
  const code = block?.querySelector('code')?.textContent ?? ''
  await copyText(code)
  target.textContent = '已复制'
  window.setTimeout(() => {
    target.textContent = '复制代码'
  }, 1200)
}

async function copyText(content: string) {
  const text = content.trim()
  if (!text) {
    return
  }

  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'true')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  textarea.remove()
}
</script>
