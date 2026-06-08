<template>
  <section class="knowledge-inventory workspace-page">
    <header class="workspace-strip">
      <div class="workspace-title">
        <p class="section-label">Knowledge inventory</p>
        <h1>知识库</h1>
        <p>管理租户知识库、发布状态和文档入口。</p>
      </div>
      <div class="meta-row">
        <span class="metric-chip">total {{ knowledgeStore.knowledgeBases.length }}</span>
        <button v-if="isAdmin" class="btn btn-primary btn-sm" type="button" @click="creating = !creating">
          {{ creating ? '收起' : '新建知识库' }}
        </button>
      </div>
    </header>

    <form v-if="isAdmin && creating" class="create-row command-box" @submit.prevent="createKnowledgeBase">
      <label class="field">
        <span>新知识库名称</span>
        <input v-model="draftName" data-testid="knowledge-create-name" type="text" placeholder="例如：售后知识库" />
      </label>
      <button
        class="btn btn-primary"
        :class="{ 'btn-loading': knowledgeStore.creatingKnowledgeBase }"
        data-testid="knowledge-create-submit"
        type="submit"
        :disabled="knowledgeStore.creatingKnowledgeBase || !draftName.trim()"
      >
        {{ knowledgeStore.creatingKnowledgeBase ? '创建中...' : '创建并进入' }}
      </button>
    </form>

    <section class="filter-row">
      <label class="field filter-field">
        <span>搜索</span>
        <input v-model="knowledgeStore.keyword" type="search" placeholder="搜索名称" @keyup.enter="knowledgeStore.fetchKnowledgeBases" />
      </label>
      <label class="field filter-field filter-field--small">
        <span>状态</span>
        <select v-model="knowledgeStore.statusFilter" @change="knowledgeStore.fetchKnowledgeBases">
          <option value="">全部状态</option>
          <option value="draft">草稿</option>
          <option value="published">已发布</option>
          <option value="archived">已归档</option>
        </select>
      </label>
      <button class="btn btn-secondary" type="button" @click="knowledgeStore.fetchKnowledgeBases">查询</button>
    </section>

    <p v-if="knowledgeStore.errorMessage" class="error-banner">{{ knowledgeStore.errorMessage }}</p>

    <LoadingSpinner v-if="knowledgeStore.loadingKnowledgeBases" text="正在加载知识库列表..." />
    <EmptyState v-else-if="knowledgeStore.isEmpty" variant="knowledge" title="暂无知识库" description="创建第一个知识库后上传文档并发布给成员使用。" />
    <section v-else class="data-frame">
      <table class="data-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>状态</th>
            <th>文档数</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in knowledgeStore.knowledgeBases" :key="item.id">
            <td>
              <button class="inventory-name" type="button" @click="goDetail(item.id)">
                <strong>{{ item.name }}</strong>
                <span>#{{ item.id }}</span>
              </button>
            </td>
            <td><span class="badge" :class="statusClass(item.status)">{{ statusLabel(item.status) }}</span></td>
            <td class="numeric">{{ item.documentCount }}</td>
            <td>{{ formatTime(item.updatedAt) }}</td>
            <td><button class="btn-text" type="button" @click="goDetail(item.id)">打开</button></td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { LoadingSpinner, EmptyState } from '../../../components'
import { useAuthStore } from '../../auth/store/auth'
import { useKnowledgeStore } from '../store/knowledge'

const router = useRouter()
const authStore = useAuthStore()
const knowledgeStore = useKnowledgeStore()
const draftName = ref('')
const creating = ref(false)

const isAdmin = computed(() => ['OWNER', 'ADMIN'].includes(authStore.currentRole ?? ''))

onMounted(async () => {
  await knowledgeStore.fetchKnowledgeBases()
})

async function createKnowledgeBase() {
  await knowledgeStore.createBase({
    name: draftName.value.trim(),
    visibility: 'tenant',
  })
  draftName.value = ''
  creating.value = false
  if (knowledgeStore.selectedKnowledgeBase) {
    await router.push(`/knowledge/${knowledgeStore.selectedKnowledgeBase.id}`)
  }
}

async function goDetail(knowledgeBaseId: number) {
  await router.push(`/knowledge/${knowledgeBaseId}`)
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    draft: '草稿',
    published: '已发布',
    archived: '已归档',
  }
  return map[status] ?? status
}

function statusClass(status: string) {
  if (status === 'published') {
    return 'badge--success'
  }
  if (status === 'archived') {
    return 'badge--warning'
  }
  return 'badge--accent'
}
</script>

<style scoped>
.create-row {
  display: grid;
  grid-template-columns: minmax(240px, 340px) auto;
  align-items: end;
  gap: 10px;
}

.filter-field {
  width: min(320px, 100%);
}

.filter-field--small {
  width: 180px;
}

.inventory-name {
  display: grid;
  gap: 3px;
  padding: 0;
  color: var(--text-main);
  text-align: left;
  border: 0;
  background: transparent;
}

.inventory-name span {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

@media (max-width: 820px) {
  .create-row {
    grid-template-columns: 1fr;
  }
}
</style>
