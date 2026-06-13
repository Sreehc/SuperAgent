# Frontend

前端基于 `Vue 3 + Vite + TypeScript + Pinia + Vue Router`，是 SuperAgent 的 Web 控制台。当前已经接通登录、会话工作台、知识库、文档详情、Trace、Settings、Tools 和 Governance 等页面。

## 技术栈

- Vue 3
- Vite
- TypeScript
- Pinia
- Vue Router
- Axios
- marked + DOMPurify
- Phosphor Icons
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
- `/forbidden`

## 权限与路由守卫

- `/login` 为公开登录页。
- 业务页面需要已登录用户。
- `/chat`、`/knowledge`、`/documents/:documentId` 面向 OWNER、ADMIN、MEMBER。
- `/traces`、`/settings`、`/tools`、`/governance` 面向 OWNER、ADMIN。
- 无权限访问管理路由时跳转 `/forbidden`。
- Axios 请求会自动附加 Access Token 和 `X-Tenant-Id`，并启用 `withCredentials` 发送 Refresh Cookie。

## 功能范围

### Chat Workspace

- 会话列表、搜索、创建、选择、重命名、归档、删除。
- SSE 流式回答、停止生成、恢复 Agent Run。
- 知识库选择和记忆策略选择：`NONE`、`SLIDING_WINDOW`、`SUMMARY_WINDOW`、`SUMMARY_PLUS_WINDOW`。
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

### Traces

- Trace 列表支持 status、execution mode、user ID 和分页过滤。
- Trace 详情展示 exchange 状态、stage timeline、model calls、retrievals、reranks。
- 当 exchange 关联 Agent Run 时展示 agent steps、tool calls、checkpoints 和 resume chain。

### Tools Console

- Plugin registry 与插件启用/禁用状态。
- Recent tool calls、request/response summary、metadata、latency、errors。
- Manifest permissions、enabled tools、secret refs 和 installation config。

### Governance Console

- Knowledge domains 创建和编辑。
- Chunking profiles 创建和编辑，包括 strategy、default flag 和 config JSON。
- Graph document entrypoints，可跳转文档详情或触发 graph rebuild。

## 本地开发

1. 复制 `./.env.example` 为 `.env.local`。
2. 执行 `npm install`。
3. 执行 `npm run dev`。
4. 打开 `http://localhost:5173`。

`.env.example` 中的关键变量：

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_SSE_TIMEOUT_SECONDS=120
VITE_APP_NAME=SuperAgent
```

`VITE_API_BASE_URL` 未配置时回退到 `http://localhost:8080/api/v1`。

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
```

## E2E 说明

- 先手动启动后端 `18080` 和前端 `4173`。
- 后端本地验证建议加上 `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true EMBEDDING_PROVIDER=local-deterministic`，这样上传文档后能直接完成解析、切块和本地 deterministic embedding。
- 默认本地/测试账号：`admin / password123`、`member / password123`。
- Playwright 使用本机已安装的 `Google Chrome`。
- 当前 E2E 覆盖登录守卫、文档上传、发起 RAG 对话、停止生成、Trace 查看、文档详情、引用来源跳转、设置页保存，以及登录后 refresh token 不落入 `localStorage`，并在桌面/平板宽度下运行。

## 联调说明

- Refresh Token 通过后端 HttpOnly Cookie 传输，前端不会在 `localStorage` 中保存长期 refresh token。
- 当前 Axios client 会使用 `withCredentials: true`，并在请求中附加 `Authorization` 与 `X-Tenant-Id`。
- 当后端未开启 Kafka 且未启用 `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true` 时，上传后的文档会停留在 `uploaded` 或 `pending` 状态。
- `EMBEDDING_PROVIDER=local-deterministic` 只适合本地联调，不代表真实召回质量。
- SSE 请求超时由 `VITE_SSE_TIMEOUT_SECONDS` 控制。
