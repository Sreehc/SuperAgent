# SuperAgent

SuperAgent 是一个面向租户的 AI 工作台，当前仓库已经接通认证、知识库、文档处理、对话、Trace 管理和设置页基础闭环。

## 目录

- `backend/`：Spring Boot 3.3 + Java 21
- `frontend/`：Vue 3 + Vite + Pinia
- `infra/`：本地依赖约定和后续脚本入口
- `docs/`：PRD、技术设计、数据库/API/UX 规格和阶段任务

## 本地前提

- JDK 21
- Node.js 20+
- npm 10+ 或 pnpm
- 本地 Docker 中可用的 PostgreSQL/pgvector、MinIO
- Kafka 仅在需要异步文档处理时开启；默认 `KAFKA_ENABLED=false`

## 环境变量

1. 复制 `backend/.env.example` 为你自己的后端环境文件。
2. 复制 `frontend/.env.example` 为 `frontend/.env.local`。
3. 核对 PostgreSQL、MinIO、模型服务和 JWT 配置。本地只做闭环验证时，可改用 `EMBEDDING_PROVIDER=local-deterministic`，避免依赖外部 embedding 服务。

关键变量：

- 后端：`POSTGRES_URL`、`POSTGRES_USER`、`POSTGRES_PASSWORD`、`MINIO_*`、`OPENAI_API_KEY`
- 前端：`VITE_API_BASE_URL`

## 本地启动

1. 启动 PostgreSQL/pgvector 和 MinIO。
2. 在 `backend/` 下执行 `./mvnw spring-boot:run`
3. 在 `frontend/` 下执行 `npm install`
4. 在 `frontend/` 下执行 `npm run dev`
5. 打开 `http://localhost:5173`

默认种子账号：

- `admin / password123`
- `member / password123`

## 验证命令

```bash
cd backend && ./mvnw test
cd frontend && npm run build
cd backend && POSTGRES_URL=jdbc:postgresql://localhost:5432/superagent_test POSTGRES_USER=postgres POSTGRES_PASSWORD=root KAFKA_ENABLED=false INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true EMBEDDING_PROVIDER=local-deterministic ./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
cd frontend && VITE_API_BASE_URL=http://127.0.0.1:18080/api/v1 npm run dev -- --host 127.0.0.1 --port 4173
cd frontend && npm run e2e
```

## 已实现页面

- `/login`
- `/chat`
- `/knowledge`
- `/documents/:documentId`
- `/traces`
- `/traces/:exchangeId`
- `/settings`

## 已知问题

- 生产环境仍应优先使用 Kafka 异步文档处理；`INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true` 主要用于本地验证闭环，上传耗时会显著增加。
- `EMBEDDING_PROVIDER=local-deterministic` 仅建议用于本地联调和 E2E，不能替代真实 embedding 质量。
- 当前设置页已支持运行时配置读写和审计，但模型供应商的真实热更新仍以现有客户端实现范围为准。
