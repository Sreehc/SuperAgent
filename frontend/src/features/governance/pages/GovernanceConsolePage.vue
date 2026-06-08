<template>
  <section class="governance-console workspace-page">
    <header class="workspace-strip">
      <div class="workspace-title">
        <p class="section-label">Knowledge governance</p>
        <h1>知识治理控制台</h1>
        <p>维护知识域、切块策略，并查看图谱同步状态的入口。</p>
      </div>
      <button class="btn btn-ghost btn-sm" type="button" @click="loadAll">刷新</button>
    </header>

    <section class="governance-tabs">
      <button v-for="tab in tabs" :key="tab" class="tab-button" :class="{ 'tab-button--active': activeTab === tab }" type="button" @click="activeTab = tab">
        {{ tab }}
      </button>
    </section>

    <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>

    <section class="governance-layout">
      <main class="governance-list">
        <template v-if="activeTab === '知识域'">
          <article v-for="domain in domains" :key="domain.id" class="governance-row" :class="{ 'governance-row--active': editingDomainId === domain.id }">
            <div>
              <strong>{{ domain.name }}</strong>
              <p>{{ domain.code }} · {{ domain.status }}</p>
              <p>{{ domain.description || '暂无描述' }}</p>
            </div>
            <button class="btn btn-ghost btn-sm" type="button" @click="editDomain(domain.id)">编辑</button>
          </article>
        </template>

        <template v-else-if="activeTab === '切块策略'">
          <article v-for="profile in profiles" :key="profile.id" class="governance-row" :class="{ 'governance-row--active': editingProfileId === profile.id }">
            <div>
              <strong>{{ profile.name }}</strong>
              <p>{{ profile.code }} · {{ profile.strategy }} · {{ profile.status }}</p>
              <p>{{ profile.isDefault ? '默认策略' : '非默认' }}</p>
            </div>
            <button class="btn btn-ghost btn-sm" type="button" @click="editProfile(profile.id)">编辑</button>
          </article>
        </template>

        <template v-else>
          <div class="filter-row">
            <label class="field filter-field">
              <span>知识库</span>
              <select v-model="selectedKnowledgeBaseId" @change="loadKnowledgeDocuments">
                <option value="">选择知识库查看图谱文档</option>
                <option v-for="base in knowledgeBases" :key="base.id" :value="`${base.id}`">{{ base.name }}</option>
              </select>
            </label>
          </div>
          <div v-if="graphDocuments.length === 0" class="empty-line">选择知识库后可查看文档图谱同步状态。</div>
          <article v-for="document in graphDocuments" :key="document.id" class="governance-row">
            <div>
              <strong>{{ document.title }}</strong>
              <p>{{ document.fileType }} · {{ document.status }} · chunks {{ document.chunkCount }}</p>
              <p>更新时间：{{ formatTime(document.updatedAt) }}</p>
            </div>
            <RouterLink class="btn btn-ghost btn-sm" :to="`/documents/${document.id}`">打开文档图谱</RouterLink>
          </article>
        </template>
      </main>

      <aside class="governance-editor inspector-box">
        <template v-if="activeTab === '知识域'">
          <p class="section-label">Domain editor</p>
          <h2>{{ editingDomainId ? '编辑知识域' : '新增知识域' }}</h2>
          <form class="editor-form" @submit.prevent="submitDomain">
            <label class="field"><span>编码</span><input v-model="domainForm.code" :disabled="Boolean(editingDomainId)" placeholder="support" /></label>
            <label class="field"><span>名称</span><input v-model="domainForm.name" placeholder="售后知识域" /></label>
            <label class="field"><span>描述</span><input v-model="domainForm.description" placeholder="用于限定知识治理和检索范围" /></label>
            <div class="action-row">
              <button class="btn btn-ghost" type="button" @click="resetDomainForm">清空</button>
              <button class="btn btn-primary" type="submit">{{ editingDomainId ? '更新知识域' : '新增知识域' }}</button>
            </div>
          </form>
        </template>

        <template v-else-if="activeTab === '切块策略'">
          <p class="section-label">Chunking editor</p>
          <h2>{{ editingProfileId ? '编辑策略' : '新增策略' }}</h2>
          <form class="editor-form" @submit.prevent="submitProfile">
            <label class="field"><span>编码</span><input v-model="profileForm.code" :disabled="Boolean(editingProfileId)" placeholder="recursive-default" /></label>
            <label class="field"><span>名称</span><input v-model="profileForm.name" placeholder="默认递归切块" /></label>
            <label class="field"><span>策略</span><input v-model="profileForm.strategy" placeholder="recursive" /></label>
            <label class="switch-row"><span>默认</span><input v-model="profileForm.isDefault" type="checkbox" /></label>
            <label class="field"><span>配置 JSON</span><textarea v-model="profileForm.configText" rows="8" placeholder='{"maxChars": 1200}' /></label>
            <div class="action-row">
              <button class="btn btn-ghost" type="button" @click="resetProfileForm">清空</button>
              <button class="btn btn-primary" type="submit">{{ editingProfileId ? '更新策略' : '新增策略' }}</button>
            </div>
          </form>
        </template>

        <template v-else>
          <p class="section-label">Graph documents</p>
          <h2>图谱文档</h2>
          <p>选择知识库后，左侧会列出文档图谱同步状态。点击文档进入 parser debugger 查看节点、关系和任务日志。</p>
          <dl>
            <div><dt>知识库</dt><dd>{{ selectedKnowledgeBaseId || '-' }}</dd></div>
            <div><dt>文档数</dt><dd>{{ graphDocuments.length }}</dd></div>
          </dl>
        </template>
      </aside>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  createChunkingProfile,
  createKnowledgeDomain,
  listChunkingProfiles,
  listKnowledgeBases,
  listKnowledgeDocuments,
  listKnowledgeDomains,
  updateChunkingProfile,
  updateKnowledgeDomain,
} from '../../knowledge/api'
import type { ChunkingProfileItem, KnowledgeBaseListItem, KnowledgeDomainItem, KnowledgeDocumentListItem } from '../../knowledge/types'

