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
        <div class="header-meta">
          <span class="status-chip">当前版本 v{{ knowledgeStore.selectedDocument?.activeVersionNo ?? '-' }}</span>
          <span class="status-chip">文档图谱 {{ documentGraphStatus }}</span>
          <span class="status-chip">版本图谱 {{ versionGraphStatus }}</span>
        </div>
      </div>
      <div v-if="isAdmin" class="header-actions">
        <select v-model="reprocessChunkingProfileId">
          <option value="">沿用当前切块策略</option>
          <option v-for="profile in knowledgeStore.chunkingProfiles" :key="profile.id" :value="`${profile.id}`">
            {{ profile.name }} · {{ profile.strategy }}
          </option>
        </select>
        <button
          class="pill-button"
          type="button"
          :disabled="knowledgeStore.reprocessingDocument"
          @click="triggerReprocess"
        >
          {{ knowledgeStore.reprocessingDocument ? '处理中...' : '重处理' }}
        </button>
        <button
          class="ghost-button"
          type="button"
          :disabled="knowledgeStore.rebuildingDocumentGraph"
          @click="knowledgeStore.rebuildCurrentDocumentGraph"
        >
          {{ knowledgeStore.rebuildingDocumentGraph ? '重建中...' : '重建图谱' }}
        </button>
        <button
          class="danger-button"
          type="button"
          :disabled="knowledgeStore.deletingDocument"
          @click="deleteDocument"
        >
          {{ knowledgeStore.deletingDocument ? '删除中...' : '删除文档' }}
        </button>
      </div>
    </header>

    <p v-if="knowledgeStore.errorMessage" class="error-banner">{{ knowledgeStore.errorMessage }}</p>
    <section v-if="knowledgeStore.loadingDocumentDetail" class="card-shell">正在加载文档详情...</section>

    <template v-else-if="knowledgeStore.selectedDocument">
      <section class="document-grid">
        <article class="card-shell">
          <h3>元数据</h3>
          <dl class="meta-list">
            <div>
              <dt>知识域</dt>
              <dd>{{ knowledgeDomainLabel }}</dd>
            </div>
            <div>
              <dt>切块策略</dt>
              <dd>{{ chunkingProfileLabel }}</dd>
            </div>
            <div>
              <dt>当前版本</dt>
              <dd>v{{ knowledgeStore.selectedDocument.activeVersionNo }}</dd>
            </div>
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
              <dt>图谱状态</dt>
              <dd>{{ documentGraphStatus }} / {{ versionGraphStatus }}</dd>
            </div>
            <div>
              <dt>错误摘要</dt>
              <dd>{{ knowledgeStore.selectedDocument.errorMessage || '-' }}</dd>
            </div>
          </dl>
        </article>

        <article class="card-shell">
          <h3>版本列表</h3>
          <div v-if="knowledgeStore.documentVersions.length === 0" class="empty-line">当前无版本记录。</div>
          <div v-for="version in knowledgeStore.documentVersions" :key="version.id" class="version-card">
            <div class="version-card__head">
              <strong>v{{ version.versionNo }}</strong>
              <span class="status-chip">{{ version.status }}</span>
            </div>
            <p>切块策略：{{ profileName(version.chunkingProfileId) }}</p>
            <p>切块数：{{ version.chunkCount }}</p>
            <p>图谱同步：{{ version.graphSyncStatus }}</p>
            <p>更新时间：{{ formatTime(version.updatedAt) }}</p>
          </div>
        </article>

        <article class="card-shell">
          <h3>解析文本</h3>
          <div v-if="!knowledgeStore.selectedDocument.parsedText" class="empty-line">当前无解析文本。</div>
          <pre v-else class="parsed-text">{{ knowledgeStore.selectedDocument.parsedText }}</pre>
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

        <article class="card-shell graph-card">
          <div class="graph-card__header">
            <div>
              <h3>文档图谱</h3>
              <p>展示当前文档版本的结构节点和关系边。</p>
            </div>
            <div class="graph-badges">
              <span class="status-chip">节点 {{ graphNodeCount }}</span>
              <span class="status-chip">边 {{ graphEdgeCount }}</span>
            </div>
          </div>
          <div v-if="knowledgeStore.loadingDocumentGraph" class="empty-line">正在加载图谱...</div>
          <div v-else-if="!knowledgeStore.documentGraph" class="empty-line">当前无图谱数据。</div>
          <template v-else>
            <div class="graph-summary">
              <div class="graph-stat">
                <strong>节点类型</strong>
                <p>{{ nodeTypeSummary }}</p>
              </div>
              <div class="graph-stat">
                <strong>边类型</strong>
                <p>{{ edgeTypeSummary }}</p>
              </div>
            </div>
            <div class="graph-columns">
              <div>
                <h4>节点</h4>
                <div v-for="node in knowledgeStore.documentGraph.nodes" :key="node.id" class="graph-item">
                  <strong>{{ node.type }} · {{ node.label }}</strong>
                  <p>{{ formatMetadata(node.metadata) }}</p>
                </div>
              </div>
              <div>
                <h4>关系</h4>
                <div
                  v-for="edge in knowledgeStore.documentGraph.edges"
                  :key="`${edge.sourceId}-${edge.targetId}-${edge.type}`"
                  class="graph-item"
                >
                  <strong>{{ edge.type }}</strong>
                  <p>{{ edge.sourceId }} → {{ edge.targetId }}</p>
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
  return domain ? `${domain.name} · ${domain.code}` : `#${domainId}`
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
  return profile ? `${profile.name} · ${profile.strategy}` : `#${profileId}`
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
    .map(([key, count]) => `${key} × ${count}`)
    .join('，')
}

