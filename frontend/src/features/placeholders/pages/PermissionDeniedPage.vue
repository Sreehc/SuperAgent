<template>
  <section class="denied-page">
    <p class="denied-page__eyebrow">/forbidden</p>
    <h2>当前租户角色无权访问这个页面</h2>
    <p>
      菜单已经按角色做了收敛，但后端权限仍然是最终校验方。你可以返回对话工作台，
      或切换到具备权限的租户后再重试。
    </p>
    <RouterLink class="denied-page__action" to="/chat">返回 /chat</RouterLink>
  </section>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<style scoped>
.denied-page {
  padding: 2rem;
  border-radius: calc(var(--radius-lg) + 4px);
  background:
    linear-gradient(180deg, rgba(255, 252, 247, 0.96), rgba(255, 247, 238, 0.96)),
    radial-gradient(circle at top right, rgba(179, 63, 45, 0.12), transparent 34%);
  border: 1px solid var(--line-soft);
  box-shadow: var(--shadow-card);
}

.denied-page__eyebrow {
  margin: 0;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.14em;
  font-size: 0.74rem;
}

.denied-page h2 {
  margin: 0.4rem 0 0.8rem;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
  font-size: clamp(1.9rem, 4vw, 2.6rem);
}

.denied-page__action {
  display: inline-flex;
  align-items: center;
  margin-top: 1rem;
  padding: 0.85rem 1.1rem;
  border-radius: 999px;
  background: var(--bg-dark);
  color: var(--text-contrast);
}
</style>
