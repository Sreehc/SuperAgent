<template>
  <section class="placeholder-console workspace-page">
    <header class="workspace-strip">
      <div class="workspace-title">
        <p class="section-label">Runtime configuration</p>
        <h1>设置</h1>
        <p>模型、RAG、Rerank、Agent 和工具运行时配置。</p>
      </div>
      <span class="status-chip">scoped edit</span>
    </header>
    <div class="config-preview" aria-hidden="true">
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

.config-preview {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr) 220px;
  gap: 1px;
  overflow: hidden;
  min-height: 220px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--line-soft);
}

.config-preview span {
  background:
    repeating-linear-gradient(to bottom, transparent 0 39px, color-mix(in srgb, var(--line-soft), transparent 22%) 39px 40px),
    var(--bg-surface);
}

.config-preview span:nth-child(2) {
  background:
    linear-gradient(var(--accent-soft), var(--accent-soft)) top left / 100% 46px no-repeat,
    repeating-linear-gradient(to bottom, transparent 0 55px, color-mix(in srgb, var(--line-soft), transparent 22%) 55px 56px),
    var(--bg-surface);
}

@media (max-width: 820px) {
  .config-preview {
    grid-template-columns: 1fr;
  }
}
</style>
