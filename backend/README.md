# Backend

后端基于 `Spring Boot 3.3 + Java 21 + Flyway + PostgreSQL/pgvector`，当前已经包含认证、知识库、文档处理、会话、Trace 和运行时设置接口。

## 当前能力

- 认证与租户：`/api/v1/auth/*`、`/api/v1/tenants`
- 知识库与文档：`/api/v1/knowledge-bases/*`、`/api/v1/documents/*`
- 会话与流式回答：`/api/v1/conversations/*`
- Trace 管理：`/api/v1/admin/traces/*`
- 运行时设置：`/api/v1/admin/settings/model|rag|rerank`
- 健康检查与 Swagger：`/actuator/health`、`/swagger-ui.html`

## 启动前提

- JDK 21
- PostgreSQL/pgvector
- MinIO
- Kafka 可选；默认 `KAFKA_ENABLED=false`
- 本地闭环验证时可额外使用 `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true` 与 `EMBEDDING_PROVIDER=local-deterministic`

如需显式切换 Java 21：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## 启动方式

1. 复制 `./.env.example`
2. 准备 `superagent` 数据库，并启用 `vector`、`pg_trgm`
3. 执行：

```bash
./mvnw spring-boot:run
```

默认端口：`8080`

## 种子账号

- `admin / password123`
- `member / password123`

默认租户：`默认租户`

## 验证命令

```bash
./mvnw -q -DskipTests compile
./mvnw -q test
curl http://localhost:8080/actuator/health
```

## 已知问题

- 文档自动解析依赖 Kafka；关闭 Kafka 时，上传动作只会创建任务，不会自动消费。
- 本地若启用 `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true`，会同步处理文档；配合 `EMBEDDING_PROVIDER=local-deterministic` 可在无外部模型时完成闭环验证，但不代表生产召回质量。
- 设置页的模型配置已经可持久化并写审计，但真实模型调用热更新仍受当前客户端装配方式限制。
