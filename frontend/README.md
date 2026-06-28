# Frontend

前端基于 `React 19 + Vite 8 + TypeScript + React Router + Zustand + TanStack Query/Table + assistant-ui`，是 SuperAgent 的 Web 控制台。

## 技术栈

- React 19
- Vite 8
- TypeScript
- React Router 7
- Zustand
- TanStack Query
- TanStack Table
- assistant-ui
- Axios
- marked + DOMPurify
- Phosphor Icons / lucide-react
- Vitest
- Playwright

## 页面范围

- `/login`
- `/chat`
- `/chat/:sessionId`
- `/knowledge`
- `/knowledge/:knowledgeBaseId`
- `/documents/:documentId`
- `/traces`
- `/traces/:exchangeId`
- `/settings`
- `/tools`
- `/governance`
- `/members`
- `/audit-logs`
- `/feedback`
- `/evals`
- `/evals/:suiteId`
- `/evals/runs/:runId`
- `/forbidden`

## 权限与路由守卫

- `/login` 为公开登录页。
- 业务页面需要已登录用户。
- `/chat`、`/knowledge`、`/documents/:documentId` 面向 OWNER、ADMIN、MEMBER。
- `/traces`、`/settings`、`/tools`、`/governance`、`/members`、`/audit-logs`、`/feedback`、`/evals` 面向 OWNER、ADMIN。
- 无权限访问管理路由时跳转 `/forbidden`。
- Axios 请求会自动附加 Access Token 和 `X-Tenant-Id`，并启用 `withCredentials` 发送 Refresh Cookie。

## 功能范围

### Chat Workspace

- 会话列表、搜索、创建、选择、重命名、归档、删除。
- assistant-ui 接入现有后端 SSE 协议。
- SSE 流式回答、停止生成、恢复 Agent Run。
- 知识库选择、执行模式选择和记忆策略选择。
- Markdown 安全渲染。
- 推荐追问、引用来源侧栏、文档跳转。
- 管理员 Trace 跳转。
- Agent Step、Tool Start/Result、Checkpoint、Resume 等 timeline 事件展示。

### Knowledge / Document

- 知识库列表、创建、编辑、发布、归档、删除。
- 文档上传，支持 title、category、tags、knowledge domain、chunking profile。
- 文档过滤、状态展示、任务日志。
- 文档详情查看 parsed text、chunks、metadata、versions、graph summary/details。
- 文档 reprocess、graph rebuild、delete 管理动作。

### Settings

- Model、RAG、Rerank、Agent、Tools 五个配置页签。
- Model/Rerank 密钥字段脱敏展示。
- OWNER 可编辑敏感配置；ADMIN 可查看或编辑非敏感运行时配置。
- Agent/Tools 配置包括 web search、HTTP、graph、code execution、超时、allowed domains 等开关。

### Traces / Tools / Governance

- Trace 列表支持 status、execution mode、user ID 和分页过滤。
- Trace 详情展示 exchange 状态、stage timeline、model calls、retrievals、reranks。
- 当 exchange 关联 Agent Run 时展示 agent steps、tool calls、checkpoints 和 resume chain。
- Tools Console 管理 plugin registry、plugin enable/disable、recent tool calls、manifest permissions、secret refs。
- Governance Console 管理 knowledge domains、chunking profiles 和 graph document entrypoints。

### Admin Consoles

- Members：成员、邀请、角色、停用/恢复、移除。
- Audit：审计日志查询和详情。
- Feedback：反馈筛选、详情、关联会话/Trace。
- Evals：评测集、用例、运行记录和 case 结果。

## 本地开发

1. 复制 `./.env.example` 为 `.env.local`。
2. 执行 `npm install`。
3. 执行 `npm run dev`。
4. 打开 `http://localhost:5173`。

关键变量：

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_SSE_TIMEOUT_SECONDS=120
VITE_APP_NAME=SuperAgent
```

如果后端运行在 `18080`：

```bash
VITE_API_BASE_URL=http://127.0.0.1:18080/api/v1 npm run dev -- --host 127.0.0.1 --port 4173
```

## 常用命令

```bash
npm run dev
npm run build
npm run preview
npm run typecheck
npm test
npm run e2e
npm run test:visual
```

## E2E 说明

- 先启动后端和前端 preview/dev server。
- 后端本地验证建议加上 `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true EMBEDDING_PROVIDER=local-deterministic`。
- 默认本地/测试账号：`admin / password123`、`member / password123`。
- Playwright 使用本机已安装的 Google Chrome。

## 联调说明

- Refresh Token 通过后端 HttpOnly Cookie 传输，前端不会在 `localStorage` 中保存长期 refresh token。
- Axios client 使用 `withCredentials: true`，并在请求中附加 `Authorization` 与 `X-Tenant-Id`。
- 当后端未开启 Kafka 且未启用 `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true` 时，上传后的文档不会自动完成完整处理。
- `EMBEDDING_PROVIDER=local-deterministic` 只适合本地联调，不代表真实召回质量。
- SSE 请求超时由 `VITE_SSE_TIMEOUT_SECONDS` 控制。
