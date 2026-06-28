# SuperAgent UX 规格

本文描述当前 Web 控制台的页面结构、关键交互和体验约束，面向已经存在的 React 前端实现。

## 产品形态

SuperAgent 当前不是营销站点，而是控制台型 AI 工作台：

- 高频操作以聊天、知识库、文档、Trace、设置和治理为主
- 页面应偏克制、可扫描、信息密度适中
- 前端需要同时服务终端用户、知识库管理员、平台管理员和质量/运维人员

## 主要页面

当前前端已覆盖的主要路由包括：

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
- `/feedback`
- `/evals`
- `/evals/runs/:runId`
- `/evals/:suiteId`
- `/members`
- `/audit-logs`
- `/forbidden`

## 核心交互

### 聊天工作台

- 以主消息线程为中心
- 支持知识库选择、执行模式选择、发送、停止、继续追问
- SSE 事件需要转成用户可理解的 UI
- assistant-ui 使用外部运行时模式，不替换现有后端 SSE 协议

### 会话管理

- 支持新建、切换、重命名、归档、删除
- 会话列表与当前线程需要保持清晰层级
- 危险操作应优先采用正式确认交互

### 知识库与文档

- 知识库列表需要支持创建、状态识别和快速进入详情
- 文档详情需要承载解析文本、chunk、版本、图谱、任务日志等视图
- 上传和重处理流程要明确反馈状态

### 观测与治理

- Trace 页面应支持排障和链路回看
- Tools 页面不仅展示工具清单，还要说明可用性和配置状态
- Settings、Members、Audit、Feedback、Evals 等页面要保持一致的控制台交互模式

## 体验约束

- 保持 React Router + Zustand + TanStack Query/Table + assistant-ui 现有架构
- 不破坏现有角色权限边界
- 移动端优先保证聊天可用，辅助栏可退化为 drawer
- 交互组件应保持一致的 loading / empty / error / disabled 表达
- 用户生成内容和模型输出继续走安全 Markdown 渲染

## 当前边界

- 这份文档描述的是当前体验约束和页面职责，不是新一轮完整设计稿。
- 更细的视觉打磨需求、组件补齐建议和后续体验优化范围，见 [prd.md](./prd.md)。
