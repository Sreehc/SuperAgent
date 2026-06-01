<template>
  <section class="placeholder-page">
    <p class="placeholder-page__eyebrow">/traces</p>
    <h2>Trace 管理入口已接入</h2>
    <p>该入口仅对 `OWNER / ADMIN` 可见，并在路由层做角色拦截。</p>
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
