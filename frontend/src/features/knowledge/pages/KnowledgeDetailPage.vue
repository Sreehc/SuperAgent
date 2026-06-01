<template>
  <section class="knowledge-detail">
    <header class="card-shell detail-header">
      <div>
        <p class="eyebrow">/knowledge/{{ route.params.knowledgeBaseId }}</p>
        <h2>{{ knowledgeStore.selectedKnowledgeBase?.name ?? '知识库详情' }}</h2>
        <p>{{ knowledgeStore.selectedKnowledgeBase?.description || '暂无描述' }}</p>
        <div class="header-meta">
          <span class="status-chip">{{ knowledgeStore.selectedKnowledgeBase?.status }}</span>
          <span>文档数：{{ knowledgeStore.selectedKnowledgeBase?.documentCount ?? 0 }}</span>
        </div>
      </div>
      <div v-if="isAdmin" class="header-actions">
        <button class="ghost-button" type="button" @click="knowledgeStore.publishKnowledgeBase">发布</button>
        <button class="ghost-button" type="button" @click="knowledgeStore.archiveKnowledgeBase">归档</button>
      </div>
    </header>

    <section v-if="isAdmin" class="card-shell upload-form">
      <div>
        <h3>上传文档</h3>
        <p>阶段 5 仅接通文件上传、MinIO 写入和初始任务落库。</p>
      </div>
      <form class="upload-form__grid" @submit.prevent="submitUpload">
        <input ref="fileInput" type="file" accept=".pdf,.doc,.docx,.ppt,.pptx,.md,.html,.txt" @change="onFileChange" />
        <input v-model="uploadTitle" type="text" placeholder="文档标题（可选）" />
        <input v-model="uploadCategory" type="text" placeholder="业务分类（可选）" />
        <input v-model="uploadTags" type="text" placeholder="标签，逗号分隔" />
        <button class="pill-button" type="submit" :disabled="knowledgeStore.uploadingDocument || !selectedFile">
          {{ knowledgeStore.uploadingDocument ? '上传中...' : '上传文档' }}
        </button>
      </form>
    </section>

    <section class="card-shell filters">
      <select v-model="knowledgeStore.documentStatusFilter" @change="knowledgeStore.refreshDocuments">
        <option value="">全部状态</option>
        <option value="uploaded">uploaded</option>
        <option value="ready">ready</option>
        <option value="failed">failed</option>
      </select>
      <select v-model="knowledgeStore.documentTypeFilter" @change="knowledgeStore.refreshDocuments">
        <option value="">全部类型</option>
        <option value="pdf">pdf</option>
        <option value="md">md</option>
        <option value="docx">docx</option>
        <option value="txt">txt</option>
      </select>
      <input v-model="knowledgeStore.tagFilter" type="search" placeholder="按标签筛选" @keyup.enter="knowledgeStore.refreshDocuments" />
      <button class="ghost-button" type="button" @click="knowledgeStore.refreshDocuments">刷新</button>
    </section>

    <p v-if="knowledgeStore.errorMessage" class="error-banner">{{ knowledgeStore.errorMessage }}</p>

    <section v-if="knowledgeStore.loadingDocuments" class="card-shell">正在加载文档列表...</section>
    <section v-else class="card-shell">
      <table class="table">
        <thead>
          <tr>
            <th>文档名</th>
            <th>类型</th>
            <th>状态</th>
            <th>大小</th>
            <th>切块数</th>
            <th>更新时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="knowledgeStore.documents.length === 0">
            <td colspan="6">暂无文档。</td>
          </tr>
          <tr v-for="document in knowledgeStore.documents" :key="document.id">
            <td>{{ document.title }}</td>
            <td>{{ document.fileType }}</td>
            <td><span class="status-chip">{{ document.status }}</span></td>
            <td>{{ formatFileSize(document.fileSize) }}</td>
            <td>{{ document.chunkCount }}</td>
            <td>{{ formatTime(document.updatedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../auth/store/auth'
import { useKnowledgeStore } from '../store/knowledge'

const route = useRoute()
const authStore = useAuthStore()
const knowledgeStore = useKnowledgeStore()
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const uploadTitle = ref('')
const uploadCategory = ref('')
const uploadTags = ref('')

const isAdmin = computed(() => ['OWNER', 'ADMIN'].includes(authStore.currentRole ?? ''))

onMounted(async () => {
  await loadCurrentKnowledgeBase()
})

watch(
  () => route.params.knowledgeBaseId,
  async () => {
    await loadCurrentKnowledgeBase()
  },
)

async function loadCurrentKnowledgeBase() {
  const knowledgeBaseId = Number(route.params.knowledgeBaseId)
  if (!Number.isInteger(knowledgeBaseId) || knowledgeBaseId < 1) {
    return
  }
  await knowledgeStore.selectKnowledgeBase(knowledgeBaseId)
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  selectedFile.value = target.files?.[0] ?? null
}

async function submitUpload() {
  if (!selectedFile.value) {
    return
  }
  await knowledgeStore.uploadDocument({
    file: selectedFile.value,
    title: uploadTitle.value,
    category: uploadCategory.value,
    tags: uploadTags.value,
  })
  selectedFile.value = null
  uploadTitle.value = ''
  uploadCategory.value = ''
  uploadTags.value = ''
  if (fileInput.value) {
    fileInput.value.value = ''
  }
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
.knowledge-detail {
  display: grid;
  gap: 1rem;
}

.card-shell {
  border-radius: calc(var(--radius-md) + 4px);
  border: 1px solid var(--line-soft);
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
  padding: 1rem 1.2rem;
}

.detail-header,
.header-actions,
.filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.detail-header h2,
.upload-form h3 {
  margin: 0.2rem 0;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
}

.header-meta {
  display: flex;
  gap: 0.8rem;
  flex-wrap: wrap;
  color: var(--text-secondary);
}

.upload-form__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0.8rem;
  margin-top: 0.8rem;
}

.filters input,
.filters select,
.upload-form__grid input {
  padding: 0.8rem 0.95rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.84);
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.table th,
.table td {
  padding: 0.95rem 0.75rem;
  border-bottom: 1px solid var(--line-soft);
  text-align: left;
}

.eyebrow {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.status-chip {
  display: inline-flex;
  padding: 0.2rem 0.65rem;
  border-radius: 999px;
  background: rgba(27, 47, 61, 0.08);
}

.pill-button,
.ghost-button {
  border-radius: 999px;
  padding: 0.75rem 1rem;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.78);
}

.pill-button {
  border: 0;
  background: linear-gradient(135deg, var(--bg-accent), #d78655);
  color: var(--text-contrast);
}

.error-banner {
  margin: 0;
  color: var(--danger);
}

@media (max-width: 1100px) {
  .detail-header,
  .header-actions,
  .filters,
  .upload-form__grid {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
