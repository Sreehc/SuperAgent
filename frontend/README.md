# Frontend

前端基于 `Vue 3 + Vite + TypeScript + Pinia + Vue Router`，当前已经接通登录、知识库、文档详情、会话、Trace 和设置页。

## 页面范围

- `/login`
- `/chat`
- `/knowledge`
- `/knowledge/:knowledgeBaseId`
- `/documents/:documentId`
- `/traces`
- `/traces/:exchangeId`
- `/settings`
- `/tools`
- `/governance`

## 本地开发

1. 复制 `./.env.example` 为 `.env.local`
2. 执行 `npm install`
3. 执行 `npm run dev`
4. 打开 `http://localhost:5173`

默认会调用 `VITE_API_BASE_URL`，未配置时回退到 `http://localhost:8080/api/v1`

## 常用命令

```bash
npm run build
VITE_API_BASE_URL=http://127.0.0.1:18080/api/v1 npm run dev -- --host 127.0.0.1 --port 4173
npm run e2e
```

## E2E 说明

- 先手动启动后端 `18080` 和前端 `4173`。
- 后端本地验证建议加上 `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true EMBEDDING_PROVIDER=local-deterministic`，这样上传文档后能直接完成解析、切块和本地 deterministic embedding。
- Playwright 使用本机已安装的 `Google Chrome`。
- 当前 E2E 覆盖登录守卫、文档上传、发起 RAG 对话、停止生成、Trace 查看、文档详情、引用来源跳转、设置页保存，以及登录后 refresh token 不落入 `localStorage`，并在桌面/平板宽度下运行。

## 已知问题

- 当后端未开启 Kafka 且未启用 `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true` 时，上传后的文档会停留在 `uploaded` 或 `pending` 状态。
- `EMBEDDING_PROVIDER=local-deterministic` 只适合本地联调，不代表真实召回质量。
