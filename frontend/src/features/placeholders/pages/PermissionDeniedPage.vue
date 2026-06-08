<template>
  <section class="denied-page workspace-page">
    <header class="denied-hero">
      <div class="denied-code" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <div class="workspace-title">
        <p class="section-label">Access boundary</p>
        <h1>当前租户角色无权访问这个页面</h1>
        <p>当前角色没有访问该模块的权限。可以返回对话工作台，或切换到具备权限的租户后重试。</p>
      </div>
      <RouterLink class="btn btn-primary" to="/chat">返回对话</RouterLink>
    </header>

    <section class="access-grid">
      <article>
        <span>当前角色</span>
        <strong>{{ currentRoleLabel }}</strong>
      </article>
      <article>
        <span>可访问模块</span>
        <strong>{{ accessibleModules }}</strong>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '../../auth/store/auth'

const authStore = useAuthStore()

const currentRoleLabel = computed(() => authStore.currentRole ?? 'UNKNOWN')
const accessibleModules = computed(() => {
  if (authStore.currentRole === 'OWNER' || authStore.currentRole === 'ADMIN') {
    return 'Chat, Knowledge, Trace, Tools, Governance, Settings'
  }
  return 'Chat, Knowledge'
})
</script>

<style scoped>
.denied-page {
  max-width: 860px;
}

.denied-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 18px;
  border: 1px solid color-mix(in srgb, var(--danger), transparent 56%);
  border-radius: var(--radius-2);
  background:
    linear-gradient(135deg, var(--danger-soft), transparent 42%),
    var(--bg-surface);
}

.denied-hero p:last-child {
  max-width: 62ch;
  margin: 8px 0 0;
}

.denied-code {
  display: grid;
  gap: 5px;
  width: 44px;
}

.denied-code span {
  height: 8px;
  border-radius: 99px;
  background: var(--danger);
}

.denied-code span:nth-child(2) {
  width: 70%;
  background: var(--accent-hot);
}

.denied-code span:nth-child(3) {
  width: 42%;
  background: color-mix(in srgb, var(--danger), transparent 42%);
}

.access-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--line-soft);
}

.access-grid article {
  display: grid;
  gap: 6px;
  padding: 14px;
  background: var(--bg-surface);
}

.access-grid span {
  color: var(--text-muted);
  font-size: 12px;
}

.access-grid strong {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 720;
}

@media (max-width: 720px) {
  .denied-hero,
  .access-grid {
    grid-template-columns: 1fr;
  }
}
</style>
