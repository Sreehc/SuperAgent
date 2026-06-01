# Frontend Stage 3

当前前端已经完成阶段 3 的脚手架与登录态基线：

- 技术栈固定为 `Vue 3 + Vite + TypeScript + Pinia + Vue Router`
- 已接入统一 API Client、`Authorization` 注入和 `401` 清理
- 已完成 `/login`、受保护路由、基础工作台布局和 `/chat` 占位页
- 已完成基于当前租户角色的菜单裁剪，`MEMBER` 不显示管理入口

本地开发：

1. 复制 `./.env.example` 为 `.env.local`
2. 在 `frontend/` 下执行 `npm install`
3. 执行 `npm run dev`
4. 默认访问 `http://localhost:5173`
5. 如果未提供 `.env.local`，开发环境会回退到 `http://localhost:8080/api/v1`

环境变量：

- `VITE_API_BASE_URL`：后端 API 基地址，默认 `http://localhost:8080/api/v1`
- `VITE_SSE_TIMEOUT_SECONDS`：当前统一请求超时秒数
- `VITE_APP_NAME`：页面标题和品牌名称

当前目录重点：

- `src/app`：应用入口、路由和主 Shell
- `src/api`：HTTP Client、响应类型和本地存储
- `src/features/auth`：登录页、认证类型、认证状态管理
- `src/features/placeholders`：阶段 3 占位页

下一阶段建议直接从 `/chat` 的会话列表、消息时间线和流式响应接入开始。
