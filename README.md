# SuperAgent

SuperAgent 是一个面向企业内部知识问答与 Agent 工作台场景的 AI 应用平台。项目以“可追溯、可配置、可扩展”为核心目标，围绕多租户对话、知识库管理、文档处理、RAG 检索、Trace 观测、运行时配置、Agent 工具治理和部署评测构建端到端闭环。

当前仓库已经包含 Web 控制台、Backend API、独立 Agent Service、Sandbox Runner、插件 manifest、评测脚本和 GitHub Actions 部署/评测工作流。

## 项目定位

SuperAgent 不是简单的大模型 API 包装层，而是一个可继续演进的 AI 平台骨架：

- 对知识问答采用证据驱动的 RAG 链路，并保留引用、Trace 和无证据兜底。
- 对运行过程提供阶段级 Trace、模型调用、检索、Rerank、Agent Run、Tool Call 和 Checkpoint 观测能力。
- 对模型、RAG、Rerank、Agent 和 Tools 提供租户级运行时配置入口。
- 对 Agent 工具、Web Search、HTTP、Graph、Python Sandbox 等高风险能力提供环境变量、运行时设置和角色权限边界。
- 对本地开发、评测、服务器发布和前端体验迭代保留独立模块和文档入口。

## 核心能力

### 认证、租户与权限

- 登录、登出、Access Token 和 HttpOnly Refresh Cookie。
- 当前用户资料、租户列表、租户切换和租户成员管理。
- OWNER、ADMIN、MEMBER 角色控制；Trace、Settings、Tools、Governance 等管理页仅 OWNER/ADMIN 可访问。
- 默认本地/测试种子账号：`admin / password123`、`member / password123`。

### 对话工作台

- 会话创建、切换、搜索、重命名、归档、删除。
- `/chat` 与 `/chat/:sessionId` 路由。
- SSE 流式回答、停止生成、恢复 Agent Run。
- Markdown 安全渲染、推荐追问、知识库选择、会话记忆策略选择。
- 引用来源侧栏、文档跳转、管理员 Trace 跳转。
- Agent Step、Tool Start/Result、Checkpoint、Resume 等 timeline 事件展示。

### 知识库、文档与图谱

- 知识库创建、编辑、发布、归档、删除。
- 文档上传、分类、标签、知识域、切块配置。
- 文档解析、切块、Embedding、任务日志、重处理、删除。
- 文档版本、解析文本、chunk 预览、元数据检查。
- 图谱同步状态、图谱摘要、图谱详情和 graph rebuild。

### RAG 检索链路

- 会话记忆组装、问题理解、问题改写和子问题拆分。
- pgvector 向量检索与 PostgreSQL 全文/关键词检索。
- RRF 融合、邻近 chunk 扩展、版本一致性控制。
- 可选 Rerank、证据预算控制、强制引用和无证据短路。
- Retrieval API 支持 query、knowledge base、domain、chunking profile、category、tags、topK 等过滤条件。

### Runtime Settings、Trace 与治理

- Model、RAG、Rerank、Agent、Tools 五类运行时设置。
- OWNER-only 敏感字段编辑与脱敏展示。
- Trace 列表过滤、exchange 详情、stage timeline、model calls、retrievals、reranks。
- Agent Run、Agent Steps、Tool Calls、Checkpoints、Resume Chain 观测。
- Tools Console 管理 plugin registry、plugin enable/disable、recent tool calls、manifest permissions、secret refs。
- Governance Console 管理 knowledge domains、chunking profiles 和 graph document entrypoints。

### Agent Service、Tools 与 Sandbox

- `agent-service/` 是独立 Spring Boot 服务，默认端口 `18081`。
- Agent internal endpoints 支持 run、SSE stream、resume、cancel。
- `plugins/core-tools/manifest.json` 声明核心工具：`knowledge.search`、`web.search`、`web.fetch`、`http.request`、`graph.query`、`python.sandbox`。
- `http.request` 与 `python.sandbox` 属于 high-risk tool，需要显式租户绑定和 OWNER/ADMIN 权限。
- `sandbox-runner/` 是 FastAPI 服务，提供 `/health` 与 `/internal/sandbox/execute`，用于隔离 Python 执行。

## 技术栈

### Backend API

- Java 21
- Spring Boot 3.3.5
- Maven Wrapper
- PostgreSQL、Flyway、pgvector、pg_trgm
- Redis
- Kafka
- MinIO
- Apache Tika
- Neo4j Driver
- springdoc OpenAPI、Actuator

### Agent Service

- Java 21
- Spring Boot
- PostgreSQL
- Plugin manifest loader
- Tavily Web Search 集成
- Neo4j Graph Tool 集成
- Sandbox Runner client

