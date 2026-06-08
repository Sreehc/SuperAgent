<template>
  <section class="placeholder-console workspace-page">
    <header class="workspace-strip">
      <div class="workspace-title">
        <p class="section-label">Knowledge inventory</p>
        <h1>知识库</h1>
        <p>知识库列表、文档处理状态和版本治理入口。</p>
      </div>
      <span class="status-chip">read index</span>
    </header>
    <div class="placeholder-ledger" aria-hidden="true">
      <span />
      <span />
      <span />
      <span />
    </div>
  </section>
</template>

<style scoped>
.placeholder-console {
  max-width: 960px;
}

.placeholder-ledger {
  display: grid;
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--line-soft);
}

.placeholder-ledger span {
  height: 44px;
  background:
    linear-gradient(90deg, var(--accent-soft) 0 22%, transparent 22%),
    linear-gradient(90deg, transparent 0 42%, color-mix(in srgb, var(--bg-inset), transparent 20%) 42% 74%, transparent 74%),
    var(--bg-surface);
}
</style>
