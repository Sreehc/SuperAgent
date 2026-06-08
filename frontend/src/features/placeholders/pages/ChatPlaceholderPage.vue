<template>
  <section class="placeholder-console workspace-page">
    <header class="workspace-strip placeholder-hero">
      <div class="workspace-title">
        <p class="section-label">Conversation matrix</p>
        <h1>对话工作台</h1>
        <p>会话、消息、引用来源和 Agent 运行状态会在这里汇总。</p>
      </div>
      <div class="placeholder-signal" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
    </header>
    <section class="placeholder-grid">
      <article>
        <strong>会话</strong>
        <p>新建、搜索、重命名、归档。</p>
      </article>
      <article>
        <strong>消息</strong>
        <p>流式回答、Markdown、安全渲染。</p>
      </article>
      <article>
        <strong>上下文</strong>
        <p>引用来源、文档跳转、Trace 入口。</p>
      </article>
    </section>
  </section>
</template>

<style scoped>
.placeholder-console {
  max-width: 980px;
}

.placeholder-hero {
  align-items: flex-start;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--bg-surface), var(--accent-soft) 34%), var(--bg-surface)),
    var(--bg-surface);
}

.placeholder-signal {
  display: grid;
  grid-template-columns: repeat(3, 34px);
  gap: 5px;
  padding-top: 5px;
}

.placeholder-signal span {
  height: 54px;
  border: 1px solid color-mix(in srgb, var(--accent), transparent 62%);
  border-radius: var(--radius-1);
  background:
    linear-gradient(to top, var(--accent-soft), transparent),
    repeating-linear-gradient(to top, transparent 0 7px, color-mix(in srgb, var(--accent), transparent 72%) 7px 8px);
}

.placeholder-signal span:nth-child(2) {
  height: 38px;
  margin-top: 16px;
}

.placeholder-signal span:nth-child(3) {
  height: 46px;
  margin-top: 8px;
}

.placeholder-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--line-soft);
}

.placeholder-grid article {
  display: grid;
  gap: 6px;
  min-height: 128px;
  padding: 16px;
  background: var(--bg-surface);
}

.placeholder-grid strong {
  font-size: 13px;
}

.placeholder-grid p {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.55;
}

@media (max-width: 820px) {
  .placeholder-grid {
    grid-template-columns: 1fr;
  }
}
</style>
