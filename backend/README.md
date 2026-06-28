# Backend

后端基于 `Spring Boot 3.5.16 + Spring AI 1.1.8 + Java 21 + Flyway + PostgreSQL/pgvector`，提供 SuperAgent 的认证、租户、知识库、文档处理、会话、RAG、Trace、运行时设置、Agent 管理和治理 API。

## 技术栈

- Java 21
- Spring Boot 3.5.16
- Spring AI 1.1.8
- Maven Wrapper
- PostgreSQL、Flyway、pgvector、pg_trgm
- Redis
- Kafka
- MinIO
- Apache Tika
- Neo4j Driver
- springdoc OpenAPI
- Actuator

## 当前能力

### 认证、租户与系统入口

- 认证：`/api/v1/auth/login`、`/api/v1/auth/refresh`、`/api/v1/auth/logout`、`/api/v1/auth/me`
- 租户：`/api/v1/tenants`、`/api/v1/tenants/{tenantId}/switch`、`/api/v1/tenants/{tenantId}/members`
- 系统目录：`/api/v1/system/bootstrap`、`/api/v1/system/modules/{module}`

### 会话与流式回答

- 会话 CRUD：`/api/v1/conversations`
- 消息列表与 SSE 流式回答：`/api/v1/conversations/{sessionId}/messages/stream`
- 停止与恢复：`/api/v1/conversations/{sessionId}/stop`、`/api/v1/conversations/{sessionId}/resume`
- 支持记忆策略、知识库选择、引用来源、推荐追问和 Trace 关联。

### 知识库、文档与图谱

- 知识库：`/api/v1/knowledge-bases/*`
- 文档上传、详情、删除：`/api/v1/knowledge-bases/{knowledgeBaseId}/documents`、`/api/v1/documents/{documentId}`
- chunks、tasks、reprocess：`/api/v1/documents/{documentId}/chunks`、`/api/v1/documents/{documentId}/tasks`、`/api/v1/documents/{documentId}/reprocess`
- 文档版本：`/api/v1/documents/{documentId}/versions`
- 文档图谱：`/api/v1/documents/{documentId}/graph`、`/api/v1/documents/{documentId}/graph/rebuild`

### RAG 与检索

- 检索查询：`/api/v1/retrieval/query`
- 支持 query、knowledge base、knowledge domain、chunking profile、category、tags、topK 等过滤。
- 支持向量检索、全文/关键词检索、RRF 融合、邻近 chunk、版本一致性、可选 Rerank 和无证据兜底。

### Runtime Settings

- 模型配置：`/api/v1/admin/settings/model`
- RAG 配置：`/api/v1/admin/settings/rag`
- Rerank 配置：`/api/v1/admin/settings/rerank`
- Agent 配置：`/api/v1/admin/settings/agent`
- Tools 配置：`/api/v1/admin/settings/tools`

### Agent、Tools 与 Plugins 管理

- Agent Runs：`/api/v1/admin/agent-runs`、`/api/v1/admin/agent-runs/{runId}/detail`、`/api/v1/admin/agent-runs/by-exchange/{exchangeId}`
- Tool Calls：`/api/v1/admin/tool-calls`
- Plugins：`/api/v1/admin/plugins`、`/api/v1/admin/plugins/{pluginId}`
- Backend 通过 `AGENT_SERVICE_BASE_URL` 调用独立 `agent-service/`，工具执行、Web Search、HTTP、Graph、Sandbox 需要对应环境变量、运行时设置和插件绑定。

### Trace 与观测

- Trace 列表与详情：`/api/v1/admin/traces`、`/api/v1/admin/traces/{exchangeId}`
- 模型调用：`/api/v1/admin/model-calls`
- 检索与 Rerank 观测：`/api/v1/admin/retrievals`、`/api/v1/admin/reranks`
- 支持 exchange stage、model calls、retrieval items、rerank、agent run、tool calls、checkpoints 等链路排查。

### 知识治理

- 知识域：`/api/v1/admin/knowledge-domains`
- 切块配置：`/api/v1/admin/chunking-profiles`
- 与文档上传、版本、graph rebuild 联动。

