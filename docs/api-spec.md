# SuperAgent API 规格

本文描述当前项目已经落地的 API 分组和主要契约边界。字段级细节以 Spring Controller、DTO 和 Swagger/OpenAPI 为准。

## 基本约定

- 基础前缀：`/api/v1`
- 认证方式：
  - 登录成功返回 access token
  - refresh token 通过 HttpOnly Cookie 续期
- 响应风格：
  - 列表接口通常返回分页结果或数组
  - 详情接口返回单资源对象
  - 变更接口写入审计的场景由后端统一处理
- 多租户：
  - 用户登录后在租户上下文内访问资源
  - 管理端接口按角色区分 `OWNER / ADMIN / MEMBER`

## API 分组

### 认证与当前用户

- `/auth/*`
- `/users/me`
- `/tenants`
- `/tenant-memberships`

能力包括：

- 登录、刷新、退出登录
- 当前用户查询
- 可访问租户列表和租户切换

### 会话与聊天

- `/chat/sessions`
- `/chat/sessions/{sessionId}`
- `/chat/messages`
- `/chat/stream`

能力包括：

- 新建、重命名、归档、删除会话
- 拉取消息历史
- SSE 流式回答
- 推荐追问、引用、Agent 事件和错误事件透传

### 知识库与文档

- `/knowledge-bases`
- `/knowledge-bases/{knowledgeBaseId}`
- `/documents`
- `/documents/{documentId}`

能力包括：

- 知识库 CRUD、发布、归档
- 文档上传、批量上传、重处理
- 文档详情、chunk、版本、图谱、任务日志

### 运行时设置与治理

- `/admin/settings/model`
- `/admin/settings/rag`
- `/admin/settings/rerank`
- `/admin/settings/agent`
- `/admin/settings/tools`
- `/governance/*`

能力包括：

- 模型、RAG、Rerank、Agent、Tools 租户级设置
- 密钥脱敏和敏感配置保护
- 知识域、切块策略等治理配置

### Trace、评测、反馈、审计

- `/admin/traces`
- `/admin/retrievals`
- `/admin/reranks`
- `/admin/feedback`
- `/admin/evals/*`
- `/admin/audit-logs`

能力包括：

- Trace 列表和详情
- retrieval / rerank / model call / agent run / tool call 排障
- 反馈查询和纠错治理
- 评测套件、运行结果和报告
- 审计日志检索

### 成员与管理

- `/members`
- `/invitations`

能力包括：

- 成员列表、邀请、角色调整
- 停用、恢复、移除
- 最后 OWNER 保护

## SSE 事件约定

聊天与 Agent 流式返回基于事件流，前端当前已消费的主要事件包括：

- `start`
- `trace_stage`
- `delta`
- `reference`
- `recommendation`
- `agent_step`
- `tool_start`
- `tool_result`
- `checkpoint`
- `resume`
- `done`
- `error`

这些事件由现有 Web 控制台和 assistant-ui 外部运行时共同消费，不应随意破坏命名与时序。

## Agent Service API

独立 `agent-service` 默认运行在 `http://localhost:18081`，由 backend 调用，主要能力包括：

- 创建 Agent Run
- 恢复 Run
- 取消 Run
- 输出 Agent 流式事件
- 执行工具并落库 Run / Step / Tool Call / Checkpoint

这个服务主要面向内部编排，不是公开前端直连 API。

## 当前边界

- API 文档不再维护逐接口长篇手写说明，避免与 Controller/OpenAPI 漂移。
- 真实请求/响应结构以 Swagger 页面和代码 DTO 为准。
- Agent tool 调用协议、checkpoint 持久化和运行时细节主要由内部服务协作完成，不承诺为外部公共接口。
