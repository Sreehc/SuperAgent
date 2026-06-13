<template>
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
        @click="$emit('create')"
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
        <button type="button" class="conversation-row__main" @click="$emit('select', conversation.id)">
          <strong v-if="chatStore.editingConversationId !== conversation.id">{{ conversation.title }}</strong>
          <input
            v-else
            :value="conversation.title"
            class="conversation-row__rename"
            type="text"
            @click.stop
            @keyup.enter="$emit('rename', conversation.id, ($event.target as HTMLInputElement).value)"
            @blur="$emit('rename', conversation.id, ($event.target as HTMLInputElement).value)"
          />
          <small>{{ formatTime(conversation.lastMessageAt) }}</small>
        </button>
        <div class="conversation-row__actions">
          <button class="btn-text" type="button" @click="chatStore.editingConversationId = conversation.id">重命名</button>
          <button class="btn-text" type="button" @click="$emit('archive', conversation.id)">归档</button>
          <button class="btn-text danger-text" type="button" @click="$emit('remove', conversation.id)">删除</button>
        </div>
      </article>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { PhPlus } from '@phosphor-icons/vue'
import { EmptyState, LoadingSpinner } from '../../../components'
import { useChatStore } from '../store/chat'
import { formatChatTime } from '../utils/presentation'

defineEmits<{
  create: []
  select: [sessionId: number]
  rename: [sessionId: number, title: string]
  archive: [sessionId: number]
  remove: [sessionId: number]
}>()

const chatStore = useChatStore()
const formatTime = formatChatTime
</script>
