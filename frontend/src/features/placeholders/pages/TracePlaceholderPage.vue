<template>
  <section class="placeholder-console workspace-page">
    <header class="workspace-strip">
      <div class="workspace-title">
        <p class="section-label">Observability stream</p>
        <h1>Trace</h1>
        <p>问答链路、检索、模型调用和 Agent 执行诊断。</p>
      </div>
      <span class="status-chip">capture</span>
    </header>
    <div class="trace-preview" aria-hidden="true">
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

.trace-preview {
  display: grid;
  gap: 8px;
}

.trace-preview span {
  position: relative;
  height: 46px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background:
    linear-gradient(90deg, var(--accent-soft) 0 4px, transparent 4px),
    linear-gradient(90deg, transparent 0 54%, color-mix(in srgb, var(--bg-inset), transparent 20%) 54% 72%, transparent 72%),
    var(--bg-surface);
}

.trace-preview span:nth-child(3) {
  background:
    linear-gradient(90deg, var(--hot-soft) 0 4px, transparent 4px),
    linear-gradient(90deg, transparent 0 54%, color-mix(in srgb, var(--hot-soft), transparent 25%) 54% 72%, transparent 72%),
    var(--bg-surface);
}
</style>
