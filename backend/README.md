# Backend Baseline

阶段 0 只保留后端目录骨架，不提前生成 Spring Boot 工程或业务模块。

当前约定：

- 运行时统一使用 Java 21。
- 后端将在阶段 1 按模块化单体方式初始化。
- 本地依赖通过 `../infra/README.md` 中定义的 PostgreSQL、Redis、MinIO、Kafka 连接方式接入。
- 环境变量模板见 `./.env.example`，字段名与 [docs/06-local-development.md](/Users/cheers/Desktop/workspace/SuperAgent/docs/06-local-development.md) 保持一致。

已预留目录：

- `src/main/java`
- `src/main/resources`
- `src/test/java`

下一阶段从这里继续：

1. 初始化 Spring Boot 3 + Maven/Gradle 工程。
2. 接入配置加载和数据库迁移框架。
3. 实现健康检查、统一响应和统一异常处理。
