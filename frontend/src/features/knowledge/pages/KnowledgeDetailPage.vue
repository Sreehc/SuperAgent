<template>
  <section class="knowledge-detail page-stack">
    <header class="page-header">
      <div>
        <p class="page-kicker">/knowledge/{{ route.params.knowledgeBaseId }}</p>
        <h2>{{ knowledgeStore.selectedKnowledgeBase?.name ?? '知识库详情' }}</h2>
        <p>{{ knowledgeStore.selectedKnowledgeBase?.description || '管理知识库状态、上传文档并查看处理结果。' }}</p>
        <div class="meta-row">
          <span class="badge" :class="statusClass(knowledgeStore.selectedKnowledgeBase?.status)">{{ statusLabel(knowledgeStore.selectedKnowledgeBase?.status) }}</span>
          <span class="badge">文档 {{ knowledgeStore.selectedKnowledgeBase?.documentCount ?? 0 }}</span>
        </div>
      </div>
      <div v-if="isAdmin" class="header-actions">
        <button class="btn btn-ghost btn-sm" type="button" @click="editing = !editing">{{ editing ? '取消编辑' : '编辑' }}</button>
        <button class="btn btn-secondary btn-sm" type="button" @click="knowledgeStore.publishKnowledgeBase">发布</button>
        <button class="btn btn-secondary btn-sm" type="button" @click="knowledgeStore.archiveKnowledgeBase">归档</button>
        <button class="btn btn-danger btn-sm" type="button" @click="removeKnowledgeBase">删除</button>
      </div>
    </header>

    <section v-if="isAdmin && editing && knowledgeStore.selectedKnowledgeBase" class="panel edit-panel">
      <div>
        <h3>编辑知识库</h3>
        <p>更新名称和描述，不改变文档处理状态。</p>
      </div>
      <form class="edit-form" @submit.prevent="saveKnowledgeBase">
        <label class="field">
          <span>名称</span>
          <input v-model="editName" type="text" placeholder="知识库名称" />
        </label>
        <label class="field">
          <span>描述</span>
          <input v-model="editDescription" type="text" placeholder="知识库描述" />
        </label>
        <button class="btn btn-primary" type="submit" :disabled="knowledgeStore.savingKnowledgeBase || !editName.trim()">
          {{ knowledgeStore.savingKnowledgeBase ? '保存中...' : '保存修改' }}
        </button>
      </form>
    </section>

    <section v-if="isAdmin" class="panel upload-panel">
      <div class="panel-heading">
        <div>
          <h3>上传文档</h3>
          <p>上传后进入解析、切块和图谱处理链路。</p>
        </div>
        <span class="badge">域 {{ knowledgeStore.knowledgeDomains.length }} / 策略 {{ knowledgeStore.chunkingProfiles.length }}</span>
      </div>
      <form class="upload-grid" @submit.prevent="submitUpload">
        <label class="field file-field">
          <span>文件</span>
          <input ref="fileInput" data-testid="document-upload-file" type="file" accept=".pdf,.doc,.docx,.ppt,.pptx,.md,.html,.txt" @change="onFileChange" />
        </label>
        <label class="field"><span>标题</span><input v-model="uploadTitle" type="text" placeholder="可选" /></label>
        <label class="field"><span>分类</span><input v-model="uploadCategory" type="text" placeholder="可选" /></label>
        <label class="field"><span>标签</span><input v-model="uploadTags" type="text" placeholder="逗号分隔" /></label>
        <label class="field">
          <span>知识域</span>
          <select v-model="uploadKnowledgeDomainId">
            <option value="">不绑定知识域</option>
            <option v-for="domain in knowledgeStore.knowledgeDomains" :key="domain.id" :value="`${domain.id}`">{{ domain.name }} / {{ domain.code }}</option>
          </select>
        </label>
        <label class="field">
          <span>切块策略</span>
          <select v-model="uploadChunkingProfileId">
            <option value="">默认策略</option>
            <option v-for="profile in knowledgeStore.chunkingProfiles" :key="profile.id" :value="`${profile.id}`">{{ profile.name }} / {{ profile.strategy }}</option>
          </select>
        </label>
        <button class="btn btn-primary upload-submit" data-testid="document-upload-submit" type="submit" :disabled="knowledgeStore.uploadingDocument || !selectedFile">
          {{ knowledgeStore.uploadingDocument ? '上传中...' : '上传文档' }}
        </button>
      </form>
    </section>

    <section class="toolbar">
      <label class="field toolbar-field">
        <span>文档状态</span>
        <select v-model="knowledgeStore.documentStatusFilter" @change="knowledgeStore.refreshDocuments">
          <option value="">全部状态</option>
          <option value="uploaded">已上传</option>
          <option value="ready">就绪</option>
          <option value="failed">失败</option>
        </select>
      </label>
      <label class="field toolbar-field">
        <span>文件类型</span>
        <select v-model="knowledgeStore.documentTypeFilter" @change="knowledgeStore.refreshDocuments">
          <option value="">全部类型</option>
          <option value="pdf">PDF</option>
          <option value="md">Markdown</option>
          <option value="docx">Word</option>
          <option value="txt">纯文本</option>
        </select>
      </label>
      <label class="field toolbar-field">
        <span>标签</span>
        <input v-model="knowledgeStore.tagFilter" type="search" placeholder="按标签筛选" @keyup.enter="knowledgeStore.refreshDocuments" />
      </label>
      <button class="btn btn-secondary" type="button" @click="knowledgeStore.refreshDocuments">刷新</button>
    </section>

    <p v-if="knowledgeStore.errorMessage" class="error-banner">{{ knowledgeStore.errorMessage }}</p>

    <LoadingSpinner v-if="knowledgeStore.loadingDocuments" text="正在加载文档列表..." />
    <section v-else class="table-wrap">
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
          <tr v-if="knowledgeStore.documents.length === 0"><td colspan="6">暂无文档。</td></tr>
          <tr
            v-for="document in knowledgeStore.documents"
            :key="document.id"
            class="table-row"
            :data-testid="`document-row-${document.id}`"
            @click="openDocument(document.id)"
          >
            <td><strong>{{ document.title }}</strong></td>
            <td>{{ fileTypeLabel(document.fileType) }}</td>
            <td><span class="badge" :class="statusClass(document.status)">{{ documentStatusLabel(document.status) }}</span></td>
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
import { useRoute, useRouter } from 'vue-router'
import { LoadingSpinner } from '../../../components'
import { useAuthStore } from '../../auth/store/auth'
import { useKnowledgeStore } from '../store/knowledge'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const knowledgeStore = useKnowledgeStore()
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const uploadTitle = ref('')
const uploadCategory = ref('')
const uploadTags = ref('')
const uploadKnowledgeDomainId = ref('')
const uploadChunkingProfileId = ref('')
const editing = ref(false)
const editName = ref('')
const editDescription = ref('')

