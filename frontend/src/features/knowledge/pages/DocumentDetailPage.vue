<template>
  <section class="document-debugger workspace-page">
    <header class="workspace-strip">
      <div class="workspace-title">
        <p class="section-label">Parser debugger</p>
        <h1>{{ knowledgeStore.selectedDocument?.title ?? '文档详情' }}</h1>
        <p>
          {{ docStatusLabel(knowledgeStore.selectedDocument?.status) }} / {{ fileTypeLabel(knowledgeStore.selectedDocument?.fileType) }} /
          {{ formatFileSize(knowledgeStore.selectedDocument?.fileSize ?? 0) }} / 切块 {{ knowledgeStore.selectedDocument?.chunkCount ?? 0 }}
        </p>
        <div class="meta-row">
          <span class="metric-chip">v{{ knowledgeStore.selectedDocument?.activeVersionNo ?? '-' }}</span>
          <span class="metric-chip">文档图谱 {{ documentGraphStatus }}</span>
          <span class="metric-chip">版本图谱 {{ versionGraphStatus }}</span>
        </div>
      </div>
      <div v-if="isAdmin" class="action-row document-actions">
        <button class="btn btn-ghost btn-sm" type="button" @click="editingMetadata = !editingMetadata">{{ editingMetadata ? '取消编辑' : '编辑元数据' }}</button>
        <select v-model="reprocessChunkingProfileId">
          <option value="">沿用当前切块策略</option>
          <option v-for="profile in knowledgeStore.chunkingProfiles" :key="profile.id" :value="`${profile.id}`">{{ profile.name }} / {{ profile.strategy }}</option>
        </select>
        <button class="btn btn-primary btn-sm" type="button" :disabled="knowledgeStore.reprocessingDocument" @click="triggerReprocess">
          {{ knowledgeStore.reprocessingDocument ? '处理中...' : '重处理' }}
        </button>
        <button class="btn btn-secondary btn-sm" type="button" :disabled="knowledgeStore.rebuildingDocumentGraph" @click="knowledgeStore.rebuildCurrentDocumentGraph">
          {{ knowledgeStore.rebuildingDocumentGraph ? '重建中...' : '重建图谱' }}
        </button>
        <button class="btn btn-danger btn-sm" type="button" :disabled="knowledgeStore.deletingDocument" @click="deleteDocument">
          {{ knowledgeStore.deletingDocument ? '删除中...' : '删除文档' }}
        </button>
      </div>
    </header>

    <p v-if="knowledgeStore.errorMessage" class="error-banner">{{ knowledgeStore.errorMessage }}</p>
    <LoadingSpinner v-if="knowledgeStore.loadingDocumentDetail" text="正在加载文档详情..." />

    <template v-else-if="knowledgeStore.selectedDocument">
      <section class="debugger-layout">
        <main class="reader-pane">
          <article class="parsed-reader">
            <div class="pane-heading">
              <div>
                <p class="section-label">Primary text</p>
                <h2>解析文本</h2>
              </div>
              <span class="metric-chip">{{ knowledgeStore.selectedDocument.parsedText?.length ?? 0 }} chars</span>
            </div>
            <div v-if="!knowledgeStore.selectedDocument.parsedText" class="empty-line">当前无解析文本。</div>
            <pre v-else class="parsed-text">{{ knowledgeStore.selectedDocument.parsedText }}</pre>
          </article>

          <article class="chunks-pane">
            <div class="pane-heading">
              <div>
                <p class="section-label">Chunks</p>
                <h2>切块预览</h2>
              </div>
              <span class="metric-chip">{{ knowledgeStore.documentChunks.length }}</span>
            </div>
            <div v-if="knowledgeStore.documentChunks.length === 0" class="empty-line">当前无切块数据。</div>
            <div v-for="chunk in knowledgeStore.documentChunks" :key="chunk.id" class="debug-card">
              <strong>#{{ chunk.chunkNo }} {{ chunk.sectionTitle || '未命名章节' }}</strong>
              <p>{{ chunk.content }}</p>
              <small>{{ chunk.charCount }} 字</small>
            </div>
          </article>
        </main>

        <aside class="document-inspector inspector-box">
          <article v-if="isAdmin && editingMetadata" class="inspector-section">
            <p class="section-label">Metadata editor</p>
            <h3>编辑元数据</h3>
            <form class="metadata-form" @submit.prevent="saveMetadata">
              <label class="field"><span>标题</span><input v-model="metadataForm.title" type="text" /></label>
              <label class="field"><span>分类</span><input v-model="metadataForm.category" type="text" /></label>
              <label class="field"><span>标签</span><input v-model="metadataForm.tags" type="text" placeholder="逗号分隔" /></label>
              <label class="field">
                <span>知识域</span>
                <select v-model="metadataForm.knowledgeDomainId">
                  <option value="">不绑定知识域</option>
                  <option v-for="domain in knowledgeStore.knowledgeDomains" :key="domain.id" :value="`${domain.id}`">{{ domain.name }} / {{ domain.code }}</option>
                </select>
              </label>
              <label class="field">
                <span>切块策略</span>
                <select v-model="metadataForm.chunkingProfileId">
                  <option value="">默认策略</option>
                  <option v-for="profile in knowledgeStore.chunkingProfiles" :key="profile.id" :value="`${profile.id}`">{{ profile.name }} / {{ profile.strategy }}</option>
                </select>
              </label>
              <button class="btn btn-primary btn-sm" type="submit" :disabled="knowledgeStore.savingDocumentMetadata || !metadataForm.title.trim()">
                {{ knowledgeStore.savingDocumentMetadata ? '保存中...' : '保存元数据' }}
              </button>
            </form>
          </article>

          <article class="inspector-section">
            <p class="section-label">Metadata</p>
            <h3>元数据</h3>
            <dl class="meta-list">
              <div><dt>分类</dt><dd>{{ metadataValue('category') }}</dd></div>
              <div><dt>标签</dt><dd>{{ metadataTagsLabel }}</dd></div>
              <div><dt>知识域</dt><dd>{{ knowledgeDomainLabel }}</dd></div>
              <div><dt>切块策略</dt><dd>{{ chunkingProfileLabel }}</dd></div>
              <div><dt>当前版本</dt><dd>v{{ knowledgeStore.selectedDocument.activeVersionNo }}</dd></div>
              <div><dt>文件名</dt><dd>{{ knowledgeStore.selectedDocument.fileName }}</dd></div>
              <div><dt>创建时间</dt><dd>{{ formatTime(knowledgeStore.selectedDocument.createdAt) }}</dd></div>
              <div><dt>更新时间</dt><dd>{{ formatTime(knowledgeStore.selectedDocument.updatedAt) }}</dd></div>
              <div><dt>错误摘要</dt><dd>{{ knowledgeStore.selectedDocument.errorMessage || '-' }}</dd></div>
            </dl>
          </article>

          <article class="inspector-section">
            <p class="section-label">Pipeline</p>
            <h3>任务日志</h3>
            <div v-if="knowledgeStore.documentTasks.length === 0" class="empty-line">当前无任务日志。</div>
            <div v-for="task in knowledgeStore.documentTasks" :key="task.id" class="task-event" :class="statusClass(task.status)">
              <span></span>
              <div>
                <strong>{{ taskLabel(task.taskType, task.status) }}</strong>
                <p>attempt={{ task.attemptCount }}</p>
                <p>输入：{{ task.inputSummary || '-' }}</p>
                <p>输出：{{ task.outputSummary || '-' }}</p>
                <p v-if="task.errorMessage">错误：{{ task.errorMessage }}</p>
              </div>
            </div>
          </article>

          <article class="inspector-section">
            <p class="section-label">Versions</p>
            <h3>版本列表</h3>
            <div v-if="knowledgeStore.documentVersions.length === 0" class="empty-line">当前无版本记录。</div>
            <div v-for="version in knowledgeStore.documentVersions" :key="version.id" class="debug-card">
              <div class="item-head"><strong>v{{ version.versionNo }}</strong><span class="badge" :class="statusClass(version.status)">{{ docStatusLabel(version.status) }}</span></div>
              <p>切块策略：{{ profileName(version.chunkingProfileId) }}</p>
              <p>切块数：{{ version.chunkCount }} / 图谱同步：{{ version.graphSyncStatus }}</p>
              <p>更新时间：{{ formatTime(version.updatedAt) }}</p>
            </div>
          </article>

          <article class="inspector-section">
            <div class="pane-heading">
              <div>
                <p class="section-label">Graph</p>
                <h3>文档图谱</h3>
              </div>
              <div class="meta-row"><span class="metric-chip">节点 {{ graphNodeCount }}</span><span class="metric-chip">边 {{ graphEdgeCount }}</span></div>
            </div>
            <LoadingSpinner v-if="knowledgeStore.loadingDocumentGraph" text="正在加载图谱..." />
            <div v-else-if="!knowledgeStore.documentGraph" class="empty-line">当前无图谱数据。文档可能尚未完成图谱同步，或当前策略未启用图谱生成。</div>
            <template v-else>
              <div class="graph-summary">
                <div><strong>节点类型</strong><p>{{ nodeTypeSummary }}</p></div>
                <div><strong>边类型</strong><p>{{ edgeTypeSummary }}</p></div>
              </div>
              <details class="graph-details">
                <summary>查看节点与关系</summary>
                <div>
                  <h4>节点</h4>
                  <div v-for="node in knowledgeStore.documentGraph.nodes" :key="node.id" class="debug-card">
                    <strong>{{ node.type }} / {{ node.label }}</strong>
                    <p>{{ formatMetadata(node.metadata) }}</p>
                  </div>
                  <h4>关系</h4>
                  <div v-for="edge in knowledgeStore.documentGraph.edges" :key="`${edge.sourceId}-${edge.targetId}-${edge.type}`" class="debug-card">
                    <strong>{{ edge.type }}</strong>
                    <p>{{ edge.sourceId }} -> {{ edge.targetId }}</p>
                    <p>{{ formatMetadata(edge.metadata) }}</p>
                  </div>
                </div>
              </details>
            </template>
          </article>
        </aside>
      </section>
    </template>
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
const isAdmin = computed(() => ['OWNER', 'ADMIN'].includes(authStore.currentRole ?? ''))
const reprocessChunkingProfileId = ref('')
const editingMetadata = ref(false)
const metadataForm = ref({
  title: '',
  category: '',
  tags: '',
  knowledgeDomainId: '',
  chunkingProfileId: '',
})