### Sandbox Runner

- Python 3
- FastAPI
- Uvicorn
- Pydantic
- 隔离 Python 执行、禁用网络、限制 CPU/内存/输出/文件大小

### Frontend

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

## 架构概览

```text
Vue Web App
  -> Backend API (/api/v1)
    -> Auth / Tenant / Runtime Settings
    -> Conversation / SSE / Trace
    -> Knowledge / Document / Retrieval / Rerank
    -> PostgreSQL / pgvector / pg_trgm
    -> Redis / Kafka / MinIO / Neo4j
    -> Agent Service (optional, http://localhost:18081)
      -> Plugin Registry / Core Tools
      -> Tavily Web Search / HTTP Tool / Graph Tool
      -> Sandbox Runner (optional, http://localhost:18082)

GitHub Actions
  -> rag-eval / agent-eval
  -> deploy release package
    -> backend/app.jar
    -> agent-service/app.jar
    -> frontend/
    -> sandbox-runner/
    -> plugins/
```

仓库采用模块化单体 + 独立 Agent Service 的结构，优先保证本地可运行和端到端闭环，同时为工具执行、图谱能力和多环境部署保留边界。

## 目录结构

```text
SuperAgent/
├── backend/            # Spring Boot Backend API
├── agent-service/      # 独立 Agent Service 与工具执行编排
├── sandbox-runner/     # FastAPI Python sandbox runner
├── frontend/           # Vue 3 Web 控制台
├── plugins/            # 工具插件 manifest，例如 core-tools
├── eval/               # RAG/Agent 评测套件定义
├── scripts/            # rag_eval.py、agent_eval.py 等脚本
├── artifacts/          # 评测输出摘要目录
├── infra/              # 本地依赖、systemd、Nginx、部署脚本
├── docs/               # PRD、技术设计、API、开发指南和运行手册
├── .github/workflows/  # deploy、rag-eval、agent-eval workflows
└── README.md
```

## 当前页面范围

已完成的主要前端路由：

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

其中 `/traces`、`/settings`、`/tools`、`/governance` 以及对应详情页为管理控制台能力，当前面向 OWNER/ADMIN 角色开放。

## 当前边界

- 仓库默认不提供强制 Docker Compose 编排，优先复用本机或服务器已有 PostgreSQL、Redis、MinIO、Kafka、Neo4j 等依赖。
- 外部工具、图谱查询、HTTP 请求和代码执行默认受环境变量与运行时设置控制；生产环境应显式配置白名单、密钥和租户绑定。
- 部署脚本只发布应用产物并重启已有 systemd 服务，不创建数据库、Redis、MinIO、Kafka、Neo4j、Nginx 或生产 env 文件。
- `local-deterministic` embedding 与 inline document processing 仅用于本地/E2E 闭环，不代表生产召回质量。

## 本地开发前提

- JDK 21
- Node.js 20+ 与 npm
- Python 3（需要运行 Sandbox Runner 时）
- PostgreSQL，并启用 `vector`、`pg_trgm`
- MinIO
- Redis 建议可用
- Kafka 在需要真实异步文档处理时启用
- Neo4j 在需要图谱能力或 graph tool 时启用

## 环境变量

建议先准备：

1. `backend/.env.example`
2. `frontend/.env.example` -> `frontend/.env.local`

### Backend API

常用变量：

- `APP_PORT`
- `POSTGRES_URL`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `REDIS_URL`
- `REDIS_ENABLED`
- `MINIO_ENDPOINT`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `MINIO_BUCKET`
- `KAFKA_BOOTSTRAP_SERVERS`
- `DOCUMENT_TASK_TOPIC`
- `KAFKA_ENABLED`
- `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED`
- `OPENAI_COMPATIBLE_BASE_URL`
- `OPENAI_COMPATIBLE_API_KEY`
- `CHAT_PROVIDER`
- `CHAT_MODEL`
- `EMBEDDING_PROVIDER`
- `EMBEDDING_MODEL`
- `EMBEDDING_DIMENSION`
- `RERANK_ENABLED`
- `RAG_*`
- `JWT_SECRET`
- `ACCESS_TOKEN_TTL_SECONDS`
- `REFRESH_TOKEN_TTL_SECONDS`
- `AGENT_SERVICE_BASE_URL`
- `WEB_SEARCH_ENABLED`
- `HTTP_TOOL_ENABLED`
- `GRAPH_TOOL_ENABLED`
- `CODE_EXECUTION_ENABLED`
- `NEO4J_URI`
- `NEO4J_USERNAME`
- `NEO4J_PASSWORD`
- `GRAPH_ENABLED`

本地闭环验证常用设置：

