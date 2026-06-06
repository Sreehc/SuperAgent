<template>
  <section class="governance-page">
    <header class="card-shell page-header">
      <div>
        <p class="eyebrow">/governance</p>
        <h2>知识治理控制台</h2>
        <p>维护知识域、切块策略，并查看图谱同步状态的入口。</p>
      </div>
      <button class="ghost-button" type="button" @click="loadAll">刷新</button>
    </header>

    <section class="card-shell tabs">
      <button
        v-for="tab in tabs"
        :key="tab"
        class="tab-button"
        :class="{ 'tab-button--active': activeTab === tab }"
        type="button"
        @click="activeTab = tab"
      >
        {{ tab }}
      </button>
    </section>

    <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>

    <section v-if="activeTab === '知识域'" class="card-shell stack">
      <form class="form-grid" @submit.prevent="submitDomain">
        <input v-model="domainForm.code" placeholder="编码" />
        <input v-model="domainForm.name" placeholder="名称" />
        <input v-model="domainForm.description" class="span-2" placeholder="描述" />
        <button class="pill-button" type="submit">{{ editingDomainId ? '更新知识域' : '新增知识域' }}</button>
      </form>
      <article v-for="domain in domains" :key="domain.id" class="item-card">
        <div class="item-head">
          <div>
            <strong>{{ domain.name }}</strong>
            <p>{{ domain.code }} · {{ domain.status }}</p>
          </div>
          <button class="ghost-button" type="button" @click="editDomain(domain.id)">编辑</button>
        </div>
        <p>{{ domain.description || '暂无描述' }}</p>
      </article>
    </section>

    <section v-else-if="activeTab === '切块策略'" class="card-shell stack">
      <form class="form-grid" @submit.prevent="submitProfile">
        <input v-model="profileForm.code" placeholder="编码" />
        <input v-model="profileForm.name" placeholder="名称" />
        <input v-model="profileForm.strategy" placeholder="策略" />
        <label class="toggle-inline">
          <span>默认</span>
          <input v-model="profileForm.isDefault" type="checkbox" />
        </label>
        <textarea v-model="profileForm.configText" class="span-2" rows="5" placeholder='{"maxChars": 1200}' />
        <button class="pill-button" type="submit">{{ editingProfileId ? '更新策略' : '新增策略' }}</button>
      </form>
      <article v-for="profile in profiles" :key="profile.id" class="item-card">
        <div class="item-head">
          <div>
            <strong>{{ profile.name }}</strong>
            <p>{{ profile.code }} · {{ profile.strategy }} · {{ profile.status }}</p>
          </div>
          <button class="ghost-button" type="button" @click="editProfile(profile.id)">编辑</button>
        </div>
        <pre class="metadata">{{ JSON.stringify(profile.config, null, 2) }}</pre>
      </article>
    </section>

    <section v-else class="card-shell stack">
      <div class="filter-row">
        <select v-model="selectedKnowledgeBaseId" @change="loadKnowledgeDocuments">
          <option value="">选择知识库查看图谱文档</option>
          <option v-for="base in knowledgeBases" :key="base.id" :value="`${base.id}`">{{ base.name }}</option>
        </select>
      </div>
      <div v-if="graphDocuments.length === 0" class="empty-line">选择知识库后可查看文档图谱同步状态。</div>
      <article v-for="document in graphDocuments" :key="document.id" class="item-card">
        <div class="item-head">
          <div>
            <strong>{{ document.title }}</strong>
            <p>{{ document.fileType }} · {{ document.status }}</p>
          </div>
          <RouterLink class="ghost-button link-button" :to="`/documents/${document.id}`">打开文档图谱</RouterLink>
        </div>
        <p>切块数：{{ document.chunkCount }} · 更新时间：{{ formatTime(document.updatedAt) }}</p>
      </article>
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
.governance-page {
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

.page-header,
.item-head,
.filter-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.tabs,
.stack {
  display: grid;
  gap: 0.8rem;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.8rem;
}

.span-2 {
  grid-column: 1 / -1;
}

.tab-button,
.ghost-button,
.pill-button,
.link-button {
  border-radius: 999px;
  padding: 0.75rem 1rem;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.78);
}

.tab-button--active {
  background: rgba(199, 109, 63, 0.12);
  border-color: rgba(199, 109, 63, 0.36);
}

.pill-button {
  border: 0;
  background: linear-gradient(135deg, var(--bg-accent), #d78655);
  color: var(--text-contrast);
}

.item-card {
  padding: 1rem;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.7);
}

.metadata {
  margin: 0.75rem 0 0;
  padding: 0.8rem;
  border-radius: var(--radius-sm);
  background: rgba(17, 37, 52, 0.06);
  overflow: auto;
}

.toggle-inline {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.eyebrow,
.error-banner,
.empty-line,
.item-card p {
  color: var(--text-secondary);
}

.eyebrow {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.page-header h2 {
  margin: 0.2rem 0;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
}

input,
textarea {
  padding: 0.8rem 0.95rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.84);
}

textarea {
  resize: vertical;
}

@media (max-width: 960px) {
  .page-header,
  .item-head,
  .filter-row {
    flex-direction: column;
    align-items: stretch;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: auto;
  }
}
</style>