const knowledgeDomainLabel = computed(() => {
  const domainId = knowledgeStore.selectedDocument?.knowledgeDomainId ?? null
  if (!domainId) {
    return '未绑定'
  }
  const domain = knowledgeStore.knowledgeDomains.find((item) => item.id === domainId)
  return domain ? `${domain.name} / ${domain.code}` : `#${domainId}`
})

const chunkingProfileLabel = computed(() => profileName(knowledgeStore.selectedDocument?.chunkingProfileId ?? null))
const documentGraphStatus = computed(() => knowledgeStore.documentGraph?.documentGraphSyncStatus ?? metadataValue('graphSyncStatus'))
const versionGraphStatus = computed(() => knowledgeStore.documentGraph?.versionGraphSyncStatus ?? '-')
const graphNodeCount = computed(() => knowledgeStore.documentGraph?.nodes.length ?? 0)
const graphEdgeCount = computed(() => knowledgeStore.documentGraph?.edges.length ?? 0)
const nodeTypeSummary = computed(() => summarizeTypes(knowledgeStore.documentGraph?.nodes.map((node) => node.type) ?? []))
const edgeTypeSummary = computed(() => summarizeTypes(knowledgeStore.documentGraph?.edges.map((edge) => edge.type) ?? []))
const metadataTagsLabel = computed(() => {
  const value = knowledgeStore.selectedDocument?.metadata.tags
  return Array.isArray(value) && value.length > 0 ? value.join(', ') : '-'
})

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
  await Promise.all([
    knowledgeStore.selectDocument(documentId),
    isAdmin.value ? knowledgeStore.fetchGovernanceOptions() : Promise.resolve(),
  ])
  reprocessChunkingProfileId.value = ''
  applyMetadataForm()
}

