<template>
  <section class="knowledge-page page-stack">
    <header class="page-header">
      <div>
        <p class="page-kicker">/knowledge</p>
        <h2>知识库</h2>
        <p>管理租户知识库、发布状态和文档入口。</p>
      </div>
      <form v-if="isAdmin" class="create-form" @submit.prevent="createKnowledgeBase">
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
          {{ knowledgeStore.creatingKnowledgeBase ? '创建中...' : '新建知识库' }}
        </button>
      </form>
    </header>

    <section class="toolbar">
      <label class="field toolbar-field">
        <span>搜索</span>
        <input v-model="knowledgeStore.keyword" type="search" placeholder="搜索名称" @keyup.enter="knowledgeStore.fetchKnowledgeBases" />
      </label>
      <label class="field toolbar-field toolbar-field--small">
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
    <section v-else class="table-wrap">
      <table class="table">
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
            <td><strong>{{ item.name }}</strong></td>
            <td><span class="badge" :class="statusClass(item.status)">{{ statusLabel(item.status) }}</span></td>
            <td>{{ item.documentCount }}</td>
            <td>{{ formatTime(item.updatedAt) }}</td>
            <td><button class="btn-text" type="button" @click="goDetail(item.id)">详情</button></td>
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
.create-form {
  display: grid;
  grid-template-columns: minmax(240px, 320px) auto;
  align-items: end;
  gap: 10px;
}

.toolbar-field {
  width: min(320px, 100%);
}

.toolbar-field--small {
  width: 180px;
}

@media (max-width: 820px) {
  .create-form {
    grid-template-columns: 1fr;
    width: 100%;
  }
}
</style>
