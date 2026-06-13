<template>
  <section class="feedback-page workspace-page">
    <header class="workspace-strip">
      <div class="workspace-title">
        <p class="section-label">Quality feedback</p>
        <h1>用户反馈</h1>
        <p>查看租户内回答点赞、点踩和纠错记录，用于评测集沉淀和质量回归。</p>
      </div>
      <div class="meta-row">
        <span class="metric-chip">total {{ total }}</span>
      </div>
    </header>

    <section class="filter-row">
      <label class="field feedback-filter">
        <span>类型</span>
        <select v-model="rating" @change="refresh">
          <option value="">全部</option>
          <option value="up">有帮助</option>
          <option value="down">不准确</option>
          <option value="correction">纠错</option>
        </select>
      </label>
      <button class="btn btn-secondary" type="button" @click="refresh">刷新</button>
    </section>

    <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>

    <LoadingSpinner v-if="loading" text="正在加载用户反馈..." />
    <EmptyState v-else-if="feedbacks.length === 0" variant="search" title="暂无反馈" description="当前筛选条件下没有反馈记录。" />
    <section v-else class="data-frame">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>类型</th>
            <th>会话 / 消息</th>
            <th>操作人</th>
            <th>内容</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="feedback in feedbacks" :key="feedback.id">
            <td class="mono">#{{ feedback.id }}</td>
            <td><span class="metric-chip">{{ ratingLabel(feedback.rating) }}</span></td>
            <td>
              <div class="feedback-link-stack">
                <RouterLink :to="`/chat/${feedback.sessionId}`">session #{{ feedback.sessionId }}</RouterLink>
                <span class="mono">message #{{ feedback.messageId }}</span>
                <RouterLink v-if="feedback.exchangeId" :to="`/traces/${feedback.exchangeId}`">trace #{{ feedback.exchangeId }}</RouterLink>
              </div>
            </td>
            <td class="mono">{{ feedback.actorUserId }}</td>
            <td>
              <p v-if="feedback.comment" class="feedback-copy">{{ feedback.comment }}</p>
              <p v-if="feedback.correction" class="feedback-copy">纠错：{{ feedback.correction }}</p>
              <span v-if="!feedback.comment && !feedback.correction" class="muted-text">无补充内容</span>
            </td>
            <td>{{ formatTime(feedback.updatedAt) }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="total > pageSize" class="pagination">
        <button class="btn btn-ghost btn-sm" type="button" :disabled="page <= 1" @click="goToPage(page - 1)">上一页</button>
        <span>第 {{ page }} 页 / 共 {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="page >= Math.ceil(total / pageSize)" @click="goToPage(page + 1)">下一页</button>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { EmptyState, LoadingSpinner } from '../../../components'
import { listAdminFeedbacks } from '../api'
import type { ConversationFeedback, FeedbackRating } from '../types'

const feedbacks = ref<ConversationFeedback[]>([])
const loading = ref(false)
const errorMessage = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const rating = ref<FeedbackRating | ''>('')

onMounted(fetchFeedbacks)

async function refresh() {
  page.value = 1
  await fetchFeedbacks()
}

async function fetchFeedbacks() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listAdminFeedbacks({
      page: page.value,
      pageSize: pageSize.value,
      rating: rating.value,
    })
    feedbacks.value = response.data.items
    total.value = response.data.total
  } catch {
    errorMessage.value = '用户反馈加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function goToPage(nextPage: number) {
  page.value = nextPage
  await fetchFeedbacks()
}

function ratingLabel(value: FeedbackRating) {
  if (value === 'up') {
    return '有帮助'
  }
  if (value === 'down') {
    return '不准确'
  }
  return '纠错'
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<style scoped>
.feedback-filter {
  width: min(220px, 100%);
}

.feedback-link-stack {
  display: grid;
  gap: 4px;
}

.feedback-copy {
  max-width: 420px;
  margin: 0;
  color: var(--text-muted);
  line-height: 1.55;
}

.muted-text {
  color: var(--text-subtle);
}
</style>