async function triggerReprocess() {
  await knowledgeStore.reprocessDocument({
    reason: 'Manual reprocess from document detail',
    chunkingProfileId: parseSelectedId(reprocessChunkingProfileId.value),
  })
}

async function deleteDocument() {
  const knowledgeBaseId = knowledgeStore.selectedDocument?.knowledgeBaseId
  if (!knowledgeBaseId || !window.confirm('确认删除该文档？删除后该文档不会继续参与检索。')) {
    return
  }
  const deleted = await knowledgeStore.removeCurrentDocument()
  if (deleted) {
    await router.push(`/knowledge/${knowledgeBaseId}`)
  }
}

async function saveMetadata() {
  await knowledgeStore.saveDocumentMetadata({
    title: metadataForm.value.title.trim(),
    category: metadataForm.value.category.trim(),
    tags: metadataForm.value.tags.trim(),
    knowledgeDomainId: parseSelectedId(metadataForm.value.knowledgeDomainId),
    chunkingProfileId: parseSelectedId(metadataForm.value.chunkingProfileId),
  })
  applyMetadataForm()
  editingMetadata.value = false
}

function applyMetadataForm() {
  const document = knowledgeStore.selectedDocument
  if (!document) {
    return
  }
  const tags = document.metadata.tags
  metadataForm.value = {
    title: document.title,
    category: typeof document.metadata.category === 'string' ? document.metadata.category : '',
    tags: Array.isArray(tags) ? tags.join(', ') : '',
    knowledgeDomainId: document.knowledgeDomainId ? `${document.knowledgeDomainId}` : '',
    chunkingProfileId: document.chunkingProfileId ? `${document.chunkingProfileId}` : '',
  }
}

