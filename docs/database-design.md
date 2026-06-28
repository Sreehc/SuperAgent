# SuperAgent 数据库设计

本文描述当前数据库的结构边界和核心表族。真实 DDL 以 Flyway migration 为准。

## 总体原则

- 主库：PostgreSQL
- 迁移：Flyway
- 向量检索：`pgvector`
- 全文与模糊检索：PostgreSQL FTS + `pg_trgm`
- 审计和运行时数据优先落 PostgreSQL，避免额外引入不必要的一致性复杂度

## 主要数据域

### 认证、租户与成员

核心对象：

- 用户
- 租户
- 租户成员关系
- 邀请记录
- 刷新令牌或会话相关状态

设计约束：

- 角色区分 `OWNER / ADMIN / MEMBER`
- 保留最后 OWNER 保护
- 租户隔离是大多数业务表的前置条件

### 会话与消息

核心对象：

- chat session
- chat message
- message reference / citation
- 推荐追问、流式元数据

设计约束：

- 会话支持归档、删除、重命名
- 消息需要保留用户/助手角色和时间线
- 引用必须能映射到所选证据

### 知识库与文档

核心对象：

- knowledge base
- document
- document task / task log
- document version
- document chunk
- chunk embedding

设计约束：

- 支持文档重处理与版本一致性
- chunk 与 embedding 为检索主数据
- 文档状态覆盖上传、解析、切块、索引、失败等阶段

### RAG 与观测

核心对象：

- retrieval trace
- rerank trace
- model call trace
- query understanding / rewrite 相关观测

设计约束：

- 需要支持链路回放和问题定位
- 检索、重排、引用覆盖等关键节点应可查询

### Agent Runtime

核心对象：

- agent run
- agent step
- tool call
- checkpoint

设计约束：

- 支持 resume / cancel / checkpoint 恢复
- 工具调用结果和错误要能审计
- 数据结构需要兼容产品级运行时，而不只是单轮模型调用

### 设置、反馈、评测与审计

核心对象：

- runtime settings
- audit log
- feedback
- eval suite
- eval case
- eval run

设计约束：

- 设置按租户生效
- 敏感配置脱敏存储或受保护读取
- 反馈与评测应能回链到消息、会话或 trace

## 检索相关设计

- `document_chunk` 承载切块正文、排序信息和检索辅助字段
- embedding 向量用于语义召回
- FTS / `pg_trgm` 用于关键词和模糊召回
- RRF、邻近 chunk、版本一致性和 rerank 在应用层编排，不直接固化在单表结构里

## 非 PostgreSQL 依赖

- Redis：缓存、会话运行锁、停止信号等临时态
- MinIO：原始文件对象存储
- Kafka：文档异步任务
- Neo4j：图谱查询和 `graph.query` 工具

这些组件是整体架构的一部分，但不是主业务真相库。

## 当前边界

- 不再维护详细字段级数据库长文档，避免和 migration 漂移。
- 如需确认真实表结构，应直接查看 `backend/src/main/resources/db/migration` 与 `agent-service` 对应 migration。
