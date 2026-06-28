# SuperAgent 当前实现状态

最后核对日期：2026-06-28。

本文只描述当前代码已经落地的能力和边界。若本文与历史设计、旧任务计划冲突，以代码、数据库迁移和测试为准。

## 系统形态

```text
backend/         Spring Boot Backend API：认证、租户、会话、RAG、知识库、Trace、设置、评测、审计、反馈、管理 API
agent-service/   独立 Spring Boot Agent Service：Agent Run、工具注册/执行、Checkpoint、恢复、取消
sandbox-runner/  FastAPI Python Sandbox
frontend/        React + Vite Web 控制台
plugins/         插件 Manifest，例如 core-tools
infra/           部署脚本、systemd、Nginx 模板
eval/, scripts/  RAG/Agent 评测套件和脚本
```

## 技术栈

- Java 21
- Spring Boot 3.5.16
- Spring AI 1.1.8
- PostgreSQL + Flyway + pgvector + pg_trgm
- Redis、Kafka、MinIO、Neo4j
- React 19 + Vite 8 + React Router 7
- Zustand、TanStack Query、TanStack Table、assistant-ui
- Vitest、Playwright、JUnit

## 已实现能力

### 认证、租户与权限

- 登录、刷新 Token、退出登录、当前用户查询。
- Access Token + HttpOnly Refresh Cookie。
- 多租户上下文、租户列表、租户切换。
- `OWNER / ADMIN / MEMBER` 角色控制。
- 成员管理、邀请、停用/恢复、移除和最后 OWNER 保护。

### 会话与前端工作台

- React Web 控制台已覆盖登录、Chat、Knowledge、Document、Trace、Settings、Tools、Governance、Members、Audit、Feedback、Evals 等主要页面。
- 会话创建、列表、详情、重命名、归档、删除。
- SSE 流式回答、停止生成、恢复 Agent Run。
- assistant-ui 接入现有 Spring Boot SSE 协议，Zustand 仍作为会话和流状态来源。
- Markdown 安全渲染、引用来源、推荐追问、Agent timeline、Tool event、Checkpoint 和 Trace 事件展示。

### 知识库与文档

- 知识库 CRUD、发布、归档、删除。
- 文档上传、批量上传、文档元数据治理、过期和重复检测。
- PDF / DOC / DOCX / PPT / PPTX / Markdown / HTML / TXT 解析。
- MinIO 原始文件存储。
- Kafka 异步文档任务，可在本地关闭并启用 inline processing。
- 解析、切块、Embedding、索引、任务日志、重处理、版本、图谱和 graph rebuild。

### RAG

- Query Understanding、问题改写、子问题拆分。
- pgvector 向量检索 + PostgreSQL 全文/关键词 + `pg_trgm` 模糊匹配。
- RRF 融合、邻近 Chunk 扩展、文档版本一致性过滤。
- 可选 Rerank 与失败降级。
- 相关性阈值、证据数量和字符预算控制。
- 强制引用、引用映射、无证据兜底。
- 知识库、知识域、切块策略、分类、标签过滤。
- RAG runtime metrics、Trace 和评测脚本。

### Spring AI 接入

- Backend Chat 调用已通过 Spring AI `ChatModel`。
- Backend Embedding 调用已通过 Spring AI `EmbeddingModel`。
- Query Understanding 已使用 Spring AI `BeanOutputConverter` structured output，保留兼容兜底。
- Agent Service 已使用 Spring AI `ChatModel`。
- Agent planner 支持 Spring AI `ToolCallback` tool-calling 协议，模型可原生选择工具；选择结果转换为现有 `AgentDecision`。
- Spring AI 模型构造接入 `ObservationRegistry`，可被 Micrometer/Actuator 观测链路承接。

### Agent 与工具

- Backend 可按自动路由或用户显式 `executionMode` 进入 `REACT_AGENT`。
- 独立 Agent Service 支持 run 创建、SSE stream、恢复、取消。
- Run / Step / Tool Call / Checkpoint 持久化。
- 插件 Manifest 加载、租户启停、工具注册表。
- 工具执行仍走自研 `ToolExecutionService`，保留权限、风险控制、allowlist、secret binding、审计和持久化边界。
- 核心工具包括 `knowledge.search`、`web.search`、`web.fetch`、`http.request`、`graph.query`、`python.sandbox`。

### 设置、Trace 与治理

- Model / RAG / Rerank / Agent / Tools 租户级运行时设置。
- 密钥脱敏、敏感配置 OWNER 权限、变更写审计。
- Trace 阶段、模型调用、检索、Rerank、Agent Run、Tool Call、Checkpoint 查询。
- Tools、Governance、Audit、Feedback、Evaluation 管理页面。

## 当前边界

- Spring AI 接管模型调用、tool-calling 协议和 structured output；不接管产品级 Agent Run 生命周期。
- `AgentRunExecutionService` 仍负责 checkpoint、resume、cancel、pause/SSE、持久化、运行策略和错误收敛。
- 工具执行、风险控制、allowlist、secret binding、plugin manifest、tool registry、audit log 继续保留自研实现。
- Rerank 仍保留 OpenAI-compatible 自研客户端；Spring AI 当前没有足够稳定通用的 rerank 抽象。
- 数据库结构真实来源是 Flyway migration，不再维护单独长篇数据库设计文档。
- API 真实来源是 Spring Controller/OpenAPI，不再维护单独长篇 API 设计文档。

## 验证命令

```bash
cd backend
./mvnw -DskipTests compile
./mvnw test

cd ../agent-service
mvn -DskipTests compile
mvn test

cd ../frontend
npm run typecheck
npm test
npm run build
```