### 健康检查与文档

- Actuator：`/actuator/health`
- Swagger UI：`/swagger-ui.html`
- OpenAPI JSON：`/v3/api-docs`

## 启动前提

- JDK 21
- PostgreSQL，并启用 `vector`、`pg_trgm`
- MinIO，用于原始文档存储
- Redis，默认启用，用于缓存、运行锁和停止信号
- Kafka 可选；生产异步文档链路建议启用，本地可关闭
- Neo4j 可选；图谱能力和 graph tool 需要启用
- Agent Service 可选；Agent Run 与工具执行链路需要启动 `agent-service/`

如需显式切换 Java 21：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## 关键环境变量

完整模板见 `backend/.env.example` 与 `backend/src/main/resources/application.yml`。

### 数据库、缓存与对象存储

- `POSTGRES_URL`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `REDIS_URL`
- `REDIS_ENABLED`
- `MINIO_ENDPOINT`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `MINIO_BUCKET`
- `MINIO_AUTO_CREATE_BUCKET`
- `UPLOAD_MAX_FILE_SIZE_BYTES`

### 文档处理与消息队列

- `KAFKA_BOOTSTRAP_SERVERS`
- `DOCUMENT_TASK_TOPIC`
- `KAFKA_ENABLED`
- `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED`
- `DOCUMENT_CHUNK_SIZE`
- `DOCUMENT_CHUNK_OVERLAP`
- `DOCUMENT_MAX_CHUNK_COUNT`

本地闭环常用：

```bash
KAFKA_ENABLED=false
INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true
```

### AI、Embedding、RAG 与 Rerank

- `OPENAI_COMPATIBLE_BASE_URL`
- `OPENAI_COMPATIBLE_API_KEY`
- `CHAT_PROVIDER`
- `CHAT_MODEL`
- `EMBEDDING_PROVIDER`
- `EMBEDDING_MODEL`
- `EMBEDDING_DIMENSION`
- `RERANK_ENABLED`
- `RERANK_PROVIDER`
- `RAG_REWRITE_ENABLED`
- `RAG_SUB_QUESTION_ENABLED`
- `RAG_VECTOR_TOP_K`
- `RAG_KEYWORD_TOP_K`
- `RAG_CANDIDATE_TOP_K`
- `RAG_RRF_K`
- `RAG_EVIDENCE_LIMIT`
- `RAG_MIN_RELEVANCE_SCORE`
- `RAG_FORCE_CITATION_ENABLED`

`EMBEDDING_PROVIDER=local-deterministic` 仅用于本地验证和 E2E，不代表生产召回质量。

当前 Chat 和 Embedding 调用通过 Spring AI `ChatModel` / `EmbeddingModel` 接入 OpenAI-compatible provider。Query Understanding 使用 Spring AI structured output。Rerank 仍保留自研 OpenAI-compatible 客户端。

### 安全、Cookie 与 CORS

- `JWT_SECRET`
- `ACCESS_TOKEN_TTL_SECONDS`
- `REFRESH_TOKEN_TTL_SECONDS`
- `REFRESH_COOKIE_NAME`
- `REFRESH_COOKIE_PATH`
- `REFRESH_COOKIE_DOMAIN`
- `REFRESH_COOKIE_SECURE`
- `REFRESH_COOKIE_SAME_SITE`
- `FRONTEND_ORIGIN_PRIMARY`
- `FRONTEND_ORIGIN_SECONDARY`
- `FRONTEND_ORIGIN_TERTIARY`
- `FRONTEND_ORIGIN_QUATERNARY`

### Agent、Tools 与 Graph

- `AGENT_SERVICE_BASE_URL`，默认 `http://localhost:18081`
- `AGENT_ENABLED_DEFAULT`
- `AGENT_MAX_MODEL_STEPS`
- `AGENT_MAX_TOOL_CALLS`
- `AGENT_CHECKPOINT_ENABLED`
- `WEB_SEARCH_ENABLED`，默认 `false`
- `HTTP_TOOL_ENABLED`，默认 `false`
- `GRAPH_TOOL_ENABLED`，默认 `false`
- `CODE_EXECUTION_ENABLED`，默认 `false`
- `TOOL_TIMEOUT_MS`
- `SEARCH_PROVIDER`
- `ALLOWED_HTTP_DOMAINS`
- `GRAPH_ENABLED`，默认 `false`
- `NEO4J_URI`
- `NEO4J_USERNAME`
- `NEO4J_PASSWORD`