```bash
KAFKA_ENABLED=false
INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true
EMBEDDING_PROVIDER=local-deterministic
```

### Agent Service

常用变量：

- `AGENT_SERVICE_PORT`，默认 `18081`
- `POSTGRES_URL`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `PLUGIN_MANIFEST_ROOT`，默认 `../plugins`
- `SANDBOX_RUNNER_BASE_URL`，默认 `http://localhost:18082`
- `BACKEND_BASE_URL`，默认 `http://localhost:8080`
- `SEARCH_PROVIDER`
- `TAVILY_BASE_URL`
- `TAVILY_API_KEY`
- `TAVILY_DEFAULT_MAX_RESULTS`
- `WEB_FETCH_MAX_SUMMARY_CHARS`
- `WEB_FETCH_MAX_BODY_CHARS`
- `GRAPH_ENABLED`
- `NEO4J_URI`
- `NEO4J_USERNAME`
- `NEO4J_PASSWORD`

### Sandbox Runner

常用变量：

- `SANDBOX_PORT`，直接运行默认 `18082`
- `SANDBOX_PYTHON_BIN`
- `SANDBOX_MAX_CODE_LENGTH`
- `SANDBOX_MAX_MEMORY_BYTES`
- `SANDBOX_MAX_FILE_BYTES`

### Frontend

常用变量：

- `VITE_API_BASE_URL`
- `VITE_SSE_TIMEOUT_SECONDS`
- `VITE_APP_NAME`

## 本地启动

### 1. 准备依赖

确保以下服务按需可用：

- PostgreSQL / pgvector / pg_trgm
- MinIO
- Redis
- Kafka 可选
- Neo4j 可选

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
POSTGRES_PASSWORD=postgres \
KAFKA_ENABLED=false \
INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true \
EMBEDDING_PROVIDER=local-deterministic \
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
```

### 3. 可选：启动 Agent Service

当需要验证 Agent Run、Tools、Web Search、Graph Tool 或 Python Sandbox 调用链路时启动：

```bash
cd agent-service
mvn spring-boot:run
```

默认端口：`18081`。

### 4. 可选：启动 Sandbox Runner

当需要启用 `python.sandbox` 工具时启动：

```bash
cd sandbox-runner
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python app.py
```

直接运行默认端口：`18082`；`infra/systemd/superagent-sandbox-runner.service` 模板使用 `127.0.0.1:18122`。

### 5. 启动前端

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

### 6. 访问应用

- 前端默认地址：`http://localhost:5173`
- 示例联调地址：`http://127.0.0.1:4173`
- Backend 默认地址：`http://localhost:8080`
- Backend 示例联调地址：`http://127.0.0.1:18080`
- Swagger UI：`http://localhost:8080/swagger-ui.html`

默认本地/测试种子账号：

- `admin / password123`
- `member / password123`

## 验证命令

### 后端

```bash
cd backend
./mvnw -q -DskipTests compile
./mvnw -q test
curl http://localhost:8080/actuator/health
```

### 前端

```bash
cd frontend
npm run typecheck
npm test
npm run build
npm run e2e
```

### RAG / Agent 评测

评测脚本会写入 `artifacts/*.json` 摘要文件：

```bash
python3 scripts/rag_eval.py
python3 scripts/agent_eval.py
```

对应 GitHub Actions：

- `.github/workflows/rag-eval.yml`
- `.github/workflows/agent-eval.yml`
- `.github/workflows/deploy.yml`

## 相关文档

- [文档索引](./docs/README.md)
- [PRD](./docs/prd.md)
- [技术设计](./docs/01-technical-design.md)
- [数据库设计](./docs/02-database-design.md)
- [API 设计](./docs/03-api-design.md)
- [前端 UX 规格](./docs/05-frontend-ux-spec.md)
- [本地开发环境](./docs/06-local-development.md)
- [RAG 运行手册](./docs/09-rag-runtime-runbook.md)
- [前端 Taste 重构计划](./docs/10-frontend-taste-rebuild-plan.md)
- [前端高级视觉重构计划](./docs/11-frontend-premium-overhaul-plan.md)
- [Backend README](./backend/README.md)
- [Frontend README](./frontend/README.md)
- [Infra README](./infra/README.md)

## 运行说明

- 生产级文档处理建议使用 Kafka 异步链路；本地允许用 inline processing 验证闭环。
- Refresh Token 通过后端 HttpOnly Cookie 传输，前端不会在 `localStorage` 中保存长期 refresh token。
- 外部工具默认关闭，需要管理员显式配置环境变量、运行时设置和插件/工具绑定后才可启用。
- 高风险工具如 `http.request`、`python.sandbox` 应结合域名白名单、租户绑定、角色权限和审计记录使用。
