<template>
  <section class="document-detail page-stack">
    <header class="page-header">
      <div>
        <p class="page-kicker">/documents/{{ route.params.documentId }}</p>
        <h2>{{ knowledgeStore.selectedDocument?.title ?? '文档详情' }}</h2>
        <p>
          {{ docStatusLabel(knowledgeStore.selectedDocument?.status) }} / {{ fileTypeLabel(knowledgeStore.selectedDocument?.fileType) }} /
          {{ formatFileSize(knowledgeStore.selectedDocument?.fileSize ?? 0) }} / 切块 {{ knowledgeStore.selectedDocument?.chunkCount ?? 0 }}
        </p>
        <div class="meta-row">
          <span class="badge">v{{ knowledgeStore.selectedDocument?.activeVersionNo ?? '-' }}</span>
          <span class="badge">文档图谱 {{ documentGraphStatus }}</span>
          <span class="badge">版本图谱 {{ versionGraphStatus }}</span>
        </div>
      </div>
      <div v-if="isAdmin" class="header-actions">
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
      <section class="document-grid">
        <article class="panel meta-panel">
          <h3>元数据</h3>
          <dl class="meta-list">
            <div><dt>知识域</dt><dd>{{ knowledgeDomainLabel }}</dd></div>
            <div><dt>切块策略</dt><dd>{{ chunkingProfileLabel }}</dd></div>
            <div><dt>当前版本</dt><dd>v{{ knowledgeStore.selectedDocument.activeVersionNo }}</dd></div>
            <div><dt>文件名</dt><dd>{{ knowledgeStore.selectedDocument.fileName }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ formatTime(knowledgeStore.selectedDocument.createdAt) }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ formatTime(knowledgeStore.selectedDocument.updatedAt) }}</dd></div>
            <div><dt>错误摘要</dt><dd>{{ knowledgeStore.selectedDocument.errorMessage || '-' }}</dd></div>
          </dl>
        </article>

        <article class="panel versions-panel">
          <h3>版本列表</h3>
          <div v-if="knowledgeStore.documentVersions.length === 0" class="empty-line">当前无版本记录。</div>
          <div v-for="version in knowledgeStore.documentVersions" :key="version.id" class="compact-card">
            <div class="item-head"><strong>v{{ version.versionNo }}</strong><span class="badge" :class="statusClass(version.status)">{{ docStatusLabel(version.status) }}</span></div>
            <p>切块策略：{{ profileName(version.chunkingProfileId) }}</p>
            <p>切块数：{{ version.chunkCount }} / 图谱同步：{{ version.graphSyncStatus }}</p>
            <p>更新时间：{{ formatTime(version.updatedAt) }}</p>
          </div>
        </article>

        <article class="panel parsed-panel">
          <h3>解析文本</h3>
          <div v-if="!knowledgeStore.selectedDocument.parsedText" class="empty-line">当前无解析文本。</div>
          <pre v-else class="parsed-text">{{ knowledgeStore.selectedDocument.parsedText }}</pre>
        </article>

        <article class="panel chunks-panel">
          <h3>切块预览</h3>
          <div v-if="knowledgeStore.documentChunks.length === 0" class="empty-line">当前无切块数据。</div>
          <div v-for="chunk in knowledgeStore.documentChunks" :key="chunk.id" class="compact-card">
            <strong>#{{ chunk.chunkNo }} {{ chunk.sectionTitle || '未命名章节' }}</strong>
            <p>{{ chunk.content }}</p>
            <small>{{ chunk.charCount }} 字</small>
          </div>
        </article>

        <article class="panel tasks-panel">
          <h3>任务日志</h3>
          <div v-if="knowledgeStore.documentTasks.length === 0" class="empty-line">当前无任务日志。</div>
          <div v-for="task in knowledgeStore.documentTasks" :key="task.id" class="compact-card">
            <strong>{{ taskLabel(task.taskType, task.status) }}</strong>
            <p>attempt={{ task.attemptCount }}</p>
            <p>输入：{{ task.inputSummary || '-' }}</p>
            <p>输出：{{ task.outputSummary || '-' }}</p>
            <p v-if="task.errorMessage">错误：{{ task.errorMessage }}</p>
          </div>
        </article>

        <article class="panel graph-panel">
          <div class="panel-heading">
            <div>
              <h3>文档图谱</h3>
              <p>当前文档版本的结构节点和关系边。</p>
            </div>
            <div class="meta-row"><span class="badge">节点 {{ graphNodeCount }}</span><span class="badge">边 {{ graphEdgeCount }}</span></div>
          </div>
          <LoadingSpinner v-if="knowledgeStore.loadingDocumentGraph" text="正在加载图谱..." />
          <div v-else-if="!knowledgeStore.documentGraph" class="empty-line">当前无图谱数据。</div>
          <template v-else>
            <div class="graph-summary">
              <div><strong>节点类型</strong><p>{{ nodeTypeSummary }}</p></div>
              <div><strong>边类型</strong><p>{{ edgeTypeSummary }}</p></div>
            </div>
            <div class="graph-columns">
              <div>
                <h4>节点</h4>
                <div v-for="node in knowledgeStore.documentGraph.nodes" :key="node.id" class="compact-card">
                  <strong>{{ node.type }} / {{ node.label }}</strong>
                  <p>{{ formatMetadata(node.metadata) }}</p>
                </div>
              </div>
              <div>
                <h4>关系</h4>
                <div v-for="edge in knowledgeStore.documentGraph.edges" :key="`${edge.sourceId}-${edge.targetId}-${edge.type}`" class="compact-card">
                  <strong>{{ edge.type }}</strong>
                  <p>{{ edge.sourceId }} -> {{ edge.targetId }}</p>
                  <p>{{ formatMetadata(edge.metadata) }}</p>
                </div>
              </div>
            </div>
          </template>
        </article>
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
.meta-row,
.header-actions,
.item-head,
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

.header-actions select {
  width: 220px;
}

.document-grid {
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  gap: 12px;
}

.parsed-panel,
.graph-panel {
  grid-column: 1 / -1;
}

.panel {
  display: grid;
  align-content: start;
  gap: 12px;
}

.panel h3,
.panel h4 {
  margin: 0;
}

.panel-heading {
  justify-content: space-between;
}

.panel-heading p {
  margin: 6px 0 0;
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
  color: var(--color-text-subtle);
  font-size: 12px;
}

.meta-list dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-weight: 650;
}

.compact-card {
  display: grid;
  gap: 6px;
  padding: 10px 0;
  border-top: 1px solid var(--color-border);
}

.compact-card:first-of-type {
  border-top: 0;
}

.compact-card p,
.compact-card small {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.parsed-text {
  max-height: 420px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.graph-summary,
.graph-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.graph-summary > div,
.graph-columns > div {
  min-width: 0;
}

.graph-summary > div {
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
}

.graph-summary p {
  margin: 6px 0 0;
  color: var(--color-text-muted);
}

@media (max-width: 980px) {
  .document-grid,
  .graph-summary,
  .graph-columns {
    grid-template-columns: 1fr;
  }

  .parsed-panel,
  .graph-panel {
    grid-column: auto;
  }
}
</style>
