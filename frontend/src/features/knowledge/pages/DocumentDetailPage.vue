<template>
  <section class="document-detail">
    <header class="card-shell detail-header">
      <div>
        <p class="eyebrow">/documents/{{ route.params.documentId }}</p>
        <h2>{{ knowledgeStore.selectedDocument?.title ?? '文档详情' }}</h2>
        <p>
          状态：{{ knowledgeStore.selectedDocument?.status ?? '-' }}
          · 类型：{{ knowledgeStore.selectedDocument?.fileType ?? '-' }}
          · 大小：{{ formatFileSize(knowledgeStore.selectedDocument?.fileSize ?? 0) }}
          · 切块：{{ knowledgeStore.selectedDocument?.chunkCount ?? 0 }}
        </p>
      </div>
      <button
        v-if="isAdmin"
        class="pill-button"
        type="button"
        :disabled="knowledgeStore.reprocessingDocument"
        @click="knowledgeStore.reprocessDocument('Manual reprocess from document detail')"
      >
        {{ knowledgeStore.reprocessingDocument ? '处理中...' : '重处理' }}
      </button>
    </header>

    <p v-if="knowledgeStore.errorMessage" class="error-banner">{{ knowledgeStore.errorMessage }}</p>
    <section v-if="knowledgeStore.loadingDocumentDetail" class="card-shell">正在加载文档详情...</section>

    <template v-else-if="knowledgeStore.selectedDocument">
      <section class="document-grid">
        <article class="card-shell">
          <h3>元数据</h3>
          <dl class="meta-list">
            <div>
              <dt>文件名</dt>
              <dd>{{ knowledgeStore.selectedDocument.fileName }}</dd>
            </div>
            <div>
              <dt>创建时间</dt>
              <dd>{{ formatTime(knowledgeStore.selectedDocument.createdAt) }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ formatTime(knowledgeStore.selectedDocument.updatedAt) }}</dd>
            </div>
            <div>
              <dt>错误摘要</dt>
              <dd>{{ knowledgeStore.selectedDocument.errorMessage || '-' }}</dd>
            </div>
          </dl>
        </article>

        <article class="card-shell">
          <h3>切块预览</h3>
          <div v-if="knowledgeStore.documentChunks.length === 0" class="empty-line">当前无切块数据。</div>
          <div v-for="chunk in knowledgeStore.documentChunks" :key="chunk.id" class="chunk-card">
            <strong>#{{ chunk.chunkNo }} {{ chunk.sectionTitle || '未命名章节' }}</strong>
            <p>{{ chunk.content }}</p>
            <small>{{ chunk.charCount }} chars</small>
          </div>
        </article>

        <article class="card-shell">
          <h3>任务日志</h3>
          <div v-if="knowledgeStore.documentTasks.length === 0" class="empty-line">当前无任务日志。</div>
          <div v-for="task in knowledgeStore.documentTasks" :key="task.id" class="task-card">
            <strong>{{ task.taskType }} · {{ task.status }}</strong>
            <p>attempt={{ task.attemptCount }}</p>
            <p>输入：{{ task.inputSummary || '-' }}</p>
            <p>输出：{{ task.outputSummary || '-' }}</p>
            <p v-if="task.errorMessage">错误：{{ task.errorMessage }}</p>
          </div>
        </article>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../auth/store/auth'
import { useKnowledgeStore } from '../store/knowledge'

const route = useRoute()
const authStore = useAuthStore()
const knowledgeStore = useKnowledgeStore()
const isAdmin = computed(() => ['OWNER', 'ADMIN'].includes(authStore.currentRole ?? ''))

onMounted(async () => {
  await loadDocument()
})

watch(
  () => route.params.documentId,
  async () => {
    await loadDocument()
  },
)

async function loadDocument() {
  const documentId = Number(route.params.documentId)
  if (!Number.isInteger(documentId) || documentId < 1) {
    return
  }
  await knowledgeStore.selectDocument(documentId)
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatFileSize(value: number) {
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}
</script>

<style scoped>
.document-detail,
.document-grid {
  display: grid;
  gap: 1rem;
}

.document-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.card-shell {
  border-radius: calc(var(--radius-md) + 4px);
  border: 1px solid var(--line-soft);
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
  padding: 1rem 1.2rem;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: center;
}

.detail-header h2 {
  margin: 0.2rem 0;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
}

.meta-list {
  display: grid;
  gap: 0.8rem;
}

.meta-list dt {
  color: var(--text-secondary);
}

.meta-list dd {
  margin: 0.2rem 0 0;
}

.chunk-card,
.task-card {
  padding: 0.8rem 0;
  border-bottom: 1px solid var(--line-soft);
}

.eyebrow {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.pill-button {
  border-radius: 999px;
  padding: 0.75rem 1rem;
  border: 0;
  background: linear-gradient(135deg, var(--bg-accent), #d78655);
  color: var(--text-contrast);
}

.empty-line,
.error-banner {
  color: var(--text-secondary);
}
</style>
