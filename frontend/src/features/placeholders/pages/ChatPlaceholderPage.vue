<template>
  <section class="page-card page-card--chat">
    <div class="page-card__hero">
      <p class="page-card__eyebrow">/chat</p>
      <h2>登录后的默认落点已经接通。</h2>
      <p>
        这里保留阶段 3 所需的对话工作台占位结构，后续阶段再接会话列表、消息时间线和
        SSE。
      </p>
    </div>

    <div class="chat-grid">
      <article class="placeholder-panel">
        <h3>会话侧栏</h3>
        <ul>
          <li>新建会话按钮</li>
          <li>搜索框占位</li>
          <li>按时间分组的会话列表占位</li>
        </ul>
      </article>

      <article class="placeholder-panel">
        <h3>消息工作区</h3>
        <p>当前阶段只验证守卫、布局和登录态，消息流在下一阶段实现。</p>
      </article>

      <article class="placeholder-panel">
        <h3>右侧面板</h3>
        <p>引用来源和 Trace 入口已预留，但不展开具体细节。</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.page-card {
  padding: 2rem;
  border-radius: calc(var(--radius-lg) + 4px);
  background: var(--bg-panel);
  border: 1px solid var(--line-soft);
  box-shadow: var(--shadow-card);
}

.page-card--chat {
  background:
    linear-gradient(180deg, rgba(255, 252, 247, 0.96), rgba(255, 247, 238, 0.96)),
    radial-gradient(circle at top right, rgba(199, 109, 63, 0.14), transparent 34%);
}

.page-card__hero h2 {
  margin: 0.3rem 0 0.8rem;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
  font-size: clamp(2rem, 4vw, 3rem);
}

.page-card__eyebrow {
  margin: 0;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 0.74rem;
}

.chat-grid {
  display: grid;
  grid-template-columns: 0.9fr 1.2fr 0.9fr;
  gap: 1rem;
  margin-top: 1.5rem;
}

.placeholder-panel {
  min-height: 240px;
  padding: 1.2rem;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(31, 32, 38, 0.08);
}

.placeholder-panel h3 {
  margin-top: 0;
}

@media (max-width: 980px) {
  .chat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
