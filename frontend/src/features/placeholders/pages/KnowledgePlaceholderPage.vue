<template>
  <section class="placeholder-page">
    <p class="placeholder-page__eyebrow">/knowledge</p>
    <h2>知识库页面已占位</h2>
    <p>当前阶段仅验证基础导航和角色菜单控制，不展开知识库管理细节。</p>
  </section>
</template>

<style scoped>
.placeholder-page {
  padding: 2rem;
  border-radius: calc(var(--radius-lg) + 4px);
  background: var(--bg-panel);
  border: 1px solid var(--line-soft);
  box-shadow: var(--shadow-soft);
}

.placeholder-page__eyebrow {
  margin: 0;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.14em;
  font-size: 0.74rem;
}

.placeholder-page h2 {
  font-family: 'Fraunces', 'Iowan Old Style', serif;
}
</style>