const isAdmin = computed(() => ['OWNER', 'ADMIN'].includes(authStore.currentRole ?? ''))

onMounted(async () => {
  await Promise.all([loadCurrentKnowledgeBase(), loadGovernanceOptions()])
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
  editName.value = knowledgeStore.selectedKnowledgeBase?.name ?? ''
  editDescription.value = knowledgeStore.selectedKnowledgeBase?.description ?? ''
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
    knowledgeDomainId: parseSelectedId(uploadKnowledgeDomainId.value),
    chunkingProfileId: parseSelectedId(uploadChunkingProfileId.value),
  })
  selectedFile.value = null
  uploadTitle.value = ''
  uploadCategory.value = ''
  uploadTags.value = ''
  uploadKnowledgeDomainId.value = ''
  uploadChunkingProfileId.value = ''
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

async function openDocument(documentId: number) {
  await router.push(`/documents/${documentId}`)
}

async function saveKnowledgeBase() {
  await knowledgeStore.saveKnowledgeBase({
    name: editName.value.trim(),
    description: editDescription.value.trim() || undefined,
  })
  editing.value = false
}

async function removeKnowledgeBase() {
  if (!window.confirm('删除知识库后无法恢复，确认继续吗？')) {
    return
  }
  const deleted = await knowledgeStore.removeKnowledgeBase()
  if (deleted) {
    await router.push('/knowledge')
  }
}

async function loadGovernanceOptions() {
  if (!isAdmin.value) {
    return
  }
  await knowledgeStore.fetchGovernanceOptions()
}

function parseSelectedId(value: string) {
  if (!value) {
    return null
  }
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
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

function statusLabel(status?: string) {
  const map: Record<string, string> = {
    draft: '草稿',
    published: '已发布',
    archived: '已归档',
  }
  return status ? (map[status] ?? status) : '-'
}

function documentStatusLabel(status?: string) {
  return status ?? '-'
}

function statusClass(status?: string) {
  if (status === 'published' || status === 'ready') {
    return 'badge--success'
  }
  if (status === 'failed') {
    return 'badge--danger'
  }
  if (status === 'archived' || status === 'processing' || status === 'pending') {
    return 'badge--warning'
  }
  return 'badge--accent'
}

function fileTypeLabel(type?: string) {
  const map: Record<string, string> = {
    pdf: 'PDF',
    md: 'Markdown',
    docx: 'Word',
    doc: 'Word',
    pptx: 'PPT',
    ppt: 'PPT',
    txt: '纯文本',
    html: 'HTML',
  }
  return type ? (map[type] ?? type.toUpperCase()) : '-'
}
</script>

<style scoped>
.meta-row,
.header-actions,
.panel-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.meta-row {
  margin-top: 10px;
}

.header-actions {
  justify-content: flex-end;
}

.edit-panel,
.upload-panel {
  display: grid;
  gap: 14px;
}

.panel-heading {
  justify-content: space-between;
}

.panel-heading h3,
.edit-panel h3 {
  margin: 0;
}

.panel-heading p,
.edit-panel p {
  margin: 6px 0 0;
}

.edit-form {
  display: grid;
  grid-template-columns: minmax(180px, 260px) minmax(220px, 1fr) auto;
  align-items: end;
  gap: 10px;
}

.upload-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  align-items: end;
}

.file-field,
.upload-submit {
  grid-column: span 2;
}

.toolbar-field {
  width: min(220px, 100%);
}

.table-row {
  cursor: pointer;
}

@media (max-width: 1120px) {
  .edit-form,
  .upload-grid {
    grid-template-columns: 1fr 1fr;
  }

  .file-field,
  .upload-submit {
    grid-column: auto;
  }
}

@media (max-width: 760px) {
  .edit-form,
  .upload-grid {
    grid-template-columns: 1fr;
  }
}
</style>