## 数据库迁移与种子数据

Flyway 自动执行 `backend/src/main/resources/db/migration` 下的迁移：

- `V1__enable_postgresql_extensions.sql`：启用 `vector`、`pg_trgm`。
- `V2__create_core_schema.sql`：创建租户、用户、知识库、文档、chunk、embedding、会话、引用、Trace、Rerank、审计等核心表。
- `V3__create_indexes_and_triggers.sql`：创建 GIN/trigram/trace/audit 索引和 `updated_at` 触发器。
- `V4__create_runtime_settings.sql`：创建租户级运行时设置表。
- `V5__add_agent_platform_schema.sql`：增加知识域、切块配置、文档版本、插件、工具绑定、Agent Run、Step、Checkpoint、Tool Call、Eval 表。
- `V6__add_document_embedding_ann_index.sql`：创建 pgvector HNSW cosine ANN 索引。
- `V99__seed_test_users.sql`：创建本地/测试账号和默认租户。

种子账号：

- `admin / password123`，角色 OWNER
- `member / password123`，角色 MEMBER

默认租户：`默认租户`，code 为 `default`。

## 启动方式

1. 准备 `superagent` 数据库，并启用 `vector`、`pg_trgm`。
2. 按需准备 MinIO、Redis、Kafka、Neo4j。
3. 复制或参考 `backend/.env.example` 设置环境变量。
4. 执行：

```bash
./mvnw spring-boot:run
```

默认端口：`8080`。

本地闭环验证示例：

```bash
POSTGRES_URL=jdbc:postgresql://localhost:5432/superagent_test \
POSTGRES_USER=postgres \
POSTGRES_PASSWORD=postgres \
KAFKA_ENABLED=false \
INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true \
EMBEDDING_PROVIDER=local-deterministic \
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
```

## Agent Service 集成

Backend 不直接在进程内执行所有 Agent 工具，而是通过 `AGENT_SERVICE_BASE_URL` 调用独立 `agent-service/`。默认地址：

```text
http://localhost:18081
```

Agent Service 负责 Agent Run、SSE stream、resume、cancel 和工具执行编排。工具能力来自 `plugins/core-tools/manifest.json`，包括：

- `knowledge.search`
- `web.search`
- `web.fetch`
- `http.request`
- `graph.query`
- `python.sandbox`

其中 Web Search、HTTP、Graph、Python Sandbox 需要对应运行时开关、外部依赖、域名白名单或租户工具绑定。

Agent Service 内部使用 Spring AI 负责模型调用、tool-calling 协议和 structured output，但 checkpoint、resume、cancel、SSE、工具权限、审计和持久化仍由自研运行时负责。

## 认证说明

- 登录接口返回 Access Token，并通过 HttpOnly Cookie 下发 Refresh Token。
- `/api/v1/auth/refresh` 和 `/api/v1/auth/logout` 依赖该 Refresh Cookie，不再要求前端在请求体中传递 `refreshToken`。
- 前端必须开启 credentials，确保 Cookie 能随认证相关请求发送。

## 验证命令

```bash
./mvnw -q -DskipTests compile
./mvnw -q test
curl http://localhost:8080/actuator/health
```

Swagger UI：

```text
http://localhost:8080/swagger-ui.html
```

## 运行说明

- 生产级文档处理建议使用 Kafka 异步链路；本地可用 `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true` 同步处理文档。
- `local-deterministic` embedding 仅用于本地验证和 E2E。
- 外部工具默认关闭，尤其是 HTTP 请求和代码执行，应结合运行时设置、工具绑定、角色权限、域名白名单和审计记录使用。
- 运行时设置已经可持久化并写审计；模型、RAG、Rerank、Agent 和 Tools 配置分别在对应运行链路中生效。
