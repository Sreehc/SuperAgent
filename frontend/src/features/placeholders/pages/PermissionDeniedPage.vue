<template>
  <section class="denied-page page-stack">
    <header class="page-header">
      <div>
        <p class="page-kicker">/forbidden</p>
        <h2>当前租户角色无权访问这个页面</h2>
        <p>当前角色没有访问该模块的权限。可以返回对话工作台，或切换到具备权限的租户后重试。</p>
      </div>
      <RouterLink class="btn btn-primary" to="/chat">返回对话</RouterLink>
    </header>
  </section>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<style scoped>
.denied-page {
  max-width: 860px;
}

.denied-page .page-header {
  border-left: 4px solid var(--color-danger);
}

.denied-page p:last-child {
  max-width: 62ch;
  margin: 8px 0 0;
}
</style>
