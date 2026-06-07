<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-kicker">/chat</p>
        <h2>对话工作台</h2>
        <p>会话、消息、引用来源和 Agent 运行状态会在这里汇总。</p>
      </div>
    </header>
    <section class="placeholder-grid">
      <article class="panel"><h3>会话</h3><p>新建、搜索、重命名、归档。</p></article>
      <article class="panel"><h3>消息</h3><p>流式回答、Markdown、安全渲染。</p></article>
      <article class="panel"><h3>上下文</h3><p>引用来源、文档跳转、Trace 入口。</p></article>
    </section>
  </section>
</template>

<style scoped>
.placeholder-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.panel p {
  margin-bottom: 0;
}

@media (max-width: 820px) {
  .placeholder-grid {
    grid-template-columns: 1fr;
  }
}
</style>
