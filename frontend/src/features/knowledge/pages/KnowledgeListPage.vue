<template>
  <section class="knowledge-page">
    <header class="knowledge-page__header card-shell">
      <div>
        <p class="eyebrow">/knowledge</p>
        <h2>知识库</h2>
        <p>管理员管理知识库，成员查看已发布知识库。</p>
      </div>
      <form v-if="isAdmin" class="create-form" @submit.prevent="createKnowledgeBase">
        <input v-model="draftName" data-testid="knowledge-create-name" type="text" placeholder="新知识库名称" />
        <button class="pill-button" data-testid="knowledge-create-submit" type="submit" :disabled="knowledgeStore.creatingKnowledgeBase || !draftName.trim()">
          {{ knowledgeStore.creatingKnowledgeBase ? '创建中...' : '+ 新建知识库' }}
        </button>
      </form>
    </header>

    <section class="knowledge-page__filters card-shell">
      <input v-model="knowledgeStore.keyword" type="search" placeholder="搜索名称..." @keyup.enter="knowledgeStore.fetchKnowledgeBases" />
      <select v-model="knowledgeStore.statusFilter" @change="knowledgeStore.fetchKnowledgeBases">
        <option value="">全部状态</option>
        <option value="draft">草稿</option>
        <option value="published">已发布</option>
        <option value="archived">已归档</option>
      </select>
      <button class="ghost-button" type="button" @click="knowledgeStore.fetchKnowledgeBases">查询</button>
    </section>

    <p v-if="knowledgeStore.errorMessage" class="error-banner">{{ knowledgeStore.errorMessage }}</p>

    <section v-if="knowledgeStore.loadingKnowledgeBases" class="card-shell">正在加载知识库列表...</section>
    <section v-else-if="knowledgeStore.isEmpty" class="card-shell">暂无知识库。</section>
    <section v-else class="card-shell">
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
            <td>{{ item.name }}</td>
            <td><span class="status-chip">{{ statusLabel(item.status) }}</span></td>
            <td>{{ item.documentCount }}</td>
            <td>{{ formatTime(item.updatedAt) }}</td>
            <td>
              <button class="table-link" type="button" @click="goDetail(item.id)">查看</button>
              <button v-if="isAdmin" class="table-link" type="button" @click="goDetail(item.id)">管理</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
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
</script>

<style scoped>
.knowledge-page {
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

.knowledge-page__header,
.knowledge-page__filters,
.create-form {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.knowledge-page__header h2 {
  margin: 0.2rem 0;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
}

.knowledge-page__filters input,
.knowledge-page__filters select,
.create-form input {
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
.ghost-button,
.table-link {
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

.table-link {
  padding: 0.4rem 0.8rem;
}

.error-banner {
  margin: 0;
  color: var(--danger);
}

@media (max-width: 960px) {
  .knowledge-page__header,
  .knowledge-page__filters,
  .create-form {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