function profileName(profileId: number | null) {
  if (!profileId) {
    return '默认/未绑定'
  }
  const profile = knowledgeStore.chunkingProfiles.find((item) => item.id === profileId)
  return profile ? `${profile.name} / ${profile.strategy}` : `#${profileId}`
}

function parseSelectedId(value: string) {
  if (!value) {
    return null
  }
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function summarizeTypes(values: string[]) {
  if (values.length === 0) {
    return '暂无'
  }
  const counts = values.reduce<Record<string, number>>((accumulator, value) => {
    accumulator[value] = (accumulator[value] ?? 0) + 1
    return accumulator
  }, {})
  return Object.entries(counts)
    .map(([key, count]) => `${key} x ${count}`)
    .join(', ')
}

function formatMetadata(metadata: Record<string, unknown>) {
  const entries = Object.entries(metadata ?? {})
  if (entries.length === 0) {
    return '无附加信息'
  }
  return entries
    .slice(0, 4)
    .map(([key, value]) => `${key}: ${formatUnknown(value)}`)
    .join(' / ')
}

function formatUnknown(value: unknown) {
  if (Array.isArray(value)) {
    return value.join(', ')
  }
  if (value && typeof value === 'object') {
    return JSON.stringify(value)
  }
  return `${value ?? '-'}`
}

function metadataValue(key: string) {
  const metadata = knowledgeStore.selectedDocument?.metadata ?? {}
  const value = metadata[key]
  return value == null ? '-' : `${value}`
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

function docStatusLabel(status?: string) {
  const map: Record<string, string> = {
    uploaded: '已上传',
    ready: '就绪',
    failed: '失败',
    processing: '处理中',
    pending: '待处理',
    success: 'success',
  }
  return status ? (map[status] ?? status) : '-'
}

function statusClass(status?: string) {
  if (status === 'ready' || status === 'success') {
    return 'badge--success'
  }
  if (status === 'failed') {
    return 'badge--danger'
  }
  if (status === 'processing' || status === 'pending') {
    return 'badge--warning'
  }
  return 'badge--accent'
}

function taskLabel(taskType: string, status: string) {
  const typeMap: Record<string, string> = {
    parse: 'parse',
    chunk: 'chunk',
    embed: 'embed',
    graph: 'graph',
    reprocess: '重处理',
  }
  return `${typeMap[taskType] ?? taskType} · ${docStatusLabel(status)}`
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
.document-actions select {
  width: 220px;
}

.debugger-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 12px;
}

.reader-pane {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.parsed-reader,
.chunks-pane,
.inspector-section {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--bg-surface);
}

.metadata-form {
  display: grid;
  gap: 10px;
}

.pane-heading,
.item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.pane-heading h2,
.pane-heading h3,
.inspector-section h3,
.graph-details h4 {
  margin: 0;
}

.parsed-text {
  max-height: 62vh;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  background: color-mix(in srgb, var(--bg-inset), var(--bg-surface) 24%);
}

.debug-card {
  display: grid;
  gap: 6px;
  padding: 10px 0;
  border-top: 1px solid var(--line-soft);
}

.debug-card:first-of-type {
  border-top: 0;
}

.debug-card p,
.debug-card small,
.task-event p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.58;
}

.document-inspector {
  max-height: calc(100vh - var(--utility-height) - 28px);
  overflow: auto;
}

.meta-list {
  display: grid;
  gap: 10px;
  margin: 0;
}

.meta-list div {
  display: grid;
  gap: 3px;
}

.meta-list dt {
  color: var(--text-subtle);
  font-size: 12px;
}

.meta-list dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-weight: 650;
}

.task-event {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid var(--line-soft);
}

.task-event > span {
  width: 9px;
  height: 9px;
  margin-top: 5px;
  border-radius: 999px;
  background: var(--accent);
  box-shadow: 0 0 0 5px var(--accent-soft);
}

.task-event.badge--success > span {
  background: var(--success);
  box-shadow: 0 0 0 5px var(--success-soft);
}

.task-event.badge--danger > span {
  background: var(--danger);
  box-shadow: 0 0 0 5px var(--danger-soft);
}

.task-event.badge--warning > span {
  background: var(--warning);
  box-shadow: 0 0 0 5px var(--warning-soft);
}

.graph-summary {
  display: grid;
  gap: 8px;
}

.graph-summary > div {
  padding: 10px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background: var(--bg-inset);
}

.graph-summary p {
  margin: 6px 0 0;
  color: var(--text-muted);
}

.graph-details summary {
  cursor: pointer;
  color: var(--accent);
  font-weight: 720;
}

.graph-details > div {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

@media (max-width: 1080px) {
  .debugger-layout {
    grid-template-columns: 1fr;
  }

  .document-inspector {
    max-height: none;
  }
}
</style>
