# SuperAgent

SuperAgent 是一个面向企业内部知识问答场景的 AI 应用平台。项目以“可追溯、可配置、可扩展”为核心目标，围绕多租户对话工作台、知识库管理、文档异步处理、RAG 检索链路和 Trace 观测构建完整闭环。

当前仓库实现重点是 MVP 的 Web 控制台和后端基础能力：

- 支持登录、租户上下文和角色权限控制。
- 支持会话管理、SSE 流式回答、停止生成和引用来源展示。
- 支持知识库、文档上传、解析、切块、向量化和重处理。
- 支持向量检索、关键词检索、RRF 融合、可插拔 Rerank 和无证据兜底。
- 支持 Trace 查询、运行时设置和审计能力。

首版保留 Agent 执行模式扩展点，但不实现完整工具调用闭环。

## 项目定位

SuperAgent 不是简单的大模型 API 包装层，而是一个可继续演进的 AI 平台骨架：

- 对知识问答采用证据驱动的 RAG 链路。
- 对运行过程提供 Trace 和阶段级可观测能力。
- 对模型、检索和 Rerank 提供运行时配置入口。
- 对后续 Agent、服务拆分和多环境部署保留接口边界。

## 核心能力

### 对话工作台

- 会话创建、切换、重命名、归档、删除。
- 流式回答、停止生成、推荐追问。
- Markdown 安全渲染。
- 引用来源侧栏和 Trace 跳转。

### 知识库与文档

- 知识库创建、发布、归档、删除。
- 文档上传、状态流转、任务日志。
- 文档详情查看解析文本、切块和错误信息。
- 文档重处理与索引替换。

### RAG 检索链路

- 会话记忆组装。
- 问题改写与子问题拆分。
- pgvector 向量检索。
- PostgreSQL 全文检索。
- RRF 融合与可选 Rerank。
- 证据预算控制和无证据短路。

### 观测与配置

- Trace 列表与详情页。
- 模型、Embedding、RAG、Rerank 配置页。
- 敏感字段脱敏展示。
- 配置修改审计日志。

## 技术栈

### 后端

- Java 21
- Spring Boot 3.3
- PostgreSQL
- pgvector
- Redis
- Kafka
- MinIO
- Flyway
- Apache Tika

### 前端

- Vue 3
- Vite
- TypeScript
- Pinia
- Vue Router
- Axios
- Playwright
- Vitest

## 架构概览

```text
Vue Web App
  -> Spring Boot API
    -> Auth / Tenant
    -> Chat / SSE
    -> Knowledge / Document Worker
    -> RAG / Retrieval / Rerank
    -> Trace / Runtime Settings
    -> PostgreSQL / pgvector
    -> Redis
    -> Kafka
    -> MinIO
```

仓库采用模块化单体设计，优先保证本地可运行和端到端闭环，同时保留后续拆分空间。

## 目录结构

```text
SuperAgent/
├── backend/    # Spring Boot 后端
├── frontend/   # Vue 3 前端
├── infra/      # 本地环境说明和后续脚本入口
├── docs/       # PRD、技术设计、数据库/API/开发任务文档
└── README.md
```

## 当前实现范围

已完成的主要页面：

- `/login`
- `/chat`
- `/knowledge`
- `/knowledge/:knowledgeBaseId`
- `/documents/:documentId`
- `/traces`
- `/traces/:exchangeId`
- `/settings`

当前不包含的范围：

- Docker Compose 配套
- CI 配套
- 完整 Agent 工具调用闭环

## 本地开发前提

- JDK 21
- Node.js 20+
- npm 10+ 或 pnpm
- 本机已可用的 PostgreSQL/pgvector
- 本机已可用的 MinIO
- Redis 建议可用
- Kafka 在需要真实异步文档处理时启用

本仓库当前优先复用本机已有 Docker 服务，不额外生成 Compose。

## 环境变量

建议先准备：

1. `backend/.env.example`
2. `frontend/.env.example` -> `frontend/.env.local`

常用变量：

### 后端

- `POSTGRES_URL`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `REDIS_URL`
- `MINIO_ENDPOINT`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `MINIO_BUCKET`
- `OPENAI_API_KEY`
- `KAFKA_ENABLED`
- `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED`

### 前端

- `VITE_API_BASE_URL`

本地闭环验证常用设置：

```bash
KAFKA_ENABLED=false
INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true
EMBEDDING_PROVIDER=local-deterministic
```

说明：

- `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true` 适合本地联调，不适合作为生产方案。
- `EMBEDDING_PROVIDER=local-deterministic` 仅用于本地测试和 E2E，不能替代真实向量质量。

## 本地启动

### 1. 准备依赖

确保以下服务可用：

- PostgreSQL / pgvector
- MinIO
- Redis
- Kafka 可选

### 2. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

如果要使用本地闭环验证配置：

```bash
cd backend
POSTGRES_URL=jdbc:postgresql://localhost:5432/superagent_test \
POSTGRES_USER=postgres \
POSTGRES_PASSWORD=root \
KAFKA_ENABLED=false \
INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true \
EMBEDDING_PROVIDER=local-deterministic \
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

如果后端运行在 `18080`：

```bash
cd frontend
VITE_API_BASE_URL=http://127.0.0.1:18080/api/v1 npm run dev -- --host 127.0.0.1 --port 4173
```

### 4. 访问应用

- 前端默认地址：`http://localhost:5173`
- 示例联调地址：`http://127.0.0.1:4173`

默认种子账号：

- `admin / password123`
- `member / password123`

## 验证命令

### 后端

```bash
cd backend && ./mvnw test
```

### 前端类型检查

```bash
cd frontend && npm run typecheck
```

### 前端单元测试

```bash
cd frontend && npm test -- --run
```

### 前端 E2E

```bash
cd frontend && npm run e2e
```

## 相关文档

- [PRD](./docs/prd.md)
- [技术设计](./docs/01-technical-design.md)
- [数据库设计](./docs/02-database-design.md)
- [API 设计](./docs/03-api-design.md)
- [开发任务拆分](./docs/04-development-tasks.md)
- [本地开发环境](./docs/06-local-development.md)

## 已知说明

- 当前生产级文档处理建议使用 Kafka 异步链路，本地允许降级为同步处理以便验证闭环。
- 运行时设置页支持配置更新和字段级校验，但具体生效范围以当前客户端实现为准。
- 项目已经具备完整 RAG MVP 闭环，但完整 Agent 工具执行仍属于后续演进方向。
