# Frontend

前端基于 `Vue 3 + Vite + TypeScript + Pinia + Vue Router`，当前已经接通登录、知识库、文档详情、会话、Trace 和设置页。

## 页面范围

- `/login`
- `/chat`
- `/knowledge`
- `/documents/:documentId`
- `/traces`
- `/traces/:exchangeId`
- `/settings`

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
- Playwright 使用本机已安装的 `Google Chrome`。
- 当前 E2E 覆盖登录守卫、设置页可见性、文档上传、会话发起和 Trace 查看。

## 已知问题

- 对话页还没有直接选择知识库的控件，测试通过后端 API 先创建带知识库的会话。
- 当后端未消费文档任务时，上传后的文档会停留在 `uploaded` 或 `pending` 状态。