function formatMetadata(metadata: Record<string, unknown>) {
  const entries = Object.entries(metadata ?? {})
  if (entries.length === 0) {
    return '无附加信息'
  }
  return entries
    .slice(0, 4)
    .map(([key, value]) => `${key}: ${formatUnknown(value)}`)
    .join(' · ')
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
</script>

<style scoped>
.document-detail,
.document-grid {
  display: grid;
  gap: 1rem;
}

.document-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
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

.header-actions,
.header-meta,
.graph-badges,
.graph-summary,
.graph-columns {
  display: flex;
  gap: 0.8rem;
}

.header-actions {
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.header-actions select {
  min-width: 13rem;
}

.header-meta,
.graph-badges {
  flex-wrap: wrap;
  margin-top: 0.8rem;
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
.task-card,
.version-card,
.graph-item {
  padding: 0.8rem 0;
  border-bottom: 1px solid var(--line-soft);
}

.version-card__head,
.graph-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.8rem;
}

.parsed-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary);
  max-height: 24rem;
  overflow: auto;
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

.ghost-button,
.danger-button,
.header-actions select {
  border-radius: var(--radius-sm);
  padding: 0.75rem 0.95rem;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.84);
}

.danger-button {
  border-color: rgba(178, 63, 63, 0.35);
  color: #9f2f2f;
}

.status-chip {
  display: inline-flex;
  padding: 0.25rem 0.7rem;
  border-radius: 999px;
  background: rgba(27, 47, 61, 0.08);
}

.graph-card {
  grid-column: 1 / -1;
}

.graph-summary,
.graph-columns {
  margin-top: 1rem;
}

.graph-stat,
.graph-columns > div {
  flex: 1;
  min-width: 0;
}

.empty-line,
.error-banner {
  color: var(--text-secondary);
}

@media (max-width: 960px) {
  .document-grid {
    grid-template-columns: 1fr;
  }

  .detail-header,
  .graph-card__header,
  .graph-columns,
  .graph-summary {
    flex-direction: column;
    align-items: flex-start;
  }

  .graph-card {
    grid-column: auto;
  }
}
</style>