const tabs = ['知识域', '切块策略', '图谱文档'] as const
const activeTab = ref<(typeof tabs)[number]>('知识域')
const domains = ref<KnowledgeDomainItem[]>([])
const profiles = ref<ChunkingProfileItem[]>([])
const knowledgeBases = ref<KnowledgeBaseListItem[]>([])
const graphDocuments = ref<KnowledgeDocumentListItem[]>([])
const selectedKnowledgeBaseId = ref('')
const editingDomainId = ref<number | null>(null)
const editingProfileId = ref<number | null>(null)
const errorMessage = ref('')

const domainForm = reactive({
  code: '',
  name: '',
  description: '',
})

const profileForm = reactive({
  code: '',
  name: '',
  strategy: 'recursive',
  isDefault: false,
  configText: '{"maxChars": 1200}',
})

onMounted(async () => {
  await loadAll()
})

async function loadAll() {
  errorMessage.value = ''
  try {
    const [domainResponse, profileResponse, knowledgeResponse] = await Promise.all([
      listKnowledgeDomains(),
      listChunkingProfiles(),
      listKnowledgeBases({ pageSize: 100 }),
    ])
    domains.value = domainResponse.data
    profiles.value = profileResponse.data
    knowledgeBases.value = knowledgeResponse.data.items
    await loadKnowledgeDocuments()
  } catch {
    errorMessage.value = '治理数据加载失败，请稍后重试。'
  }
}

async function loadKnowledgeDocuments() {
  if (!selectedKnowledgeBaseId.value) {
    graphDocuments.value = []
    return
  }
  try {
    const response = await listKnowledgeDocuments(Number(selectedKnowledgeBaseId.value), { pageSize: 20 })
    graphDocuments.value = response.data.items
  } catch {
    errorMessage.value = '文档列表加载失败，请稍后重试。'
  }
}

