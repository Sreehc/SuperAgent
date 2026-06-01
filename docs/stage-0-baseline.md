# 阶段 0：环境基线与项目骨架规划

本文件记录阶段 0 的实际交付物和后续开发入口。范围严格限制在目录骨架、环境变量模板和本地依赖接入约定，不提前实现业务模块。

## 交付物

- 新建 `backend/` 基础目录和标准源码占位目录。
- 新建 `frontend/` 基础目录和前端源码占位目录。
- 新建 `infra/` 基础目录，并固定本地依赖连接方式。
- 新增 `backend/.env.example` 和 `frontend/.env.example`。
- 为后续阶段补充目录说明文件：`backend/README.md`、`frontend/README.md`、`infra/README.md`。

## 目录基线

```text
SuperAgent/
├── backend/
│   ├── .env.example
│   ├── README.md
│   └── src/
│       ├── main/java/
│       ├── main/resources/
│       └── test/java/
├── frontend/
│   ├── .env.example
│   ├── README.md
│   ├── public/
│   └── src/
├── infra/
│   ├── README.md
│   ├── docker/
│   └── scripts/
└── docs/
    └── stage-0-baseline.md
```

## 本地依赖连接约定

- PostgreSQL/pgvector：`localhost:5432`
- Redis：`localhost:6379`
- MinIO：`http://localhost:9000`
- Kafka：`localhost:9092`，阶段 0 只保留连接位，默认禁用
- 前端 API：`http://localhost:8080/api/v1`

完整说明以 [infra/README.md](/Users/cheers/Desktop/workspace/SuperAgent/infra/README.md) 为准。

## 范围边界

本阶段明确不做以下内容：

- 不初始化 Spring Boot 或 Vue CLI/Vite 业务工程。
- 不创建业务模块、实体、接口、数据库迁移或 Docker Compose。
- 不接入真实模型、认证、RAG、文档处理或前端页面。

## 下一阶段入口

- 阶段 1 从 `backend/` 开始初始化 Spring Boot 工程、配置加载和数据库迁移。
- 阶段 3 从 `frontend/` 开始初始化 Vue 3 + Vite + TypeScript 工程。
- 如果需要异步文档流水线，再在 `infra/` 中补 Kafka 启动配置。
