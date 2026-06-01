<template>
  <section class="placeholder-page">
    <p class="placeholder-page__eyebrow">/settings</p>
    <h2>设置页占位已接入</h2>
    <p>当前阶段只建立管理员入口和守卫，运行时配置细节在后续阶段实现。</p>
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