async function submitDomain() {
  try {
    if (editingDomainId.value) {
      await updateKnowledgeDomain(editingDomainId.value, {
        name: domainForm.name.trim(),
        description: domainForm.description.trim() || undefined,
      })
    } else {
      await createKnowledgeDomain({
        code: domainForm.code.trim(),
        name: domainForm.name.trim(),
        description: domainForm.description.trim() || undefined,
      })
    }
    resetDomainForm()
    await loadAll()
  } catch {
    errorMessage.value = '知识域保存失败，请检查输入。'
  }
}

function editDomain(domainId: number) {
  const domain = domains.value.find((item) => item.id === domainId)
  if (!domain) {
    return
  }
  editingDomainId.value = domain.id
  domainForm.code = domain.code
  domainForm.name = domain.name
  domainForm.description = domain.description ?? ''
}

async function submitProfile() {
  try {
    const config = JSON.parse(profileForm.configText || '{}') as Record<string, unknown>
    if (editingProfileId.value) {
      await updateChunkingProfile(editingProfileId.value, {
        name: profileForm.name.trim(),
        strategy: profileForm.strategy.trim(),
        isDefault: profileForm.isDefault,
        config,
      })
    } else {
      await createChunkingProfile({
        code: profileForm.code.trim(),
        name: profileForm.name.trim(),
        strategy: profileForm.strategy.trim(),
        isDefault: profileForm.isDefault,
        config,
      })
    }
    resetProfileForm()
    await loadAll()
  } catch {
    errorMessage.value = '切块策略保存失败，请检查 JSON 配置。'
  }
}

function editProfile(profileId: number) {
  const profile = profiles.value.find((item) => item.id === profileId)
  if (!profile) {
    return
  }
  editingProfileId.value = profile.id
  profileForm.code = profile.code
  profileForm.name = profile.name
  profileForm.strategy = profile.strategy
  profileForm.isDefault = profile.isDefault
  profileForm.configText = JSON.stringify(profile.config, null, 2)
}

function resetDomainForm() {
  editingDomainId.value = null
  domainForm.code = ''
  domainForm.name = ''
  domainForm.description = ''
}

function resetProfileForm() {
  editingProfileId.value = null
  profileForm.code = ''
  profileForm.name = ''
  profileForm.strategy = 'recursive'
  profileForm.isDefault = false
  profileForm.configText = '{"maxChars": 1200}'
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
.governance-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--bg-surface);
}

.tab-button {
  color: var(--text-muted);
  background: var(--bg-subtle);
  border-color: var(--line-soft);
}

.tab-button--active {
  color: var(--accent);
  border-color: color-mix(in srgb, var(--accent), transparent 58%);
  background: var(--accent-soft);
}

.governance-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 12px;
}

.governance-list {
  display: grid;
  align-content: start;
  gap: 10px;
  min-width: 0;
}

.governance-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--bg-surface);
}

.governance-row--active,
.governance-row:hover {
  border-color: color-mix(in srgb, var(--accent), transparent 58%);
  background: var(--accent-soft);
}

.governance-row p {
  margin: 5px 0 0;
  color: var(--text-muted);
  line-height: 1.5;
}

.governance-editor h2 {
  margin: 0;
  font-size: 18px;
}

.governance-editor p {
  margin: 6px 0 0;
  color: var(--text-muted);
  line-height: 1.58;
}

.editor-form {
  display: grid;
  gap: 10px;
}

.switch-row {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  padding: 10px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background: var(--bg-inset);
}

.filter-field {
  width: min(360px, 100%);
}

.governance-editor dl {
  display: grid;
  gap: 8px;
  margin: 0;
}

.governance-editor dl div {
  padding: 9px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background: var(--bg-inset);
}

.governance-editor dt {
  color: var(--text-subtle);
  font-size: 12px;
}

.governance-editor dd {
  margin: 3px 0 0;
  font-family: var(--font-mono);
  font-size: 12px;
}

@media (max-width: 980px) {
  .governance-layout {
    grid-template-columns: 1fr;
  }
}
</style>
