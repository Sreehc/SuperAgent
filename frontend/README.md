# Frontend Baseline

阶段 0 只保留前端目录骨架，不提前生成 Vue 业务代码。

当前约定：

- 前端技术栈固定为 Vue 3 + Vite + TypeScript + Pinia。
- 本地开发服务默认使用 `http://localhost:5173`。
- API 基地址和 SSE 超时通过 `./.env.example` 配置。
- 页面与模块划分以后续阶段按 [docs/01-technical-design.md](/Users/cheers/Desktop/workspace/SuperAgent/docs/01-technical-design.md) 落地。

已预留目录：

- `src`
- `public`

下一阶段从这里继续：

1. 初始化 Vite + Vue 3 + TypeScript 工程。
2. 接入 Vue Router、Pinia 和基础布局。
3. 实现登录态、路由守卫和 `/chat` 占位页。
